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

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.RemoteException
import android.util.Base64
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import co.touchlab.kermit.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource
import org.json.JSONObject
import org.meshtastic.core.data.repository.LocationRepository
import org.meshtastic.core.data.repository.NodeRepository
import org.meshtastic.core.data.repository.PacketRepository
import org.meshtastic.core.data.repository.RadioConfigRepository
import org.meshtastic.core.database.entity.MyNodeEntity
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.database.model.getStringResFrom
import org.meshtastic.core.model.Position
import org.meshtastic.core.model.util.toChannelSet
import org.meshtastic.core.navigation.SettingsRoutes
import org.meshtastic.core.prefs.analytics.AnalyticsPrefs
import org.meshtastic.core.prefs.map.MapConsentPrefs
import org.meshtastic.core.service.ConnectionState
import org.meshtastic.core.service.IMeshService
import org.meshtastic.core.service.ServiceRepository
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.cant_shutdown
import org.meshtastic.core.ui.util.getChannelList
import org.meshtastic.feature.settings.navigation.ConfigRoute
import org.meshtastic.feature.settings.navigation.ModuleRoute
import org.meshtastic.feature.settings.util.UiText
import org.meshtastic.proto.AdminMessage
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config
import org.meshtastic.proto.DeviceConnectionStatus
import org.meshtastic.proto.DeviceMetadata
import org.meshtastic.proto.DeviceProfile
import org.meshtastic.proto.HardwareModel
import org.meshtastic.proto.LocalModuleConfig
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.ModuleConfig
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.Routing
import org.meshtastic.proto.User
import java.io.FileOutputStream
import javax.inject.Inject

/** Data class that represents the current RadioConfig state. */
data class RadioConfigState(
    val isLocal: Boolean = false,
    val connected: Boolean = false,
    val route: String = "",
    val metadata: DeviceMetadata? = null,
    val userConfig: User = User(),
    val channelList: List<ChannelSettings> = emptyList(),
    val radioConfig: Config = Config(),
    val moduleConfig: LocalModuleConfig = LocalModuleConfig(),
    val ringtone: String = "",
    val cannedMessageMessages: String = "",
    val deviceConnectionStatus: DeviceConnectionStatus? = null,
    val responseState: ResponseState<Boolean> = ResponseState.Empty,
    val analyticsAvailable: Boolean = true,
    val analyticsEnabled: Boolean = false,
    val nodeDbResetPreserveFavorites: Boolean = false,
)

@Suppress("LongParameterList")
@HiltViewModel
class RadioConfigViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val app: Application,
    private val radioConfigRepository: RadioConfigRepository,
    private val packetRepository: PacketRepository,
    private val serviceRepository: ServiceRepository,
    private val nodeRepository: NodeRepository,
    private val locationRepository: LocationRepository,
    private val mapConsentPrefs: MapConsentPrefs,
    private val analyticsPrefs: AnalyticsPrefs,
) : ViewModel() {
    private val meshService: IMeshService?
        get() = serviceRepository.meshService

    var analyticsAllowedFlow = analyticsPrefs.getAnalyticsAllowedChangesFlow()

    fun toggleAnalyticsAllowed() {
        analyticsPrefs.analyticsAllowed = !analyticsPrefs.analyticsAllowed
    }

    private val destNum =
        savedStateHandle.get<Int>("destNum")
            ?: runCatching { savedStateHandle.toRoute<SettingsRoutes.Settings>().destNum }.getOrNull()

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

    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    suspend fun getCurrentLocation(): Location? = if (
        ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    ) {
        locationRepository.getLocations().firstOrNull()
    } else {
        null
    }

    init {
        nodeRepository.nodeDBbyNum
            .mapLatest { nodes -> nodes[destNum] ?: nodes.values.firstOrNull() }
            .distinctUntilChanged()
            .onEach {
                _destNode.value = it
                _radioConfigState.update { state -> state.copy(metadata = it?.metadata) }
            }
            .launchIn(viewModelScope)

        radioConfigRepository.deviceProfileFlow.onEach { _currentDeviceProfile.value = it }.launchIn(viewModelScope)

        serviceRepository.meshPacketFlow.onEach(::processPacketResponse).launchIn(viewModelScope)

        combine(serviceRepository.connectionState, radioConfigState) { connState, configState ->
            _radioConfigState.update { it.copy(connected = connState == ConnectionState.Connected) }
        }
            .launchIn(viewModelScope)

        nodeRepository.myNodeInfo
            .onEach { ni ->
                _radioConfigState.update { it.copy(isLocal = destNum == null || destNum == ni?.myNodeNum) }
            }
            .launchIn(viewModelScope)

        Logger.d { "RadioConfigViewModel created" }
    }

    private val myNodeInfo: StateFlow<MyNodeEntity?>
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

    private fun request(destNum: Int, requestAction: suspend (IMeshService, Int, Int) -> Unit, errorMessage: String) =
        viewModelScope.launch {
            meshService?.let { service ->
                val packetId = service.packetId
                try {
                    requestAction(service, packetId, destNum)
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
                } catch (ex: RemoteException) {
                    Logger.e { "$errorMessage: ${ex.message}" }
                }
            }
        }

    fun setOwner(user: User) {
        setRemoteOwner(destNode.value?.num ?: return, user)
    }

    private fun setRemoteOwner(destNum: Int, user: User) = request(
        destNum,
        { service, packetId, _ ->
            _radioConfigState.update { it.copy(userConfig = user) }
            service.setRemoteOwner(packetId, destNum, user.encode())
        },
        "Request setOwner error",
    )

    private fun getOwner(destNum: Int) = request(
        destNum,
        { service, packetId, dest -> service.getRemoteOwner(packetId, dest) },
        "Request getOwner error",
    )

    fun updateChannels(new: List<ChannelSettings>, old: List<ChannelSettings>) {
        val destNum = destNode.value?.num ?: return
        getChannelList(new, old).forEach { setRemoteChannel(destNum, it) }

        if (destNum == myNodeNum) {
            viewModelScope.launch {
                packetRepository.migrateChannelsByPSK(old, new)
                radioConfigRepository.replaceAllSettings(new)
            }
        }
        _radioConfigState.update { it.copy(channelList = new) }
    }

    private fun setChannels(channelUrl: String) = viewModelScope.launch {
        val new = channelUrl.toUri().toChannelSet()
        val old = radioConfigRepository.channelSetFlow.firstOrNull() ?: return@launch
        updateChannels(new.settings, old.settings)
    }

    private fun setRemoteChannel(destNum: Int, channel: Channel) = request(
        destNum,
        { service, packetId, dest -> service.setRemoteChannel(packetId, dest, channel.encode()) },
        "Request setRemoteChannel error",
    )

    private fun getChannel(destNum: Int, index: Int) = request(
        destNum,
        { service, packetId, dest -> service.getRemoteChannel(packetId, dest, index) },
        "Request getChannel error",
    )

    fun setConfig(config: Config) {
        setRemoteConfig(destNode.value?.num ?: return, config)
    }

    private fun setRemoteConfig(destNum: Int, config: Config) = request(
        destNum,
        { service, packetId, dest ->
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
            service.setRemoteConfig(packetId, dest, config.encode())
        },
        "Request setConfig error",
    )

    private fun getConfig(destNum: Int, configType: Int) = request(
        destNum,
        { service, packetId, dest -> service.getRemoteConfig(packetId, dest, configType) },
        "Request getConfig error",
    )

    fun setModuleConfig(config: ModuleConfig) {
        setRemoteModuleConfig(destNode.value?.num ?: return, config)
    }

    private fun setRemoteModuleConfig(destNum: Int, config: ModuleConfig) = request(
        destNum,
        { service, packetId, dest ->
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
                    ),
                )
            }
            service.setModuleConfig(packetId, dest, config.encode())
        },
        "Request setModuleConfig error",
    )

    private fun getModuleConfig(destNum: Int, configType: Int) = request(
        destNum,
        { service, packetId, dest -> service.getModuleConfig(packetId, dest, configType) },
        "Request getModuleConfig error",
    )

    fun setRingtone(ringtone: String) {
        val destNum = destNode.value?.num ?: return
        _radioConfigState.update { it.copy(ringtone = ringtone) }
        meshService?.setRingtone(destNum, ringtone)
    }

    private fun getRingtone(destNum: Int) = request(
        destNum,
        { service, packetId, dest -> service.getRingtone(packetId, dest) },
        "Request getRingtone error",
    )

    fun setCannedMessages(messages: String) {
        val destNum = destNode.value?.num ?: return
        _radioConfigState.update { it.copy(cannedMessageMessages = messages) }
        meshService?.setCannedMessages(destNum, messages)
    }

    private fun getCannedMessages(destNum: Int) = request(
        destNum,
        { service, packetId, dest -> service.getCannedMessages(packetId, dest) },
        "Request getCannedMessages error",
    )

    private fun getDeviceConnectionStatus(destNum: Int) = request(
        destNum,
        { service, packetId, dest -> service.getDeviceConnectionStatus(packetId, dest) },
        "Request getDeviceConnectionStatus error",
    )

    private fun requestShutdown(destNum: Int) = request(
        destNum,
        { service, packetId, dest -> service.requestShutdown(packetId, dest) },
        "Request shutdown error",
    )

    private fun requestReboot(destNum: Int) =
        request(destNum, { service, packetId, dest -> service.requestReboot(packetId, dest) }, "Request reboot error")

    private fun requestFactoryReset(destNum: Int) {
        request(
            destNum,
            { service, packetId, dest -> service.requestFactoryReset(packetId, dest) },
            "Request factory reset error",
        )
        if (destNum == myNodeNum) {
            viewModelScope.launch {
                // Clear the service's in-memory node cache first so screens refresh immediately.
                val existingNodeNums = nodeRepository.getNodeDBbyNum().firstOrNull()?.keys?.toList().orEmpty()
                meshService?.let { service ->
                    existingNodeNums.forEach { service.removeByNodenum(service.packetId, it) }
                }
                nodeRepository.clearNodeDB()
            }
        }
    }

    private fun requestNodedbReset(destNum: Int, preserveFavorites: Boolean) {
        request(
            destNum,
            { service, packetId, dest -> service.requestNodedbReset(packetId, dest, preserveFavorites) },
            "Request NodeDB reset error",
        )
        if (destNum == myNodeNum) {
            viewModelScope.launch {
                // Clear the service's in-memory node cache as well so UI updates immediately.
                val existingNodeNums = nodeRepository.getNodeDBbyNum().firstOrNull()?.keys?.toList().orEmpty()
                meshService?.let { service ->
                    existingNodeNums.forEach { service.removeByNodenum(service.packetId, it) }
                }
                nodeRepository.clearNodeDB(preserveFavorites)
            }
        }
    }

    private fun sendAdminRequest(destNum: Int) {
        val route = radioConfigState.value.route
        _radioConfigState.update { it.copy(route = "") } // setter (response is PortNum.ROUTING_APP)

        val preserveFavorites = radioConfigState.value.nodeDbResetPreserveFavorites

        when (route) {
            AdminRoute.REBOOT.name -> requestReboot(destNum)
            AdminRoute.SHUTDOWN.name ->
                with(radioConfigState.value) {
                    if (metadata != null && metadata.canShutdown != true) {
                        sendError(Res.string.cant_shutdown)
                    } else {
                        requestShutdown(destNum)
                    }
                }

            AdminRoute.FACTORY_RESET.name -> requestFactoryReset(destNum)
            AdminRoute.NODEDB_RESET.name -> requestNodedbReset(destNum, preserveFavorites)
        }
    }

    fun setFixedPosition(position: Position) {
        val destNum = destNode.value?.num ?: return
        try {
            meshService?.setFixedPosition(destNum, position)
        } catch (ex: RemoteException) {
            Logger.e { "Set fixed position error: ${ex.message}" }
        }
    }

    fun removeFixedPosition() = setFixedPosition(Position(0.0, 0.0, 0))

    fun importProfile(uri: Uri, onResult: (DeviceProfile) -> Unit) = viewModelScope.launch(Dispatchers.IO) {
        try {
            app.contentResolver.openInputStream(uri).use { inputStream ->
                val bytes = inputStream?.readBytes() ?: ByteArray(0)
                val protobuf = DeviceProfile.ADAPTER.decode(bytes)
                onResult(protobuf)
            }
        } catch (ex: Exception) {
            Logger.e { "Import DeviceProfile error: ${ex.message}" }
            sendError(ex.customMessage)
        }
    }

    fun exportProfile(uri: Uri, profile: DeviceProfile) = viewModelScope.launch { writeToUri(uri, profile) }

    private suspend fun writeToUri(uri: Uri, message: com.squareup.wire.Message<*, *>) = withContext(Dispatchers.IO) {
        try {
            app.contentResolver.openFileDescriptor(uri, "wt")?.use { parcelFileDescriptor ->
                FileOutputStream(parcelFileDescriptor.fileDescriptor).use { outputStream ->
                    outputStream.write(message.encode())
                }
            }
            setResponseStateSuccess()
        } catch (ex: Exception) {
            Logger.e { "Can't write file error: ${ex.message}" }
            sendError(ex.customMessage)
        }
    }

    fun exportSecurityConfig(uri: Uri, securityConfig: Config.SecurityConfig) =
        viewModelScope.launch { writeSecurityKeysJsonToUri(uri, securityConfig) }

    private val indentSpaces = 4

    private suspend fun writeSecurityKeysJsonToUri(uri: Uri, securityConfig: Config.SecurityConfig) =
        withContext(Dispatchers.IO) {
            try {
                val publicKeyBytes = securityConfig.public_key?.toByteArray() ?: ByteArray(0)
                val privateKeyBytes = securityConfig.private_key?.toByteArray() ?: ByteArray(0)

                // Convert byte arrays to Base64 strings for human readability in JSON
                val publicKeyBase64 = Base64.encodeToString(publicKeyBytes, Base64.NO_WRAP)
                val privateKeyBase64 = Base64.encodeToString(privateKeyBytes, Base64.NO_WRAP)

                // Create a JSON object
                val jsonObject =
                    JSONObject().apply {
                        put("timestamp", System.currentTimeMillis())
                        put("public_key", publicKeyBase64)
                        put("private_key", privateKeyBase64)
                    }

                // Convert JSON object to a string
                val jsonString = jsonObject.toString(indentSpaces)

                app.contentResolver.openFileDescriptor(uri, "wt")?.use { parcelFileDescriptor ->
                    FileOutputStream(parcelFileDescriptor.fileDescriptor).use { outputStream ->
                        outputStream.write(jsonString.toByteArray(Charsets.UTF_8))
                    }
                }
                setResponseStateSuccess()
            } catch (ex: Exception) {
                val errorMessage = "Can't write security keys JSON error: ${ex.message}"
                Logger.e { errorMessage }
                sendError(ex.customMessage)
            }
        }

    fun installProfile(protobuf: DeviceProfile) {
        val destNum = destNode.value?.num ?: return
        with(protobuf) {
            meshService?.beginEditSettings(destNum)
            if (long_name != null || short_name != null) {
                destNode.value?.user?.let {
                    val user = it.copy(long_name = long_name ?: it.long_name, short_name = short_name ?: it.short_name)
                    setOwner(user)
                }
            }
            config?.let { lc ->
                lc.device?.let { setConfig(Config(device = it)) }
                lc.position?.let { setConfig(Config(position = it)) }
                lc.power?.let { setConfig(Config(power = it)) }
                lc.network?.let { setConfig(Config(network = it)) }
                lc.display?.let { setConfig(Config(display = it)) }
                lc.lora?.let { setConfig(Config(lora = it)) }
                lc.bluetooth?.let { setConfig(Config(bluetooth = it)) }
                lc.security?.let { setConfig(Config(security = it)) }
            }
            if (fixed_position != null) {
                setFixedPosition(Position(fixed_position!!))
            }
            module_config?.let { lmc ->
                lmc.mqtt?.let { setModuleConfig(ModuleConfig(mqtt = it)) }
                lmc.serial?.let { setModuleConfig(ModuleConfig(serial = it)) }
                lmc.external_notification?.let { setModuleConfig(ModuleConfig(external_notification = it)) }
                lmc.store_forward?.let { setModuleConfig(ModuleConfig(store_forward = it)) }
                lmc.range_test?.let { setModuleConfig(ModuleConfig(range_test = it)) }
                lmc.telemetry?.let { setModuleConfig(ModuleConfig(telemetry = it)) }
                lmc.canned_message?.let { setModuleConfig(ModuleConfig(canned_message = it)) }
                lmc.audio?.let { setModuleConfig(ModuleConfig(audio = it)) }
                lmc.remote_hardware?.let { setModuleConfig(ModuleConfig(remote_hardware = it)) }
                lmc.neighbor_info?.let { setModuleConfig(ModuleConfig(neighbor_info = it)) }
                lmc.ambient_lighting?.let { setModuleConfig(ModuleConfig(ambient_lighting = it)) }
                lmc.detection_sensor?.let { setModuleConfig(ModuleConfig(detection_sensor = it)) }
                lmc.paxcounter?.let { setModuleConfig(ModuleConfig(paxcounter = it)) }
                lmc.statusmessage?.let { setModuleConfig(ModuleConfig(statusmessage = it)) }
            }
            meshService?.commitEditSettings(destNum)
        }
    }

    fun clearPacketResponse() {
        requestIds.value = hashSetOf()
        _radioConfigState.update { it.copy(responseState = ResponseState.Empty) }
    }

    fun setResponseStateLoading(route: Enum<*>) {
        val destNum = destNode.value?.num ?: return

        _radioConfigState.update {
            RadioConfigState(
                isLocal = it.isLocal,
                connected = it.connected,
                route = route.name,
                metadata = it.metadata,
                nodeDbResetPreserveFavorites = it.nodeDbResetPreserveFavorites,
                responseState = ResponseState.Loading(),
            )
        }

        when (route) {
            ConfigRoute.USER -> getOwner(destNum)

            ConfigRoute.CHANNELS -> {
                getChannel(destNum, 0)
                getConfig(destNum, ConfigRoute.LORA.type)
                // channel editor is synchronous, so we don't use requestIds as total
                setResponseStateTotal(maxChannels + 1)
            }

            is AdminRoute -> {
                getConfig(destNum, AdminMessage.ConfigType.SESSIONKEY_CONFIG.value)
                setResponseStateTotal(2)
            }

            is ConfigRoute -> {
                if (route == ConfigRoute.LORA) {
                    getChannel(destNum, 0)
                }
                if (route == ConfigRoute.NETWORK) {
                    getDeviceConnectionStatus(destNum)
                }
                getConfig(destNum, route.type)
            }

            is ModuleRoute -> {
                if (route == ModuleRoute.CANNED_MESSAGE) {
                    getCannedMessages(destNum)
                }
                if (route == ModuleRoute.EXT_NOTIFICATION) {
                    getRingtone(destNum)
                }
                getModuleConfig(destNum, route.type)
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

    private fun setResponseStateSuccess() {
        _radioConfigState.update { state ->
            if (state.responseState is ResponseState.Loading) {
                state.copy(responseState = ResponseState.Success(true))
            } else {
                state // Return the unchanged state for other response states
            }
        }
    }

    private val Exception.customMessage: String
        get() = "${javaClass.simpleName}: $message"

    private fun sendError(error: String) = setResponseStateError(UiText.DynamicString(error))

    private fun sendError(id: StringResource) = setResponseStateError(UiText.StringResource(id))

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

    private fun processPacketResponse(packet: MeshPacket) {
        val data = packet.decoded ?: return
        if (data.request_id !in requestIds.value) return
        val route = radioConfigState.value.route

        val destNum = destNode.value?.num ?: return
        val debugMsg = "requestId: ${data.request_id.toUInt()} to: ${destNum.toUInt()} received %s"

        if (data.portnum == PortNum.ROUTING_APP) {
            val parsed = Routing.ADAPTER.decode(data.payload)
            Logger.d { debugMsg.format(parsed.error_reason?.name) }
            if (parsed.error_reason != Routing.Error.NONE) {
                sendError(getStringResFrom(parsed.error_reason?.value ?: 0))
            } else if (packet.from == destNum && route.isEmpty()) {
                requestIds.update { it.apply { remove(data.request_id) } }
                if (requestIds.value.isEmpty()) {
                    setResponseStateSuccess()
                } else {
                    incrementCompleted()
                }
            }
        }
        if (data.portnum == PortNum.ADMIN_APP) {
            val parsed = AdminMessage.ADAPTER.decode(data.payload)
            // Explicitly log the non-null field name for clarity
            val variant =
                when {
                    parsed.get_device_metadata_response != null -> "get_device_metadata_response"
                    parsed.get_channel_response != null -> "get_channel_response"
                    parsed.get_owner_response != null -> "get_owner_response"
                    parsed.get_config_response != null -> "get_config_response"
                    parsed.get_module_config_response != null -> "get_module_config_response"
                    parsed.get_canned_message_module_messages_response != null ->
                        "get_canned_message_module_messages_response"
                    parsed.get_ringtone_response != null -> "get_ringtone_response"
                    parsed.get_device_connection_status_response != null -> "get_device_connection_status_response"
                    else -> "unknown"
                }
            Logger.d { debugMsg.format(variant) }
            if (destNum != packet.from) {
                sendError("Unexpected sender: ${packet.from.toUInt()} instead of ${destNum.toUInt()}.")
                return
            }
            when {
                parsed.get_device_metadata_response != null -> {
                    _radioConfigState.update { it.copy(metadata = parsed.get_device_metadata_response) }
                    incrementCompleted()
                }

                parsed.get_channel_response != null -> {
                    val response = parsed.get_channel_response!!
                    // Stop once we get to the first disabled entry
                    if (response.role != Channel.Role.DISABLED) {
                        _radioConfigState.update { state ->
                            state.copy(
                                channelList =
                                state.channelList.toMutableList().apply {
                                    val index = response.index ?: 0
                                    val settings = response.settings ?: ChannelSettings()
                                    // Make sure list is large enough
                                    while (size <= index) add(ChannelSettings())
                                    set(index, settings)
                                },
                            )
                        }
                        incrementCompleted()
                        val index = response.index ?: 0
                        if (index + 1 < maxChannels && route == ConfigRoute.CHANNELS.name) {
                            // Not done yet, request next channel
                            getChannel(destNum, index + 1)
                        }
                    } else {
                        // Received last channel, update total and start channel editor
                        setResponseStateTotal((response.index ?: 0) + 1)
                    }
                }

                parsed.get_owner_response != null -> {
                    _radioConfigState.update { it.copy(userConfig = parsed.get_owner_response!!) }
                    incrementCompleted()
                }

                parsed.get_config_response != null -> {
                    val response = parsed.get_config_response!!
                    _radioConfigState.update { it.copy(radioConfig = response) }
                    incrementCompleted()
                }

                parsed.get_module_config_response != null -> {
                    val response = parsed.get_module_config_response!!
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
                            ),
                        )
                    }
                    incrementCompleted()
                }

                parsed.get_canned_message_module_messages_response != null -> {
                    _radioConfigState.update {
                        it.copy(cannedMessageMessages = parsed.get_canned_message_module_messages_response!!)
                    }
                    incrementCompleted()
                }

                parsed.get_ringtone_response != null -> {
                    _radioConfigState.update { it.copy(ringtone = parsed.get_ringtone_response!!) }
                    incrementCompleted()
                }

                parsed.get_device_connection_status_response != null -> {
                    _radioConfigState.update {
                        it.copy(deviceConnectionStatus = parsed.get_device_connection_status_response)
                    }
                    incrementCompleted()
                }

                else -> Logger.d { "No custom processing needed for $parsed" }
            }

            if (AdminRoute.entries.any { it.name == route }) {
                sendAdminRequest(destNum)
            }
            requestIds.update { it.apply { remove(data.request_id) } }
        }
    }
}
