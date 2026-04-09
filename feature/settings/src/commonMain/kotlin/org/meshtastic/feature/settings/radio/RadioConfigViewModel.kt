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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.koin.core.annotation.InjectedParam
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.common.util.MeshtasticUri
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
import org.meshtastic.core.model.MyNodeInfo
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.Position
import org.meshtastic.core.repository.AnalyticsPrefs
import org.meshtastic.core.repository.FileService
import org.meshtastic.core.repository.HomoglyphPrefs
import org.meshtastic.core.repository.LocationRepository
import org.meshtastic.core.repository.LocationService
import org.meshtastic.core.repository.MapConsentPrefs
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.UiText
import org.meshtastic.core.resources.cant_shutdown
import org.meshtastic.core.resources.timeout
import org.meshtastic.core.ui.util.getChannelList
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
) : ViewModel() {
    val analyticsAllowedFlow = analyticsPrefs.analyticsAllowed

    fun toggleAnalyticsAllowed() {
        toggleAnalyticsUseCase()
    }

    val homoglyphEncodingEnabledFlow = homoglyphEncodingPrefs.homoglyphEncodingEnabled

    fun toggleHomoglyphCharactersEncodingEnabled() {
        toggleHomoglyphEncodingUseCase()
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
        viewModelScope.launch { _radioConfigState.update { it.copy(nodeDbResetPreserveFavorites = preserveFavorites) } }
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
        viewModelScope.launch {
            _radioConfigState.update { it.copy(userConfig = user) }
            val packetId = radioConfigUseCase.setOwner(destNum, user)
            registerRequestId(packetId)
        }
    }

    fun updateChannels(new: List<ChannelSettings>, old: List<ChannelSettings>) {
        val destNum = destNode.value?.num ?: return
        getChannelList(new, old).forEach { channel ->
            viewModelScope.launch {
                val packetId = radioConfigUseCase.setRemoteChannel(destNum, channel)
                registerRequestId(packetId)
            }
        }

        if (destNum == myNodeNum) {
            viewModelScope.launch {
                packetRepository.migrateChannelsByPSK(old, new)
                radioConfigRepository.replaceAllSettings(new)
            }
        }
        _radioConfigState.update { it.copy(channelList = new) }
    }

    fun setConfig(config: Config) {
        val destNum = destNode.value?.num ?: return
        viewModelScope.launch {
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
        viewModelScope.launch {
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
        viewModelScope.launch { radioConfigUseCase.setRingtone(destNum, ringtone) }
    }

    fun setCannedMessages(messages: String) {
        val destNum = destNode.value?.num ?: return
        _radioConfigState.update { it.copy(cannedMessageMessages = messages) }
        viewModelScope.launch { radioConfigUseCase.setCannedMessages(destNum, messages) }
    }

    private fun sendAdminRequest(destNum: Int) {
        val route = radioConfigState.value.route
        _radioConfigState.update { it.copy(route = "") } // setter (response is PortNum.ROUTING_APP)

        val preserveFavorites = radioConfigState.value.nodeDbResetPreserveFavorites

        when (route) {
            AdminRoute.REBOOT.name ->
                viewModelScope.launch {
                    val packetId = adminActionsUseCase.reboot(destNum)
                    registerRequestId(packetId)
                }
            AdminRoute.SHUTDOWN.name ->
                with(radioConfigState.value) {
                    if (metadata?.canShutdown != true) {
                        sendError(Res.string.cant_shutdown)
                    } else {
                        viewModelScope.launch {
                            val packetId = adminActionsUseCase.shutdown(destNum)
                            registerRequestId(packetId)
                        }
                    }
                }

            AdminRoute.FACTORY_RESET.name ->
                viewModelScope.launch {
                    val isLocal = (destNum == myNodeNum)
                    val packetId = adminActionsUseCase.factoryReset(destNum, isLocal)
                    registerRequestId(packetId)
                }
            AdminRoute.NODEDB_RESET.name ->
                viewModelScope.launch {
                    val isLocal = (destNum == myNodeNum)
                    val packetId = adminActionsUseCase.nodedbReset(destNum, preserveFavorites, isLocal)
                    registerRequestId(packetId)
                }
        }
    }

    fun setFixedPosition(position: Position) {
        val destNum = destNode.value?.num ?: return
        viewModelScope.launch { radioConfigUseCase.setFixedPosition(destNum, position) }
    }

    fun removeFixedPosition() {
        val destNum = destNode.value?.num ?: return
        viewModelScope.launch { radioConfigUseCase.removeFixedPosition(destNum) }
    }

    fun importProfile(uri: MeshtasticUri, onResult: (DeviceProfile) -> Unit) {
        viewModelScope.launch {
            try {
                var profile: DeviceProfile? = null
                fileService.read(uri) { source ->
                    importProfileUseCase(source).onSuccess { profile = it }.onFailure { throw it }
                }
                profile?.let { onResult(it) }
            } catch (ex: Exception) {
                Logger.e { "Import DeviceProfile error: ${ex.message}" }
            }
        }
    }

    fun exportProfile(uri: MeshtasticUri, profile: DeviceProfile) {
        viewModelScope.launch {
            try {
                fileService.write(uri) { sink ->
                    exportProfileUseCase(sink, profile).onSuccess { /* Success */ }.onFailure { throw it }
                }
            } catch (ex: Exception) {
                Logger.e { "Can't write file error: ${ex.message}" }
            }
        }
    }

    fun exportSecurityConfig(uri: MeshtasticUri, securityConfig: Config.SecurityConfig) {
        viewModelScope.launch {
            try {
                fileService.write(uri) { sink ->
                    exportSecurityConfigUseCase(sink, securityConfig).onSuccess { /* Success */ }.onFailure { throw it }
                }
            } catch (ex: Exception) {
                Logger.e { "Can't write security keys JSON error: ${ex.message}" }
            }
        }
    }

    fun installProfile(protobuf: DeviceProfile) {
        val destNum = destNode.value?.num ?: return
        viewModelScope.launch { installProfileUseCase(destNum, protobuf, destNode.value?.user) }
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
                viewModelScope.launch {
                    val packetId = radioConfigUseCase.getOwner(destNum)
                    registerRequestId(packetId)
                }

            ConfigRoute.CHANNELS -> {
                viewModelScope.launch {
                    val packetId = radioConfigUseCase.getChannel(destNum, 0)
                    registerRequestId(packetId)
                }
                viewModelScope.launch {
                    val packetId = radioConfigUseCase.getConfig(destNum, AdminMessage.ConfigType.LORA_CONFIG.value)
                    registerRequestId(packetId)
                }
                // channel editor is synchronous, so we don't use requestIds as total
                setResponseStateTotal(maxChannels + 1)
            }

            is AdminRoute -> {
                viewModelScope.launch {
                    val packetId =
                        radioConfigUseCase.getConfig(destNum, AdminMessage.ConfigType.SESSIONKEY_CONFIG.value)
                    registerRequestId(packetId)
                }
                setResponseStateTotal(2)
            }

            is ConfigRoute -> {
                if (route == ConfigRoute.LORA) {
                    viewModelScope.launch {
                        val packetId = radioConfigUseCase.getChannel(destNum, 0)
                        registerRequestId(packetId)
                    }
                }
                if (route == ConfigRoute.NETWORK) {
                    viewModelScope.launch {
                        val packetId = radioConfigUseCase.getDeviceConnectionStatus(destNum)
                        registerRequestId(packetId)
                    }
                }
                viewModelScope.launch {
                    val packetId = radioConfigUseCase.getConfig(destNum, route.type)
                    registerRequestId(packetId)
                }
            }

            is ModuleRoute -> {
                if (route == ModuleRoute.CANNED_MESSAGE) {
                    viewModelScope.launch {
                        val packetId = radioConfigUseCase.getCannedMessages(destNum)
                        registerRequestId(packetId)
                    }
                }
                if (route == ModuleRoute.EXT_NOTIFICATION) {
                    viewModelScope.launch {
                        val packetId = radioConfigUseCase.getRingtone(destNum)
                        registerRequestId(packetId)
                    }
                }
                viewModelScope.launch {
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
        viewModelScope.launch {
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
                        viewModelScope.launch {
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
