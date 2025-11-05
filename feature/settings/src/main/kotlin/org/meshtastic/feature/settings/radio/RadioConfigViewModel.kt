/*
 * Copyright (c) 2025 Meshtastic LLC
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
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.google.protobuf.MessageLite
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
import org.meshtastic.core.ui.util.getChannelList
import org.meshtastic.feature.settings.navigation.ConfigRoute
import org.meshtastic.feature.settings.navigation.ModuleRoute
import org.meshtastic.feature.settings.util.UiText
import org.meshtastic.proto.AdminProtos
import org.meshtastic.proto.ChannelProtos
import org.meshtastic.proto.ClientOnlyProtos.DeviceProfile
import org.meshtastic.proto.ConfigProtos
import org.meshtastic.proto.ConfigProtos.Config.SecurityConfig
import org.meshtastic.proto.ConnStatusProtos
import org.meshtastic.proto.MeshProtos
import org.meshtastic.proto.ModuleConfigProtos
import org.meshtastic.proto.Portnums
import org.meshtastic.proto.config
import org.meshtastic.proto.deviceProfile
import org.meshtastic.proto.moduleConfig
import timber.log.Timber
import java.io.FileOutputStream
import javax.inject.Inject
import org.meshtastic.core.strings.R as Res

/** Data class that represents the current RadioConfig state. */
data class RadioConfigState(
    val isLocal: Boolean = false,
    val connected: Boolean = false,
    val route: String = "",
    val metadata: MeshProtos.DeviceMetadata? = null,
    val userConfig: MeshProtos.User = MeshProtos.User.getDefaultInstance(),
    val channelList: List<ChannelProtos.ChannelSettings> = emptyList(),
    val radioConfig: ConfigProtos.Config = config {},
    val moduleConfig: ModuleConfigProtos.ModuleConfig = moduleConfig {},
    val ringtone: String = "",
    val cannedMessageMessages: String = "",
    val deviceConnectionStatus: ConnStatusProtos.DeviceConnectionStatus? = null,
    val responseState: ResponseState<Boolean> = ResponseState.Empty,
    val analyticsAvailable: Boolean = true,
    val analyticsEnabled: Boolean = false,
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

    private val destNum = savedStateHandle.toRoute<SettingsRoutes.Settings>().destNum
    private val _destNode = MutableStateFlow<Node?>(null)
    val destNode: StateFlow<Node?>
        get() = _destNode

    private val requestIds = MutableStateFlow(hashSetOf<Int>())
    private val _radioConfigState = MutableStateFlow(RadioConfigState())
    val radioConfigState: StateFlow<RadioConfigState> = _radioConfigState

    private val _currentDeviceProfile = MutableStateFlow(deviceProfile {})
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
            _radioConfigState.update { it.copy(connected = connState == ConnectionState.CONNECTED) }
        }
            .launchIn(viewModelScope)

        nodeRepository.myNodeInfo
            .onEach { ni ->
                _radioConfigState.update { it.copy(isLocal = destNum == null || destNum == ni?.myNodeNum) }
            }
            .launchIn(viewModelScope)

        Timber.d("RadioConfigViewModel created")
    }

    private val myNodeInfo: StateFlow<MyNodeEntity?>
        get() = nodeRepository.myNodeInfo

    val myNodeNum
        get() = myNodeInfo.value?.myNodeNum

    val maxChannels
        get() = myNodeInfo.value?.maxChannels ?: 8

    val hasPaFan: Boolean
        get() =
            destNode.value?.user?.hwModel in
                setOf(
                    null,
                    MeshProtos.HardwareModel.UNRECOGNIZED,
                    MeshProtos.HardwareModel.UNSET,
                    MeshProtos.HardwareModel.BETAFPV_2400_TX,
                    MeshProtos.HardwareModel.RADIOMASTER_900_BANDIT_NANO,
                    MeshProtos.HardwareModel.RADIOMASTER_900_BANDIT,
                )

    override fun onCleared() {
        super.onCleared()
        Timber.d("RadioConfigViewModel cleared")
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
                    Timber.e("$errorMessage: ${ex.message}")
                }
            }
        }

    fun setOwner(user: MeshProtos.User) {
        setRemoteOwner(destNode.value?.num ?: return, user)
    }

    private fun setRemoteOwner(destNum: Int, user: MeshProtos.User) = request(
        destNum,
        { service, packetId, _ ->
            _radioConfigState.update { it.copy(userConfig = user) }
            service.setRemoteOwner(packetId, user.toByteArray())
        },
        "Request setOwner error",
    )

    private fun getOwner(destNum: Int) = request(
        destNum,
        { service, packetId, dest -> service.getRemoteOwner(packetId, dest) },
        "Request getOwner error",
    )

    fun updateChannels(new: List<ChannelProtos.ChannelSettings>, old: List<ChannelProtos.ChannelSettings>) {
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
        updateChannels(new.settingsList, old.settingsList)
    }

    private fun setRemoteChannel(destNum: Int, channel: ChannelProtos.Channel) = request(
        destNum,
        { service, packetId, dest -> service.setRemoteChannel(packetId, dest, channel.toByteArray()) },
        "Request setRemoteChannel error",
    )

    private fun getChannel(destNum: Int, index: Int) = request(
        destNum,
        { service, packetId, dest -> service.getRemoteChannel(packetId, dest, index) },
        "Request getChannel error",
    )

    fun setConfig(config: ConfigProtos.Config) {
        setRemoteConfig(destNode.value?.num ?: return, config)
    }

    private fun setRemoteConfig(destNum: Int, config: ConfigProtos.Config) = request(
        destNum,
        { service, packetId, dest ->
            _radioConfigState.update { it.copy(radioConfig = config) }
            service.setRemoteConfig(packetId, dest, config.toByteArray())
        },
        "Request setConfig error",
    )

    private fun getConfig(destNum: Int, configType: Int) = request(
        destNum,
        { service, packetId, dest -> service.getRemoteConfig(packetId, dest, configType) },
        "Request getConfig error",
    )

    fun setModuleConfig(config: ModuleConfigProtos.ModuleConfig) {
        setModuleConfig(destNode.value?.num ?: return, config)
    }

    private fun setModuleConfig(destNum: Int, config: ModuleConfigProtos.ModuleConfig) = request(
        destNum,
        { service, packetId, dest ->
            _radioConfigState.update { it.copy(moduleConfig = config) }
            service.setModuleConfig(packetId, dest, config.toByteArray())
        },
        "Request setConfig error",
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
            viewModelScope.launch { nodeRepository.clearNodeDB() }
        }
    }

    private fun requestNodedbReset(destNum: Int) {
        request(
            destNum,
            { service, packetId, dest -> service.requestNodedbReset(packetId, dest) },
            "Request NodeDB reset error",
        )
        if (destNum == myNodeNum) {
            viewModelScope.launch { nodeRepository.clearNodeDB() }
        }
    }

    private fun sendAdminRequest(destNum: Int) {
        val route = radioConfigState.value.route
        _radioConfigState.update { it.copy(route = "") } // setter (response is PortNum.ROUTING_APP)

        when (route) {
            AdminRoute.REBOOT.name -> requestReboot(destNum)
            AdminRoute.SHUTDOWN.name ->
                with(radioConfigState.value) {
                    if (metadata != null && !metadata.canShutdown) {
                        sendError(Res.string.cant_shutdown)
                    } else {
                        requestShutdown(destNum)
                    }
                }

            AdminRoute.FACTORY_RESET.name -> requestFactoryReset(destNum)
            AdminRoute.NODEDB_RESET.name -> requestNodedbReset(destNum)
        }
    }

    fun setFixedPosition(position: Position) {
        val destNum = destNode.value?.num ?: return
        try {
            meshService?.setFixedPosition(destNum, position)
        } catch (ex: RemoteException) {
            Timber.e("Set fixed position error: ${ex.message}")
        }
    }

    fun removeFixedPosition() = setFixedPosition(Position(0.0, 0.0, 0))

    fun importProfile(uri: Uri, onResult: (DeviceProfile) -> Unit) = viewModelScope.launch(Dispatchers.IO) {
        try {
            app.contentResolver.openInputStream(uri).use { inputStream ->
                val bytes = inputStream?.readBytes()
                val protobuf = DeviceProfile.parseFrom(bytes)
                onResult(protobuf)
            }
        } catch (ex: Exception) {
            Timber.e("Import DeviceProfile error: ${ex.message}")
            sendError(ex.customMessage)
        }
    }

    fun exportProfile(uri: Uri, profile: DeviceProfile) = viewModelScope.launch { writeToUri(uri, profile) }

    private suspend fun writeToUri(uri: Uri, message: MessageLite) = withContext(Dispatchers.IO) {
        try {
            app.contentResolver.openFileDescriptor(uri, "wt")?.use { parcelFileDescriptor ->
                FileOutputStream(parcelFileDescriptor.fileDescriptor).use { outputStream ->
                    message.writeTo(outputStream)
                }
            }
            setResponseStateSuccess()
        } catch (ex: Exception) {
            Timber.e("Can't write file error: ${ex.message}")
            sendError(ex.customMessage)
        }
    }

    fun exportSecurityConfig(uri: Uri, securityConfig: SecurityConfig) =
        viewModelScope.launch { writeSecurityKeysJsonToUri(uri, securityConfig) }

    private val indentSpaces = 4

    private suspend fun writeSecurityKeysJsonToUri(uri: Uri, securityConfig: SecurityConfig) =
        withContext(Dispatchers.IO) {
            try {
                val publicKeyBytes = securityConfig.publicKey.toByteArray()
                val privateKeyBytes = securityConfig.privateKey.toByteArray()

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
                Timber.e(errorMessage)
                sendError(ex.customMessage)
            }
        }

    fun installProfile(protobuf: DeviceProfile) = with(protobuf) {
        meshService?.beginEditSettings()
        if (hasLongName() || hasShortName()) {
            destNode.value?.user?.let {
                val user =
                    MeshProtos.User.newBuilder()
                        .setId(it.id)
                        .setLongName(if (hasLongName()) longName else it.longName)
                        .setShortName(if (hasShortName()) shortName else it.shortName)
                        .setIsLicensed(it.isLicensed)
                        .build()
                setOwner(user)
            }
        }
        if (hasChannelUrl()) {
            try {
                setChannels(channelUrl)
            } catch (ex: Exception) {
                Timber.e(ex, "DeviceProfile channel import error")
                sendError(ex.customMessage)
            }
        }
        if (hasConfig()) {
            val descriptor = ConfigProtos.Config.getDescriptor()
            config.allFields.forEach { (field, value) ->
                val newConfig =
                    ConfigProtos.Config.newBuilder().setField(descriptor.findFieldByName(field.name), value).build()
                setConfig(newConfig)
            }
        }
        if (hasFixedPosition()) {
            setFixedPosition(Position(fixedPosition))
        }
        if (hasModuleConfig()) {
            val descriptor = ModuleConfigProtos.ModuleConfig.getDescriptor()
            moduleConfig.allFields.forEach { (field, value) ->
                val newConfig =
                    ModuleConfigProtos.ModuleConfig.newBuilder()
                        .setField(descriptor.findFieldByName(field.name), value)
                        .build()
                setModuleConfig(newConfig)
            }
        }
        meshService?.commitEditSettings()
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
                getConfig(destNum, AdminProtos.AdminMessage.ConfigType.SESSIONKEY_CONFIG_VALUE)
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

    private fun sendError(@StringRes id: Int) = setResponseStateError(UiText.StringResource(id))

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

    private fun processPacketResponse(packet: MeshProtos.MeshPacket) {
        val data = packet.decoded
        if (data.requestId !in requestIds.value) return
        val route = radioConfigState.value.route

        val destNum = destNode.value?.num ?: return
        val debugMsg = "requestId: ${data.requestId.toUInt()} to: ${destNum.toUInt()} received %s"

        if (data?.portnumValue == Portnums.PortNum.ROUTING_APP_VALUE) {
            val parsed = MeshProtos.Routing.parseFrom(data.payload)
            Timber.d(debugMsg.format(parsed.errorReason.name))
            if (parsed.errorReason != MeshProtos.Routing.Error.NONE) {
                sendError(getStringResFrom(parsed.errorReasonValue))
            } else if (packet.from == destNum && route.isEmpty()) {
                requestIds.update { it.apply { remove(data.requestId) } }
                if (requestIds.value.isEmpty()) {
                    setResponseStateSuccess()
                } else {
                    incrementCompleted()
                }
            }
        }
        if (data?.portnumValue == Portnums.PortNum.ADMIN_APP_VALUE) {
            val parsed = AdminProtos.AdminMessage.parseFrom(data.payload)
            Timber.d(debugMsg.format(parsed.payloadVariantCase.name))
            if (destNum != packet.from) {
                sendError("Unexpected sender: ${packet.from.toUInt()} instead of ${destNum.toUInt()}.")
                return
            }
            when (parsed.payloadVariantCase) {
                AdminProtos.AdminMessage.PayloadVariantCase.GET_DEVICE_METADATA_RESPONSE -> {
                    _radioConfigState.update { it.copy(metadata = parsed.getDeviceMetadataResponse) }
                    incrementCompleted()
                }

                AdminProtos.AdminMessage.PayloadVariantCase.GET_CHANNEL_RESPONSE -> {
                    val response = parsed.getChannelResponse
                    // Stop once we get to the first disabled entry
                    if (response.role != ChannelProtos.Channel.Role.DISABLED) {
                        _radioConfigState.update { state ->
                            state.copy(
                                channelList =
                                state.channelList.toMutableList().apply { add(response.index, response.settings) },
                            )
                        }
                        incrementCompleted()
                        if (response.index + 1 < maxChannels && route == ConfigRoute.CHANNELS.name) {
                            // Not done yet, request next channel
                            getChannel(destNum, response.index + 1)
                        }
                    } else {
                        // Received last channel, update total and start channel editor
                        setResponseStateTotal(response.index + 1)
                    }
                }

                AdminProtos.AdminMessage.PayloadVariantCase.GET_OWNER_RESPONSE -> {
                    _radioConfigState.update { it.copy(userConfig = parsed.getOwnerResponse) }
                    incrementCompleted()
                }

                AdminProtos.AdminMessage.PayloadVariantCase.GET_CONFIG_RESPONSE -> {
                    val response = parsed.getConfigResponse
                    if (response.payloadVariantCase.number == 0) { // PAYLOADVARIANT_NOT_SET
                        sendError(response.payloadVariantCase.name)
                    }
                    _radioConfigState.update { it.copy(radioConfig = response) }
                    incrementCompleted()
                }

                AdminProtos.AdminMessage.PayloadVariantCase.GET_MODULE_CONFIG_RESPONSE -> {
                    val response = parsed.getModuleConfigResponse
                    if (response.payloadVariantCase.number == 0) { // PAYLOADVARIANT_NOT_SET
                        sendError(response.payloadVariantCase.name)
                    }
                    _radioConfigState.update { it.copy(moduleConfig = response) }
                    incrementCompleted()
                }

                AdminProtos.AdminMessage.PayloadVariantCase.GET_CANNED_MESSAGE_MODULE_MESSAGES_RESPONSE -> {
                    _radioConfigState.update {
                        it.copy(cannedMessageMessages = parsed.getCannedMessageModuleMessagesResponse)
                    }
                    incrementCompleted()
                }

                AdminProtos.AdminMessage.PayloadVariantCase.GET_RINGTONE_RESPONSE -> {
                    _radioConfigState.update { it.copy(ringtone = parsed.getRingtoneResponse) }
                    incrementCompleted()
                }

                AdminProtos.AdminMessage.PayloadVariantCase.GET_DEVICE_CONNECTION_STATUS_RESPONSE -> {
                    _radioConfigState.update {
                        it.copy(deviceConnectionStatus = parsed.getDeviceConnectionStatusResponse)
                    }
                    incrementCompleted()
                }

                else -> Timber.d("No custom processing needed for ${parsed.payloadVariantCase}")
            }

            if (AdminRoute.entries.any { it.name == route }) {
                sendAdminRequest(destNum)
            }
            requestIds.update { it.apply { remove(data.requestId) } }
        }
    }
}
