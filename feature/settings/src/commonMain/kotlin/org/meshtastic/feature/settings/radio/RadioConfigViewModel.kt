/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
import kotlinx.coroutines.delay
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
import org.meshtastic.core.domain.usecase.settings.AdminActionsUseCase
import org.meshtastic.core.domain.usecase.settings.ExportProfileUseCase
import org.meshtastic.core.domain.usecase.settings.ExportSecurityConfigUseCase
import org.meshtastic.core.domain.usecase.settings.ImportProfileUseCase
import org.meshtastic.core.domain.usecase.settings.InstallProfileUseCase
import org.meshtastic.core.domain.usecase.settings.ProcessRadioResponseUseCase
import org.meshtastic.core.domain.usecase.settings.RadioConfigUseCase
import org.meshtastic.core.domain.usecase.settings.RadioResponseResult
import org.meshtastic.core.domain.usecase.settings.ToggleAnalyticsUseCase
import org.meshtastic.core.domain.usecase.settings.ToggleHomoglyphEncodingUseCase
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
import org.meshtastic.proto.Channel
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
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.ModuleConfig
import org.meshtastic.proto.User
import kotlin.time.Duration.Companion.seconds

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
    private val toggleAnalyticsUseCase: ToggleAnalyticsUseCase,
    private val toggleHomoglyphEncodingUseCase: ToggleHomoglyphEncodingUseCase,
    protected val importProfileUseCase: ImportProfileUseCase,
    protected val exportProfileUseCase: ExportProfileUseCase,
    protected val exportSecurityConfigUseCase: ExportSecurityConfigUseCase,
    private val installProfileUseCase: InstallProfileUseCase,
    private val radioConfigUseCase: RadioConfigUseCase,
    private val adminActionsUseCase: AdminActionsUseCase,
    private val processRadioResponseUseCase: ProcessRadioResponseUseCase,
    private val locationService: LocationService,
    private val fileService: FileService,
    private val mqttManager: MqttManager,
) : ViewModel() {
    val analyticsAllowedFlow = analyticsPrefs.analyticsAllowed

    fun toggleAnalyticsAllowed() {
        toggleAnalyticsUseCase()
    }

    val homoglyphEncodingEnabledFlow = homoglyphEncodingPrefs.homoglyphEncodingEnabled

    fun toggleHomoglyphCharactersEncodingEnabled() {
        toggleHomoglyphEncodingUseCase()
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
                    runCatching { mqttManager.probe(address, tlsEnabled, username, password) }
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

    private val requestIds = MutableStateFlow(hashSetOf<Int>())
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

        serviceRepository.meshPacketFlow.onEach(::processPacketResponse).launchIn(viewModelScope)

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
            val packetId = radioConfigUseCase.setOwner(destNum, user)
            registerRequestId(packetId)
        }
    }

    fun updateChannels(new: List<ChannelSettings>, old: List<ChannelSettings>) {
        val destNum = destNode.value?.num ?: return
        getChannelList(new, old).forEach { channel ->
            safeLaunch(tag = "setRemoteChannel") {
                val packetId = radioConfigUseCase.setRemoteChannel(destNum, channel)
                registerRequestId(packetId)
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
            val packetId = radioConfigUseCase.setConfig(destNum, config)
            registerRequestId(packetId)
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
            val packetId = radioConfigUseCase.setModuleConfig(destNum, config)
            registerRequestId(packetId)
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

    private fun sendAdminRequest(destNum: Int) {
        val route = radioConfigState.value.route
        _radioConfigState.update { it.copy(route = "") } // setter (response is PortNum.ROUTING_APP)

        val preserveFavorites = radioConfigState.value.nodeDbResetPreserveFavorites

        when (route) {
            AdminRoute.REBOOT.name ->
                safeLaunch(tag = "reboot") {
                    val packetId = adminActionsUseCase.reboot(destNum)
                    registerRequestId(packetId)
                }
            AdminRoute.SHUTDOWN.name ->
                with(radioConfigState.value) {
                    if (metadata?.canShutdown != true) {
                        sendError(Res.string.cant_shutdown)
                    } else {
                        safeLaunch(tag = "shutdown") {
                            val packetId = adminActionsUseCase.shutdown(destNum)
                            registerRequestId(packetId)
                        }
                    }
                }

            AdminRoute.FACTORY_RESET.name ->
                safeLaunch(tag = "factoryReset") {
                    val isLocal = (destNum == myNodeNum)
                    val packetId = adminActionsUseCase.factoryReset(destNum, isLocal)
                    registerRequestId(packetId)
                }
            AdminRoute.NODEDB_RESET.name ->
                safeLaunch(tag = "nodedbReset") {
                    val isLocal = (destNum == myNodeNum)
                    val packetId = adminActionsUseCase.nodedbReset(destNum, preserveFavorites, isLocal)
                    registerRequestId(packetId)
                }
        }
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
        requestIds.value = hashSetOf()
        _radioConfigState.update { it.copy(responseState = ResponseState.Empty) }
    }

    fun setResponseStateLoading(route: Enum<*>) {
        val destNum = destNumFlow.value ?: destNode.value?.num ?: return

        _radioConfigState.update { it.copy(route = route.name, responseState = ResponseState.Loading()) }

        when (route) {
            ConfigRoute.USER ->
                safeLaunch(tag = "getOwner") {
                    val packetId = radioConfigUseCase.getOwner(destNum)
                    registerRequestId(packetId)
                }

            ConfigRoute.CHANNELS -> {
                safeLaunch(tag = "getChannel0") {
                    val packetId = radioConfigUseCase.getChannel(destNum, 0)
                    registerRequestId(packetId)
                }
                safeLaunch(tag = "getLoraConfig") {
                    val packetId = radioConfigUseCase.getConfig(destNum, AdminMessage.ConfigType.LORA_CONFIG.value)
                    registerRequestId(packetId)
                }
                // channel editor is synchronous, so we don't use requestIds as total
                setResponseStateTotal(maxChannels + 1)
            }

            is AdminRoute -> {
                safeLaunch(tag = "getSessionKeyConfig") {
                    val packetId =
                        radioConfigUseCase.getConfig(destNum, AdminMessage.ConfigType.SESSIONKEY_CONFIG.value)
                    registerRequestId(packetId)
                }
                setResponseStateTotal(2)
            }

            is ConfigRoute -> {
                if (route == ConfigRoute.LORA) {
                    safeLaunch(tag = "getChannel0ForLora") {
                        val packetId = radioConfigUseCase.getChannel(destNum, 0)
                        registerRequestId(packetId)
                    }
                }
                if (route == ConfigRoute.NETWORK) {
                    safeLaunch(tag = "getConnectionStatus") {
                        val packetId = radioConfigUseCase.getDeviceConnectionStatus(destNum)
                        registerRequestId(packetId)
                    }
                }
                safeLaunch(tag = "getConfig") {
                    val packetId = radioConfigUseCase.getConfig(destNum, route.type)
                    registerRequestId(packetId)
                }
            }

            is ModuleRoute -> {
                if (route == ModuleRoute.CANNED_MESSAGE) {
                    safeLaunch(tag = "getCannedMessages") {
                        val packetId = radioConfigUseCase.getCannedMessages(destNum)
                        registerRequestId(packetId)
                    }
                }
                if (route == ModuleRoute.EXT_NOTIFICATION) {
                    safeLaunch(tag = "getRingtone") {
                        val packetId = radioConfigUseCase.getRingtone(destNum)
                        registerRequestId(packetId)
                    }
                }
                safeLaunch(tag = "getModuleConfig") {
                    val packetId = radioConfigUseCase.getModuleConfig(destNum, route.type)
                    registerRequestId(packetId)
                }
            }
        }
    }

    fun shouldReportLocation(nodeNum: Int?) = mapConsentPrefs.shouldReportLocation(nodeNum)

    fun setShouldReportLocation(nodeNum: Int?, shouldReportLocation: Boolean) {
        mapConsentPrefs.setShouldReportLocation(nodeNum, shouldReportLocation)
    }

    private fun setResponseStateTotal(total: Int) {
        _radioConfigState.update { state ->
            if (state.responseState is ResponseState.Loading) {
                state.copy(responseState = state.responseState.copy(total = total))
            } else {
                state // Return the unchanged state for other response states
            }
        }
    }

    protected fun setResponseStateSuccess() {
        _radioConfigState.update { state ->
            if (state.responseState is ResponseState.Loading) {
                state.copy(responseState = ResponseState.Success(true))
            } else {
                state // Return the unchanged state for other response states
            }
        }
    }

    protected fun sendError(error: String) = setResponseStateError(UiText.DynamicString(error))

    protected fun sendError(id: StringResource) = setResponseStateError(UiText.Resource(id))

    protected fun sendError(error: UiText) = setResponseStateError(error)

    private fun setResponseStateError(error: UiText) {
        _radioConfigState.update { it.copy(responseState = ResponseState.Error(error)) }
    }

    private fun incrementCompleted() {
        _radioConfigState.update { state ->
            if (state.responseState is ResponseState.Loading) {
                val increment = state.responseState.completed + 1
                state.copy(responseState = state.responseState.copy(completed = increment))
            } else {
                state // Return the unchanged state for other response states
            }
        }
    }

    private fun registerRequestId(packetId: Int) {
        requestIds.update { it.apply { add(packetId) } }
        _radioConfigState.update { state ->
            if (state.responseState is ResponseState.Loading) {
                val total = maxOf(requestIds.value.size, state.responseState.total)
                state.copy(responseState = state.responseState.copy(total = total))
            } else {
                state.copy(
                    route = "", // setter (response is PortNum.ROUTING_APP)
                    responseState = ResponseState.Loading(),
                )
            }
        }

        val requestTimeout = 30.seconds
        safeLaunch(tag = "requestTimeout") {
            delay(requestTimeout)
            if (requestIds.value.contains(packetId)) {
                requestIds.update { it.apply { remove(packetId) } }
                if (requestIds.value.isEmpty()) {
                    sendError(Res.string.timeout)
                }
            }
        }
    }

    private fun processPacketResponse(packet: MeshPacket) {
        val destNum = destNode.value?.num ?: return
        val result = processRadioResponseUseCase(packet, destNum, requestIds.value) ?: return
        val route = radioConfigState.value.route

        when (result) {
            is RadioResponseResult.Error -> {
                sendError(result.message)
                // Abort the AdminRoute flow — do not fire the destructive action
                // (reboot/shutdown/factory_reset) if the metadata preflight failed.
                return
            }
            is RadioResponseResult.Success -> {
                if (route.isEmpty()) {
                    val data = packet.decoded!!
                    requestIds.update { it.apply { remove(data.request_id) } }
                    if (requestIds.value.isEmpty()) {
                        setResponseStateSuccess()
                    } else {
                        incrementCompleted()
                    }
                }
            }

            is RadioResponseResult.Metadata -> {
                _radioConfigState.update { it.copy(metadata = result.metadata) }
                incrementCompleted()
            }

            is RadioResponseResult.ChannelResponse -> {
                val response = result.channel
                // Stop once we get to the first disabled entry
                if (response.role != Channel.Role.DISABLED) {
                    _radioConfigState.update { state ->
                        state.copy(
                            channelList =
                            state.channelList.toMutableList().apply {
                                val index = response.index
                                val settings = response.settings ?: ChannelSettings()
                                // Make sure list is large enough
                                while (size <= index) add(ChannelSettings())
                                set(index, settings)
                            },
                        )
                    }
                    incrementCompleted()
                    val index = response.index
                    if (index + 1 < maxChannels && route == ConfigRoute.CHANNELS.name) {
                        // Not done yet, request next channel
                        safeLaunch(tag = "getNextChannel") {
                            val packetId = radioConfigUseCase.getChannel(destNum, index + 1)
                            registerRequestId(packetId)
                        }
                    }
                } else {
                    // Received last channel, update total and start channel editor
                    setResponseStateTotal(response.index + 1)
                }
            }

            is RadioResponseResult.Owner -> {
                _radioConfigState.update { it.copy(userConfig = result.user) }
                incrementCompleted()
            }

            is RadioResponseResult.ConfigResponse -> {
                val response = result.config
                _radioConfigState.update { state ->
                    state.copy(
                        radioConfig =
                        state.radioConfig.copy(
                            device = response.device ?: state.radioConfig.device,
                            position = response.position ?: state.radioConfig.position,
                            power = response.power ?: state.radioConfig.power,
                            network = response.network ?: state.radioConfig.network,
                            display = response.display ?: state.radioConfig.display,
                            lora = response.lora ?: state.radioConfig.lora,
                            bluetooth = response.bluetooth ?: state.radioConfig.bluetooth,
                            security = response.security ?: state.radioConfig.security,
                        ),
                    )
                }
                incrementCompleted()
            }

            is RadioResponseResult.ModuleConfigResponse -> {
                val response = result.config
                _radioConfigState.update { state ->
                    state.copy(
                        moduleConfig =
                        state.moduleConfig.copy(
                            mqtt = response.mqtt ?: state.moduleConfig.mqtt,
                            serial = response.serial ?: state.moduleConfig.serial,
                            external_notification =
                            response.external_notification ?: state.moduleConfig.external_notification,
                            store_forward = response.store_forward ?: state.moduleConfig.store_forward,
                            range_test = response.range_test ?: state.moduleConfig.range_test,
                            telemetry = response.telemetry ?: state.moduleConfig.telemetry,
                            canned_message = response.canned_message ?: state.moduleConfig.canned_message,
                            audio = response.audio ?: state.moduleConfig.audio,
                            remote_hardware = response.remote_hardware ?: state.moduleConfig.remote_hardware,
                            neighbor_info = response.neighbor_info ?: state.moduleConfig.neighbor_info,
                            ambient_lighting = response.ambient_lighting ?: state.moduleConfig.ambient_lighting,
                            detection_sensor = response.detection_sensor ?: state.moduleConfig.detection_sensor,
                            paxcounter = response.paxcounter ?: state.moduleConfig.paxcounter,
                            statusmessage = response.statusmessage ?: state.moduleConfig.statusmessage,
                            traffic_management =
                            response.traffic_management ?: state.moduleConfig.traffic_management,
                            tak = response.tak ?: state.moduleConfig.tak,
                        ),
                    )
                }
                incrementCompleted()
            }

            is RadioResponseResult.CannedMessages -> {
                _radioConfigState.update { it.copy(cannedMessageMessages = result.messages) }
                incrementCompleted()
            }

            is RadioResponseResult.Ringtone -> {
                _radioConfigState.update { it.copy(ringtone = result.ringtone) }
                incrementCompleted()
            }

            is RadioResponseResult.ConnectionStatus -> {
                _radioConfigState.update { it.copy(deviceConnectionStatus = result.status) }
                incrementCompleted()
            }
        }

        // Routing ACKs (Success) share the same request_id as the upcoming ADMIN_APP response.
        // Removing the id here would cause the actual admin response to be silently dropped,
        // because processRadioResponseUseCase checks `request_id in requestIds`.
        // The Success branch already handles its own id removal when route is empty (set flow).
        if (result is RadioResponseResult.Success) return

        if (AdminRoute.entries.any { it.name == route }) {
            sendAdminRequest(destNum)
        }

        val requestId = packet.decoded?.request_id ?: return
        requestIds.update { it.apply { remove(requestId) } }

        if (requestIds.value.isEmpty()) {
            if (route.isNotEmpty() && !AdminRoute.entries.any { it.name == route }) {
                clearPacketResponse()
            } else if (route.isEmpty()) {
                setResponseStateSuccess()
            }
        }
    }
}
