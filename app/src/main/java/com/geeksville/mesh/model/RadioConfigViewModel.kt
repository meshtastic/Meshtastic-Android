package com.geeksville.mesh.model

import android.app.Application
import android.net.Uri
import android.os.RemoteException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geeksville.mesh.AdminProtos
import com.geeksville.mesh.ChannelProtos
import com.geeksville.mesh.ClientOnlyProtos.DeviceProfile
import com.geeksville.mesh.ConfigProtos
import com.geeksville.mesh.IMeshService
import com.geeksville.mesh.MeshProtos
import com.geeksville.mesh.ModuleConfigProtos
import com.geeksville.mesh.MyNodeInfo
import com.geeksville.mesh.NodeInfo
import com.geeksville.mesh.Portnums
import com.geeksville.mesh.Position
import com.geeksville.mesh.R
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.config
import com.geeksville.mesh.database.entity.toNodeInfo
import com.geeksville.mesh.deviceProfile
import com.geeksville.mesh.moduleConfig
import com.geeksville.mesh.repository.datastore.RadioConfigRepository
import com.geeksville.mesh.ui.AdminRoute
import com.geeksville.mesh.ui.ConfigRoute
import com.geeksville.mesh.ui.ResponseState
import com.google.protobuf.MessageLite
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import javax.inject.Inject

/**
 * Data class that represents the current RadioConfig state.
 */
data class RadioConfigState(
    val route: String = "",
    val metadata: MeshProtos.DeviceMetadata = MeshProtos.DeviceMetadata.getDefaultInstance(),
    val userConfig: MeshProtos.User = MeshProtos.User.getDefaultInstance(),
    val channelList: List<ChannelProtos.ChannelSettings> = emptyList(),
    val radioConfig: ConfigProtos.Config = config {},
    val moduleConfig: ModuleConfigProtos.ModuleConfig = moduleConfig {},
    val ringtone: String = "",
    val cannedMessageMessages: String = "",
    val responseState: ResponseState<Boolean> = ResponseState.Empty,
) {
    fun hasMetadata() = metadata != MeshProtos.DeviceMetadata.getDefaultInstance()
}

@HiltViewModel
class RadioConfigViewModel @Inject constructor(
    private val app: Application,
    private val radioConfigRepository: RadioConfigRepository,
) : ViewModel(), Logging {

    private val meshService: IMeshService? get() = radioConfigRepository.meshService

    // Connection state to our radio device
    val connectionState get() = radioConfigRepository.connectionState

    private val _destNum = MutableStateFlow<Int?>(null)
    private val _destNode = MutableStateFlow<NodeInfo?>(null)
    val destNode: StateFlow<NodeInfo?> get() = _destNode

    /**
     * Sets the destination [NodeInfo] used in Radio Configuration.
     * @param num Destination nodeNum (or null for our local [NodeInfo]).
     */
    fun setDestNum(num: Int?) {
        _destNum.value = num
    }

    private val requestIds = MutableStateFlow(hashSetOf<Int>())
    private val _radioConfigState = MutableStateFlow(RadioConfigState())
    val radioConfigState: StateFlow<RadioConfigState> = _radioConfigState

    private val _currentDeviceProfile = MutableStateFlow(deviceProfile {})
    val currentDeviceProfile get() = _currentDeviceProfile.value

    init {
        combine(_destNum, radioConfigRepository.nodeDBbyNum) { destNum, nodes ->
            nodes[destNum] ?: nodes.values.firstOrNull()
        }.onEach { _destNode.value = it?.toNodeInfo() }.launchIn(viewModelScope)

        radioConfigRepository.deviceProfileFlow.onEach {
            _currentDeviceProfile.value = it
        }.launchIn(viewModelScope)

        radioConfigRepository.meshPacketFlow.onEach(::processPacketResponse)
            .launchIn(viewModelScope)

        debug("RadioConfigViewModel created")
    }

    private val myNodeInfo: StateFlow<MyNodeInfo?> get() = radioConfigRepository.myNodeInfo
    val myNodeNum get() = myNodeInfo.value?.myNodeNum
    val maxChannels get() = myNodeInfo.value?.maxChannels ?: 8

    val hasPaFan: Boolean
        get() = destNode.value?.user?.hwModel in setOf(
            null,
            MeshProtos.HardwareModel.UNSET,
            MeshProtos.HardwareModel.BETAFPV_2400_TX,
            MeshProtos.HardwareModel.RADIOMASTER_900_BANDIT_NANO,
            MeshProtos.HardwareModel.RADIOMASTER_900_BANDIT,
        )

    override fun onCleared() {
        super.onCleared()
        debug("RadioConfigViewModel cleared")
    }

    private fun request(
        destNum: Int,
        requestAction: suspend (IMeshService, Int, Int) -> Unit,
        errorMessage: String,
    ) = viewModelScope.launch {
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
                errormsg("$errorMessage: ${ex.message}")
            }
        }
    }

    fun setOwner(user: MeshProtos.User) {
        setRemoteOwner(myNodeNum ?: return, user)
    }

    fun setRemoteOwner(destNum: Int, user: MeshProtos.User) = request(
        destNum,
        { service, packetId, _ ->
            _radioConfigState.update { it.copy(userConfig = user) }
            service.setRemoteOwner(packetId, user.toByteArray())
        },
        "Request setOwner error",
    )

    fun getOwner(destNum: Int) = request(
        destNum,
        { service, packetId, dest -> service.getRemoteOwner(packetId, dest) },
        "Request getOwner error"
    )

    fun updateChannels(
        destNum: Int,
        new: List<ChannelProtos.ChannelSettings>,
        old: List<ChannelProtos.ChannelSettings>,
    ) {
        getChannelList(new, old).forEach { setRemoteChannel(destNum, it) }

        if (destNum == myNodeNum) viewModelScope.launch {
            radioConfigRepository.replaceAllSettings(new)
        }
        _radioConfigState.update { it.copy(channelList = new) }
    }

    private fun setChannels(channelUrl: String) = viewModelScope.launch {
        val new = Uri.parse(channelUrl).toChannelSet()
        val old = radioConfigRepository.channelSetFlow.firstOrNull() ?: return@launch
        updateChannels(myNodeNum ?: return@launch, new.settingsList, old.settingsList)
    }

    private fun setRemoteChannel(destNum: Int, channel: ChannelProtos.Channel) = request(
        destNum,
        { service, packetId, dest ->
            service.setRemoteChannel(packetId, dest, channel.toByteArray())
        },
        "Request setRemoteChannel error"
    )

    fun getChannel(destNum: Int, index: Int) = request(
        destNum,
        { service, packetId, dest -> service.getRemoteChannel(packetId, dest, index) },
        "Request getChannel error"
    )

    fun setRemoteConfig(destNum: Int, config: ConfigProtos.Config) = request(
        destNum,
        { service, packetId, dest ->
            _radioConfigState.update { it.copy(radioConfig = config) }
            service.setRemoteConfig(packetId, dest, config.toByteArray())
        },
        "Request setConfig error",
    )

    fun getConfig(destNum: Int, configType: Int) = request(
        destNum,
        { service, packetId, dest -> service.getRemoteConfig(packetId, dest, configType) },
        "Request getConfig error",
    )

    fun setModuleConfig(destNum: Int, config: ModuleConfigProtos.ModuleConfig) = request(
        destNum,
        { service, packetId, dest ->
            _radioConfigState.update { it.copy(moduleConfig = config) }
            service.setModuleConfig(packetId, dest, config.toByteArray())
        },
        "Request setConfig error",
    )

    fun getModuleConfig(destNum: Int, configType: Int) = request(
        destNum,
        { service, packetId, dest -> service.getModuleConfig(packetId, dest, configType) },
        "Request getModuleConfig error",
    )

    fun setRingtone(destNum: Int, ringtone: String) {
        _radioConfigState.update { it.copy(ringtone = ringtone) }
        meshService?.setRingtone(destNum, ringtone)
    }

    fun getRingtone(destNum: Int) = request(
        destNum,
        { service, packetId, dest -> service.getRingtone(packetId, dest) },
        "Request getRingtone error"
    )

    fun setCannedMessages(destNum: Int, messages: String) {
        _radioConfigState.update { it.copy(cannedMessageMessages = messages) }
        meshService?.setCannedMessages(destNum, messages)
    }

    fun getCannedMessages(destNum: Int) = request(
        destNum,
        { service, packetId, dest -> service.getCannedMessages(packetId, dest) },
        "Request getCannedMessages error"
    )

    private fun requestShutdown(destNum: Int) = request(
        destNum,
        { service, packetId, dest -> service.requestShutdown(packetId, dest) },
        "Request shutdown error"
    )

    private fun requestReboot(destNum: Int) = request(
        destNum,
        { service, packetId, dest -> service.requestReboot(packetId, dest) },
        "Request reboot error"
    )

    private fun requestFactoryReset(destNum: Int) = request(
        destNum,
        { service, packetId, dest -> service.requestFactoryReset(packetId, dest) },
        "Request factory reset error"
    )

    private fun requestNodedbReset(destNum: Int) = request(
        destNum,
        { service, packetId, dest -> service.requestNodedbReset(packetId, dest) },
        "Request NodeDB reset error"
    )

    private fun sendAdminRequest(destNum: Int) {
        when (radioConfigState.value.route) {
            AdminRoute.REBOOT.name -> requestReboot(destNum)
            AdminRoute.SHUTDOWN.name -> with(radioConfigState.value) {
                if (hasMetadata() && !metadata.canShutdown) {
                    setResponseStateError(app.getString(R.string.cant_shutdown))
                } else {
                    requestShutdown(destNum)
                }
            }

            AdminRoute.FACTORY_RESET.name -> requestFactoryReset(destNum)
            AdminRoute.NODEDB_RESET.name -> requestNodedbReset(destNum)
        }
    }

    fun getSessionPasskey(destNum: Int) {
        if (radioConfigState.value.hasMetadata()) {
            sendAdminRequest(destNum)
        } else {
            getConfig(destNum, AdminProtos.AdminMessage.ConfigType.SESSIONKEY_CONFIG_VALUE)
            setResponseStateTotal(2)
        }
    }

    fun setFixedPosition(destNum: Int, position: Position) {
        try {
            meshService?.setFixedPosition(destNum, position)
        } catch (ex: RemoteException) {
            errormsg("Set fixed position error: ${ex.message}")
        }
    }

    fun removeFixedPosition(destNum: Int) = setFixedPosition(destNum, Position(0.0, 0.0, 0))

    // Set the radio config (also updates our saved copy in preferences)
    fun setConfig(config: ConfigProtos.Config) {
        setRemoteConfig(myNodeNum ?: return, config)
    }

    fun setModuleConfig(config: ModuleConfigProtos.ModuleConfig) {
        setModuleConfig(myNodeNum ?: return, config)
    }

    private val _deviceProfile = MutableStateFlow<DeviceProfile?>(null)
    val deviceProfile: StateFlow<DeviceProfile?> get() = _deviceProfile

    fun setDeviceProfile(deviceProfile: DeviceProfile?) {
        _deviceProfile.value = deviceProfile
    }

    fun importProfile(uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        try {
            app.contentResolver.openInputStream(uri).use { inputStream ->
                val bytes = inputStream?.readBytes()
                val protobuf = DeviceProfile.parseFrom(bytes)
                _deviceProfile.value = protobuf
            }
        } catch (ex: Exception) {
            errormsg("Import DeviceProfile error: ${ex.message}")
            setResponseStateError(ex.customMessage)
        }
    }

    fun exportProfile(uri: Uri) = viewModelScope.launch {
        val profile = deviceProfile.value ?: return@launch
        writeToUri(uri, profile)
        _deviceProfile.value = null
    }

    private suspend fun writeToUri(uri: Uri, message: MessageLite) = withContext(Dispatchers.IO) {
        try {
            app.contentResolver.openFileDescriptor(uri, "wt")?.use { parcelFileDescriptor ->
                FileOutputStream(parcelFileDescriptor.fileDescriptor).use { outputStream ->
                    message.writeTo(outputStream)
                }
            }
            setResponseStateSuccess()
        } catch (ex: Exception) {
            errormsg("Can't write file error: ${ex.message}")
            setResponseStateError(ex.customMessage)
        }
    }

    fun installProfile(protobuf: DeviceProfile) = with(protobuf) {
        _deviceProfile.value = null
        meshService?.beginEditSettings()
        if (hasLongName() || hasShortName()) destNode.value?.user?.let {
            val user = it.copy(
                longName = if (hasLongName()) longName else it.longName,
                shortName = if (hasShortName()) shortName else it.shortName,
                hwModel = MeshProtos.HardwareModel.UNSET,
            )
            if (it != user) setOwner(user.toProto())
        }
        if (hasChannelUrl()) try {
            setChannels(channelUrl)
        } catch (ex: Exception) {
            errormsg("DeviceProfile channel import error", ex)
            setResponseStateError(ex.customMessage)
        }
        if (hasConfig()) {
            val descriptor = ConfigProtos.Config.getDescriptor()
            config.allFields.forEach { (field, value) ->
                val newConfig = ConfigProtos.Config.newBuilder()
                    .setField(descriptor.findFieldByName(field.name), value)
                    .build()
                setConfig(newConfig)
            }
        }
        if (hasModuleConfig()) {
            val descriptor = ModuleConfigProtos.ModuleConfig.getDescriptor()
            moduleConfig.allFields.forEach { (field, value) ->
                val newConfig = ModuleConfigProtos.ModuleConfig.newBuilder()
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

    fun setResponseStateLoading(route: String) {
        _radioConfigState.value = RadioConfigState(
            route = route,
            responseState = ResponseState.Loading(),
        )
        // channel editor is synchronous, so we don't use requestIds as total
        if (route == ConfigRoute.CHANNELS.name) setResponseStateTotal(maxChannels + 1)
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

    private val Exception.customMessage: String get() = "${javaClass.simpleName}: $message"
    private fun setResponseStateError(error: String) {
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
            debug(debugMsg.format(parsed.errorReason.name))
            if (parsed.errorReason != MeshProtos.Routing.Error.NONE) {
                setResponseStateError(parsed.errorReason.name)
            } else if (packet.from == destNum && route.isEmpty()) {
                requestIds.update { it.apply { remove(data.requestId) } }
                if (requestIds.value.isEmpty()) setResponseStateSuccess()
                else incrementCompleted()
            }
        }
        if (data?.portnumValue == Portnums.PortNum.ADMIN_APP_VALUE) {
            val parsed = AdminProtos.AdminMessage.parseFrom(data.payload)
            debug(debugMsg.format(parsed.payloadVariantCase.name))
            if (destNum != packet.from) {
                setResponseStateError("Unexpected sender: ${packet.from.toUInt()} instead of ${destNum.toUInt()}.")
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
                            state.copy(channelList = state.channelList.toMutableList().apply {
                                add(response.index, response.settings)
                            })
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
                        setResponseStateError(response.payloadVariantCase.name)
                    }
                    _radioConfigState.update { it.copy(radioConfig = response) }
                    incrementCompleted()
                }

                AdminProtos.AdminMessage.PayloadVariantCase.GET_MODULE_CONFIG_RESPONSE -> {
                    val response = parsed.getModuleConfigResponse
                    if (response.payloadVariantCase.number == 0) { // PAYLOADVARIANT_NOT_SET
                        setResponseStateError(response.payloadVariantCase.name)
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

                else -> TODO()
            }

            if (AdminRoute.entries.any { it.name == route }) {
                sendAdminRequest(destNum)
            }
            requestIds.update { it.apply { remove(data.requestId) } }
        }
    }
}
