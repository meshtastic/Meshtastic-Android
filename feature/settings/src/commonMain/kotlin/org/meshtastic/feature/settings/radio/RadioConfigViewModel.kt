/*
 * Copyright (c) 2026 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.meshtastic.feature.settings.radio

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.koin.core.annotation.InjectedParam
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.common.util.CommonUri
import org.meshtastic.core.common.util.safeCatching
import org.meshtastic.core.domain.usecase.settings.AdminActionsUseCase
import org.meshtastic.core.domain.usecase.settings.ExportProfileUseCase
import org.meshtastic.core.domain.usecase.settings.ExportSecurityConfigUseCase
import org.meshtastic.core.domain.usecase.settings.ImportProfileUseCase
import org.meshtastic.core.domain.usecase.settings.InstallProfileUseCase
import org.meshtastic.core.domain.usecase.settings.RadioConfigUseCase
import org.meshtastic.core.model.AdminException
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.MqttConnectionState
import org.meshtastic.core.model.MqttProbeStatus
import org.meshtastic.core.model.MyNodeInfo
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.Position
import org.meshtastic.core.repository.AnalyticsPrefs
import org.meshtastic.core.repository.FileService
import org.meshtastic.core.repository.HomoglyphPrefs
import org.meshtastic.core.repository.LocationRepository
import org.meshtastic.core.repository.LocationService
import org.meshtastic.core.repository.MapConsentPrefs
import org.meshtastic.core.repository.MqttManager
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.UiText
import org.meshtastic.core.resources.cant_shutdown
import org.meshtastic.core.resources.timeout
import org.meshtastic.core.ui.util.getChannelList
import org.meshtastic.core.ui.viewmodel.safeLaunch
import org.meshtastic.feature.settings.navigation.ConfigRoute
import org.meshtastic.feature.settings.navigation.ModuleRoute
import org.meshtastic.proto.AdminMessage
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config
import org.meshtastic.proto.DeviceConnectionStatus
import org.meshtastic.proto.DeviceMetadata
import org.meshtastic.proto.DeviceProfile
import org.meshtastic.proto.DeviceUIConfig
import org.meshtastic.proto.FileInfo
import org.meshtastic.proto.HardwareModel
import org.meshtastic.proto.LocalConfig
import org.meshtastic.proto.LocalModuleConfig
import org.meshtastic.proto.ModuleConfig
import org.meshtastic.proto.User

/** Data class that represents the current RadioConfig state. */
data class RadioConfigState(
    val isLocal: Boolean = false,
    val connected: Boolean = false,
    val route: String = "",
    val metadata: DeviceMetadata? = null,
    val userConfig: User = User(),
    val channelList: List<ChannelSettings> = emptyList(),
    val radioConfig: LocalConfig = LocalConfig(),
    val moduleConfig: LocalModuleConfig = LocalModuleConfig(),
    val ringtone: String = "",
    val cannedMessageMessages: String = "",
    val deviceConnectionStatus: DeviceConnectionStatus? = null,
    val deviceUIConfig: DeviceUIConfig? = null,
    val fileManifest: List<FileInfo> = emptyList(),
    val responseState: ResponseState<Boolean> = ResponseState.Empty,
    val analyticsAvailable: Boolean = true,
    val analyticsEnabled: Boolean = true,
    val nodeDbResetPreserveFavorites: Boolean = false,
)

@KoinViewModel
@Suppress("LongParameterList")
open class RadioConfigViewModel(
    @InjectedParam savedStateHandle: SavedStateHandle,
    private val radioConfigRepository: RadioConfigRepository,
    private val packetRepository: PacketRepository,
    private val serviceRepository: ServiceRepository,
    private val nodeRepository: NodeRepository,
    private val locationRepository: LocationRepository,
    private val mapConsentPrefs: MapConsentPrefs,
    private val analyticsPrefs: AnalyticsPrefs,
    private val homoglyphEncodingPrefs: HomoglyphPrefs,
    protected val importProfileUseCase: ImportProfileUseCase,
    protected val exportProfileUseCase: ExportProfileUseCase,
    protected val exportSecurityConfigUseCase: ExportSecurityConfigUseCase,
    private val installProfileUseCase: InstallProfileUseCase,
    private val radioConfigUseCase: RadioConfigUseCase,
    private val adminActionsUseCase: AdminActionsUseCase,
    private val locationService: LocationService,
    private val fileService: FileService,
    private val mqttManager: MqttManager,
) : ViewModel() {
    val analyticsAllowedFlow = analyticsPrefs.analyticsAllowed

    fun toggleAnalyticsAllowed() {
        analyticsPrefs.setAnalyticsAllowed(!analyticsPrefs.analyticsAllowed.value)
    }

    val homoglyphEncodingEnabledFlow = homoglyphEncodingPrefs.homoglyphEncodingEnabled

    fun toggleHomoglyphCharactersEncodingEnabled() {
        homoglyphEncodingPrefs.setHomoglyphEncodingEnabled(!homoglyphEncodingPrefs.homoglyphEncodingEnabled.value)
    }

    /** MQTT proxy connection state for the settings UI. */
    val mqttConnectionState: StateFlow<MqttConnectionState> = mqttManager.mqttConnectionState

    private val _mqttProbeStatus = MutableStateFlow<MqttProbeStatus?>(null)

    /** Latest result from a [probeMqttConnection] call, or `null` if no probe has been run. */
    val mqttProbeStatus: StateFlow<MqttProbeStatus?> = _mqttProbeStatus.asStateFlow()

    private var probeJob: Job? = null

    /**
     * Run a one-shot reachability/credentials probe against an MQTT broker. Cancels any in-flight probe before starting
     * a new one. Result is exposed via [mqttProbeStatus].
     */
    fun probeMqttConnection(address: String, tlsEnabled: Boolean, username: String?, password: String?) {
        probeJob?.cancel()
        _mqttProbeStatus.value = MqttProbeStatus.Probing
        probeJob =
            viewModelScope.launch {
                val result =
                    safeCatching { mqttManager.probe(address, tlsEnabled, username, password) }
                        .getOrElse { e ->
                            Logger.w(e) { "MQTT probe threw" }
                            MqttProbeStatus.Other(message = e.message)
                        }
                _mqttProbeStatus.value = result
            }
    }

    /** Clear the latest probe result (e.g. when the user edits the address). */
    fun clearMqttProbeStatus() {
        probeJob?.cancel()
        _mqttProbeStatus.value = null
    }

    private val destNumFlow = MutableStateFlow(savedStateHandle.get<Int>("destNum"))

    fun initDestNum(id: Int?) {
        if (destNumFlow.value != id) {
            destNumFlow.value = id
        }
    }

    private val _destNode = MutableStateFlow<Node?>(null)
    val destNode: StateFlow<Node?>
        get() = _destNode

    private val _radioConfigState = MutableStateFlow(RadioConfigState())
    val radioConfigState: StateFlow<RadioConfigState> = _radioConfigState

    fun setPreserveFavorites(preserveFavorites: Boolean) {
        _radioConfigState.update { it.copy(nodeDbResetPreserveFavorites = preserveFavorites) }
    }

    private val _currentDeviceProfile = MutableStateFlow(DeviceProfile())
    val currentDeviceProfile
        get() = _currentDeviceProfile.value

    open suspend fun getCurrentLocation(): org.meshtastic.core.repository.Location? =
        locationService.getCurrentLocation()

    init {
        combine(destNumFlow, nodeRepository.nodeDBbyNum) { id, nodes -> nodes[id] ?: nodes.values.firstOrNull() }
            .distinctUntilChanged()
            .onEach {
                _destNode.value = it
                _radioConfigState.update { state -> state.copy(metadata = it?.metadata) }
            }
            .launchIn(viewModelScope)

        radioConfigRepository.deviceProfileFlow.onEach { _currentDeviceProfile.value = it }.launchIn(viewModelScope)

        radioConfigRepository.localConfigFlow
            .onEach { lc -> if (radioConfigState.value.isLocal) _radioConfigState.update { it.copy(radioConfig = lc) } }
            .launchIn(viewModelScope)

        radioConfigRepository.channelSetFlow
            .onEach { cs ->
                if (radioConfigState.value.isLocal) _radioConfigState.update { it.copy(channelList = cs.settings) }
            }
            .launchIn(viewModelScope)

        radioConfigRepository.moduleConfigFlow
            .onEach { lmc ->
                if (radioConfigState.value.isLocal) _radioConfigState.update { it.copy(moduleConfig = lmc) }
            }
            .launchIn(viewModelScope)

        radioConfigRepository.deviceUIConfigFlow
            .onEach { uiConfig -> _radioConfigState.update { it.copy(deviceUIConfig = uiConfig) } }
            .launchIn(viewModelScope)

        radioConfigRepository.fileManifestFlow
            .onEach { manifest -> _radioConfigState.update { it.copy(fileManifest = manifest) } }
            .launchIn(viewModelScope)

        combine(serviceRepository.connectionState, radioConfigState) { connState, _ ->
            _radioConfigState.update { it.copy(connected = connState == ConnectionState.Connected) }
        }
            .launchIn(viewModelScope)

        combine(nodeRepository.myNodeInfo, destNumFlow) { ni, id ->
            _radioConfigState.update { it.copy(isLocal = (id == null) || (id == ni?.myNodeNum)) }
        }
            .launchIn(viewModelScope)

        Logger.d { "RadioConfigViewModel created" }
    }

    private val myNodeInfo: StateFlow<MyNodeInfo?>
        get() = nodeRepository.myNodeInfo

    val myNodeNum
        get() = myNodeInfo.value?.myNodeNum

    val maxChannels
        get() = myNodeInfo.value?.maxChannels ?: 8

    val hasPaFan: Boolean
        get() =
            destNode.value?.user?.hw_model in
                setOf(
                    null,
                    HardwareModel.UNSET,
                    HardwareModel.BETAFPV_2400_TX,
                    HardwareModel.RADIOMASTER_900_BANDIT_NANO,
                    HardwareModel.RADIOMASTER_900_BANDIT,
                )

    override fun onCleared() {
        super.onCleared()
        Logger.d { "RadioConfigViewModel cleared" }
    }

    fun setOwner(user: User) {
        val destNum = destNode.value?.num ?: return
        safeLaunch(tag = "setOwner") {
            _radioConfigState.update { it.copy(userConfig = user) }
            radioConfigUseCase.setOwner(destNum, user)
        }
    }

    fun updateChannels(new: List<ChannelSettings>, old: List<ChannelSettings>) {
        val destNum = destNode.value?.num ?: return
        getChannelList(new, old).forEach { channel ->
            safeLaunch(tag = "setRemoteChannel") {
                radioConfigUseCase.setRemoteChannel(destNum, channel)
            }
        }

        if (destNum == myNodeNum) {
            safeLaunch(tag = "migrateChannels") {
                packetRepository.migrateChannelsByPSK(old, new)
                radioConfigRepository.replaceAllSettings(new)
            }
        }
        _radioConfigState.update { it.copy(channelList = new) }
    }

    fun setConfig(config: Config) {
        val destNum = destNode.value?.num ?: return
        safeLaunch(tag = "setConfig") {
            _radioConfigState.update { state ->
                state.copy(
                    radioConfig =
                    state.radioConfig.copy(
                        device = config.device ?: state.radioConfig.device,
                        position = config.position ?: state.radioConfig.position,
                        power = config.power ?: state.radioConfig.power,
                        network = config.network ?: state.radioConfig.network,
                        display = config.display ?: state.radioConfig.display,
                        lora = config.lora ?: state.radioConfig.lora,
                        bluetooth = config.bluetooth ?: state.radioConfig.bluetooth,
                        security = config.security ?: state.radioConfig.security,
                    ),
                )
            }
            radioConfigUseCase.setConfig(destNum, config)
        }
    }

    @Suppress("CyclomaticComplexMethod")
    fun setModuleConfig(config: ModuleConfig) {
        val destNum = destNode.value?.num ?: return
        safeLaunch(tag = "setModuleConfig") {
            _radioConfigState.update { state ->
                state.copy(
                    moduleConfig =
                    state.moduleConfig.copy(
                        mqtt = config.mqtt ?: state.moduleConfig.mqtt,
                        serial = config.serial ?: state.moduleConfig.serial,
                        external_notification =
                        config.external_notification ?: state.moduleConfig.external_notification,
                        store_forward = config.store_forward ?: state.moduleConfig.store_forward,
                        range_test = config.range_test ?: state.moduleConfig.range_test,
                        telemetry = config.telemetry ?: state.moduleConfig.telemetry,
                        canned_message = config.canned_message ?: state.moduleConfig.canned_message,
                        audio = config.audio ?: state.moduleConfig.audio,
                        remote_hardware = config.remote_hardware ?: state.moduleConfig.remote_hardware,
                        neighbor_info = config.neighbor_info ?: state.moduleConfig.neighbor_info,
                        ambient_lighting = config.ambient_lighting ?: state.moduleConfig.ambient_lighting,
                        detection_sensor = config.detection_sensor ?: state.moduleConfig.detection_sensor,
                        paxcounter = config.paxcounter ?: state.moduleConfig.paxcounter,
                        statusmessage = config.statusmessage ?: state.moduleConfig.statusmessage,
                        traffic_management = config.traffic_management ?: state.moduleConfig.traffic_management,
                        tak = config.tak ?: state.moduleConfig.tak,
                    ),
                )
            }
            radioConfigUseCase.setModuleConfig(destNum, config)
        }
    }

    fun setRingtone(ringtone: String) {
        val destNum = destNode.value?.num ?: return
        _radioConfigState.update { it.copy(ringtone = ringtone) }
        safeLaunch(tag = "setRingtone") { radioConfigUseCase.setRingtone(destNum, ringtone) }
    }

    fun setCannedMessages(messages: String) {
        val destNum = destNode.value?.num ?: return
        _radioConfigState.update { it.copy(cannedMessageMessages = messages) }
        safeLaunch(tag = "setCannedMessages") { radioConfigUseCase.setCannedMessages(destNum, messages) }
    }

    fun setFixedPosition(position: Position) {
        val destNum = destNode.value?.num ?: return
        safeLaunch(tag = "setFixedPosition") { radioConfigUseCase.setFixedPosition(destNum, position) }
    }

    fun removeFixedPosition() {
        val destNum = destNode.value?.num ?: return
        safeLaunch(tag = "removeFixedPosition") { radioConfigUseCase.removeFixedPosition(destNum) }
    }

    fun importProfile(uri: CommonUri, onResult: (DeviceProfile) -> Unit) {
        safeLaunch(tag = "importProfile") {
            var profile: DeviceProfile? = null
            fileService.read(uri) { source ->
                importProfileUseCase(source).onSuccess { profile = it }.onFailure { throw it }
            }
            profile?.let { onResult(it) }
        }
    }

    fun exportProfile(uri: CommonUri, profile: DeviceProfile) {
        safeLaunch(tag = "exportProfile") {
            fileService.write(uri) { sink ->
                exportProfileUseCase(sink, profile).onSuccess { /* Success */ }.onFailure { throw it }
            }
        }
    }

    fun exportSecurityConfig(uri: CommonUri, securityConfig: Config.SecurityConfig) {
        safeLaunch(tag = "exportSecurityConfig") {
            fileService.write(uri) { sink ->
                exportSecurityConfigUseCase(sink, securityConfig).onSuccess { /* Success */ }.onFailure { throw it }
            }
        }
    }

    fun installProfile(protobuf: DeviceProfile) {
        val destNum = destNode.value?.num ?: return
        safeLaunch(tag = "installProfile") { installProfileUseCase(destNum, protobuf, destNode.value?.user) }
    }

    fun clearPacketResponse() {
        _radioConfigState.update { it.copy(responseState = ResponseState.Empty) }
    }

    fun setResponseStateLoading(route: Enum<*>) {
        val destNum = destNumFlow.value ?: destNode.value?.num ?: return

        _radioConfigState.update { it.copy(route = route.name, responseState = ResponseState.Loading()) }

        viewModelScope.launch {
            try {
                when (route) {
                    ConfigRoute.USER -> {
                        val user = radioConfigUseCase.getOwner(destNum)
                        _radioConfigState.update { it.copy(userConfig = user) }
                    }

                    ConfigRoute.CHANNELS -> {
                        val channels = radioConfigUseCase.listChannels(destNum)
                        val loraConfig = radioConfigUseCase.getConfig(destNum, AdminMessage.ConfigType.LORA_CONFIG.value)
                        _radioConfigState.update { state ->
                            state.copy(
                                channelList = channels.mapNotNull { it.settings },
                                radioConfig = state.radioConfig.copy(lora = loraConfig.lora ?: state.radioConfig.lora),
                            )
                        }
                    }

                    is AdminRoute -> {
                        executeAdminAction(destNum, route)
                        return@launch
                    }

                    is ConfigRoute -> {
                        val config = radioConfigUseCase.getConfig(destNum, route.type)
                        _radioConfigState.update { state ->
                            state.copy(
                                radioConfig = state.radioConfig.copy(
                                    device = config.device ?: state.radioConfig.device,
                                    position = config.position ?: state.radioConfig.position,
                                    power = config.power ?: state.radioConfig.power,
                                    network = config.network ?: state.radioConfig.network,
                                    display = config.display ?: state.radioConfig.display,
                                    lora = config.lora ?: state.radioConfig.lora,
                                    bluetooth = config.bluetooth ?: state.radioConfig.bluetooth,
                                    security = config.security ?: state.radioConfig.security,
                                ),
                            )
                        }
                        if (route == ConfigRoute.LORA) {
                            val channels = radioConfigUseCase.listChannels(destNum)
                            _radioConfigState.update { it.copy(channelList = channels.mapNotNull { ch -> ch.settings }) }
                        }
                        if (route == ConfigRoute.NETWORK) {
                            val status = radioConfigUseCase.getDeviceConnectionStatus(destNum)
                            _radioConfigState.update { it.copy(deviceConnectionStatus = status) }
                        }
                    }

                    is ModuleRoute -> {
                        val moduleConfig = radioConfigUseCase.getModuleConfig(destNum, route.type)
                        _radioConfigState.update { state ->
                            state.copy(
                                moduleConfig = state.moduleConfig.copy(
                                    mqtt = moduleConfig.mqtt ?: state.moduleConfig.mqtt,
                                    serial = moduleConfig.serial ?: state.moduleConfig.serial,
                                    external_notification =
                                    moduleConfig.external_notification ?: state.moduleConfig.external_notification,
                                    store_forward = moduleConfig.store_forward ?: state.moduleConfig.store_forward,
                                    range_test = moduleConfig.range_test ?: state.moduleConfig.range_test,
                                    telemetry = moduleConfig.telemetry ?: state.moduleConfig.telemetry,
                                    canned_message = moduleConfig.canned_message ?: state.moduleConfig.canned_message,
                                    audio = moduleConfig.audio ?: state.moduleConfig.audio,
                                    remote_hardware =
                                    moduleConfig.remote_hardware ?: state.moduleConfig.remote_hardware,
                                    neighbor_info = moduleConfig.neighbor_info ?: state.moduleConfig.neighbor_info,
                                    ambient_lighting =
                                    moduleConfig.ambient_lighting ?: state.moduleConfig.ambient_lighting,
                                    detection_sensor =
                                    moduleConfig.detection_sensor ?: state.moduleConfig.detection_sensor,
                                    paxcounter = moduleConfig.paxcounter ?: state.moduleConfig.paxcounter,
                                    statusmessage = moduleConfig.statusmessage ?: state.moduleConfig.statusmessage,
                                    traffic_management =
                                    moduleConfig.traffic_management ?: state.moduleConfig.traffic_management,
                                    tak = moduleConfig.tak ?: state.moduleConfig.tak,
                                ),
                            )
                        }
                        if (route == ModuleRoute.CANNED_MESSAGE) {
                            val messages = radioConfigUseCase.getCannedMessages(destNum)
                            _radioConfigState.update { it.copy(cannedMessageMessages = messages) }
                        }
                        if (route == ModuleRoute.EXT_NOTIFICATION) {
                            val ringtone = radioConfigUseCase.getRingtone(destNum)
                            _radioConfigState.update { it.copy(ringtone = ringtone) }
                        }
                    }
                }
                setResponseStateSuccess()
            } catch (e: AdminException) {
                sendError(e.toUiText())
            }
        }
    }

    private suspend fun executeAdminAction(destNum: Int, route: AdminRoute) {
        try {
            val preserveFavorites = radioConfigState.value.nodeDbResetPreserveFavorites
            when (route) {
                AdminRoute.REBOOT -> adminActionsUseCase.reboot(destNum)
                AdminRoute.SHUTDOWN -> {
                    if (radioConfigState.value.metadata?.canShutdown != true) {
                        sendError(Res.string.cant_shutdown)
                        return
                    }
                    adminActionsUseCase.shutdown(destNum)
                }
                AdminRoute.FACTORY_RESET -> {
                    val isLocal = (destNum == myNodeNum)
                    adminActionsUseCase.factoryReset(destNum, isLocal)
                }
                AdminRoute.NODEDB_RESET -> {
                    val isLocal = (destNum == myNodeNum)
                    adminActionsUseCase.nodedbReset(destNum, preserveFavorites, isLocal)
                }
            }
            setResponseStateSuccess()
        } catch (e: AdminException) {
            sendError(e.toUiText())
        }
    }

    private fun AdminException.toUiText(): UiText = when (this) {
        is AdminException.Timeout -> UiText.Resource(Res.string.timeout)
        else -> UiText.DynamicString(message ?: "Admin request failed")
    }

    fun shouldReportLocation(nodeNum: Int?) = mapConsentPrefs.shouldReportLocation(nodeNum)

    fun setShouldReportLocation(nodeNum: Int?, shouldReportLocation: Boolean) {
        mapConsentPrefs.setShouldReportLocation(nodeNum, shouldReportLocation)
    }

    protected fun setResponseStateSuccess() {
        _radioConfigState.update { state ->
            if (state.responseState is ResponseState.Loading) {
                state.copy(responseState = ResponseState.Success(true))
            } else {
                state
            }
        }
    }

    protected fun sendError(error: String) = setResponseStateError(UiText.DynamicString(error))

    protected fun sendError(id: StringResource) = setResponseStateError(UiText.Resource(id))

    protected fun sendError(error: UiText) = setResponseStateError(error)

    private fun setResponseStateError(error: UiText) {
        _radioConfigState.update { it.copy(responseState = ResponseState.Error(error)) }
    }
}
