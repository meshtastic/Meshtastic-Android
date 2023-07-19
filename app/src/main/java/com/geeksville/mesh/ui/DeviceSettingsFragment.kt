package com.geeksville.mesh.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.geeksville.mesh.AdminProtos
import com.geeksville.mesh.AdminProtos.AdminMessage.ConfigType
import com.geeksville.mesh.AdminProtos.AdminMessage.ModuleConfigType
import com.geeksville.mesh.ChannelProtos
import com.geeksville.mesh.ConfigProtos.Config
import com.geeksville.mesh.ModuleConfigProtos.ModuleConfig
import com.geeksville.mesh.MeshProtos
import com.geeksville.mesh.NodeInfo
import com.geeksville.mesh.Portnums
import com.geeksville.mesh.R
import com.geeksville.mesh.android.BuildUtils.debug
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.config
import com.geeksville.mesh.deviceProfile
import com.geeksville.mesh.model.Channel
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.moduleConfig
import com.geeksville.mesh.service.MeshService
import com.geeksville.mesh.ui.components.PreferenceCategory
import com.geeksville.mesh.ui.components.TextDividerPreference
import com.geeksville.mesh.ui.components.config.AudioConfigItemList
import com.geeksville.mesh.ui.components.config.BluetoothConfigItemList
import com.geeksville.mesh.ui.components.config.CannedMessageConfigItemList
import com.geeksville.mesh.ui.components.config.ChannelSettingsItemList
import com.geeksville.mesh.ui.components.config.DeviceConfigItemList
import com.geeksville.mesh.ui.components.config.DisplayConfigItemList
import com.geeksville.mesh.ui.components.config.EditDeviceProfileDialog
import com.geeksville.mesh.ui.components.config.ExternalNotificationConfigItemList
import com.geeksville.mesh.ui.components.config.LoRaConfigItemList
import com.geeksville.mesh.ui.components.config.MQTTConfigItemList
import com.geeksville.mesh.ui.components.config.NetworkConfigItemList
import com.geeksville.mesh.ui.components.config.PacketResponseStateDialog
import com.geeksville.mesh.ui.components.config.PositionConfigItemList
import com.geeksville.mesh.ui.components.config.PowerConfigItemList
import com.geeksville.mesh.ui.components.config.RangeTestConfigItemList
import com.geeksville.mesh.ui.components.config.RemoteHardwareConfigItemList
import com.geeksville.mesh.ui.components.config.SerialConfigItemList
import com.geeksville.mesh.ui.components.config.StoreForwardConfigItemList
import com.geeksville.mesh.ui.components.config.TelemetryConfigItemList
import com.geeksville.mesh.ui.components.config.UserConfigItemList
import com.google.accompanist.themeadapter.appcompat.AppCompatTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class DeviceSettingsFragment(val node: NodeInfo) : ScreenFragment("Radio Configuration"), Logging {

    private val model: UIViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setBackgroundColor(ContextCompat.getColor(context, R.color.colorAdvancedBackground))
            setContent {
                AppCompatTheme {
                    RadioConfigNavHost(node, model)
                }
            }
        }
    }
}

enum class ConfigDest(val title: String, val route: String, val config: ConfigType) {
    DEVICE("Device", "device", ConfigType.DEVICE_CONFIG),
    POSITION("Position", "position", ConfigType.POSITION_CONFIG),
    POWER("Power", "power", ConfigType.POWER_CONFIG),
    NETWORK("Network", "network", ConfigType.NETWORK_CONFIG),
    DISPLAY("Display", "display", ConfigType.DISPLAY_CONFIG),
    LORA("LoRa", "lora", ConfigType.LORA_CONFIG),
    BLUETOOTH("Bluetooth", "bluetooth", ConfigType.BLUETOOTH_CONFIG);
}

enum class ModuleDest(val title: String, val route: String, val config: ModuleConfigType) {
    MQTT("MQTT", "mqtt", ModuleConfigType.MQTT_CONFIG),
    SERIAL("Serial", "serial", ModuleConfigType.SERIAL_CONFIG),
    EXTERNAL_NOTIFICATION("External Notification", "ext_not", ModuleConfigType.EXTNOTIF_CONFIG),
    STORE_FORWARD("Store & Forward", "store_forward", ModuleConfigType.STOREFORWARD_CONFIG),
    RANGE_TEST("Range Test", "range_test", ModuleConfigType.RANGETEST_CONFIG),
    TELEMETRY("Telemetry", "telemetry", ModuleConfigType.TELEMETRY_CONFIG),
    CANNED_MESSAGE("Canned Message", "canned_message", ModuleConfigType.CANNEDMSG_CONFIG),
    AUDIO("Audio", "audio", ModuleConfigType.AUDIO_CONFIG),
    REMOTE_HARDWARE("Remote Hardware", "remote_hardware", ModuleConfigType.REMOTEHARDWARE_CONFIG);
}

/**
 * This sealed class defines each possible state of a packet response.
 */
sealed class PacketResponseState {
    object Loading : PacketResponseState() {
        var total: Int = 0
        var completed: Int = 0
    }

    data class Success(val packets: List<String>) : PacketResponseState()
    object Empty : PacketResponseState()
    data class Error(val error: String) : PacketResponseState()
}

@Composable
fun RadioConfigNavHost(node: NodeInfo, viewModel: UIViewModel = viewModel()) {
    val navController = rememberNavController()
    val focusManager = LocalFocusManager.current

    val connectionState by viewModel.connectionState.observeAsState()
    val connected = connectionState == MeshService.ConnectionState.CONNECTED

    val destNum = node.num
    val isLocal = destNum == viewModel.myNodeNum
    val maxChannels = viewModel.myNodeInfo.value?.maxChannels ?: 8

    var userConfig by remember { mutableStateOf(MeshProtos.User.getDefaultInstance()) }
    val channelList = remember { mutableStateListOf<ChannelProtos.ChannelSettings>() }
    var radioConfig by remember { mutableStateOf(Config.getDefaultInstance()) }
    var moduleConfig by remember { mutableStateOf(ModuleConfig.getDefaultInstance()) }

    var location by remember(node) { mutableStateOf(node.position) }
    var ringtone by remember { mutableStateOf("") }
    var cannedMessageMessages by remember { mutableStateOf("") }

    val packetResponse by viewModel.packetResponse.collectAsStateWithLifecycle()
    val deviceProfile by viewModel.deviceProfile.collectAsStateWithLifecycle()
    var packetResponseState by remember { mutableStateOf<PacketResponseState>(PacketResponseState.Empty) }
    val isWaiting = packetResponseState !is PacketResponseState.Empty
    var showEditDeviceProfileDialog by remember { mutableStateOf(false) }

    val importConfigLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == Activity.RESULT_OK) {
            showEditDeviceProfileDialog = true
            it.data?.data?.let { file_uri -> viewModel.importProfile(file_uri) }
        }
    }

    val exportConfigLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == Activity.RESULT_OK) {
            it.data?.data?.let { file_uri -> viewModel.exportProfile(file_uri) }
        }
    }

    if (showEditDeviceProfileDialog) EditDeviceProfileDialog(
        title = if (deviceProfile != null) "Import configuration" else "Export configuration",
        deviceProfile = deviceProfile ?: with(viewModel) {
            deviceProfile {
                ourNodeInfo.value?.user?.let {
                    longName = it.longName
                    shortName = it.shortName
                }
                channelUrl = channels.value.getChannelUrl().toString()
                config = localConfig.value
                this.moduleConfig = module
            }
        },
        onAddClick = {
            showEditDeviceProfileDialog = false
            if (deviceProfile != null) {
                viewModel.installProfile(it)
            } else {
                viewModel.setDeviceProfile(it)
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/*"
                    putExtra(Intent.EXTRA_TITLE, "${destNum.toUInt()}.cfg")
                }
                exportConfigLauncher.launch(intent)
            }
        },
        onDismissRequest = {
            showEditDeviceProfileDialog = false
            viewModel.setDeviceProfile(null)
        }
    )

    if (isWaiting) PacketResponseStateDialog(
        packetResponseState,
        onDismiss = {
            packetResponseState = PacketResponseState.Empty
            viewModel.clearPacketResponse()
        }
    )

    if (isWaiting) LaunchedEffect(packetResponse) {
        val data = packetResponse?.meshPacket?.decoded
        val from = packetResponse?.meshPacket?.from?.toUInt()
        if (data?.portnumValue == Portnums.PortNum.ROUTING_APP_VALUE) {
            val parsed = MeshProtos.Routing.parseFrom(data.payload)
            debug("packet for destNum ${destNum.toUInt()} received ${parsed.errorReason} from $from")
            if (parsed.errorReason != MeshProtos.Routing.Error.NONE) {
                packetResponseState = PacketResponseState.Error(parsed.errorReason.toString())
            } else if (packetResponse?.meshPacket?.from == destNum) {
                packetResponseState = PacketResponseState.Success(emptyList())
            }
        }
        if (data?.portnumValue == Portnums.PortNum.ADMIN_APP_VALUE) {
            viewModel.clearPacketResponse()
            val parsed = AdminProtos.AdminMessage.parseFrom(data.payload)
            debug("packet for destNum ${destNum.toUInt()} received ${parsed.payloadVariantCase} from $from")
            // check destination: lora config or channel editor
            val goChannels = (packetResponseState as PacketResponseState.Loading).total > 2
            when (parsed.payloadVariantCase) {
                AdminProtos.AdminMessage.PayloadVariantCase.GET_CHANNEL_RESPONSE -> {
                    val response = parsed.getChannelResponse
                    (packetResponseState as PacketResponseState.Loading).completed++
                    // Stop once we get to the first disabled entry
                    if (response.role != ChannelProtos.Channel.Role.DISABLED) {
                        channelList.add(response.index, response.settings)
                        if (response.index + 1 < maxChannels && goChannels) {
                            // Not done yet, request next channel
                            viewModel.getChannel(destNum, response.index + 1)
                        } else {
                            // Received max channels, get lora config (for default channel names)
                            viewModel.getConfig(destNum, ConfigType.LORA_CONFIG_VALUE)
                        }
                    } else {
                        // Received last channel, get lora config (for default channel names)
                        viewModel.getConfig(destNum, ConfigType.LORA_CONFIG_VALUE)
                    }
                }

                AdminProtos.AdminMessage.PayloadVariantCase.GET_OWNER_RESPONSE -> {
                    packetResponseState = PacketResponseState.Empty
                    userConfig = parsed.getOwnerResponse
                    navController.navigate("user")
                }

                AdminProtos.AdminMessage.PayloadVariantCase.GET_CONFIG_RESPONSE -> {
                    packetResponseState = PacketResponseState.Empty
                    val response = parsed.getConfigResponse
                    radioConfig = response
                    if (goChannels) navController.navigate("channels")
                    else enumValues<ConfigDest>().find { it.name == "${response.payloadVariantCase}" }
                        ?.let { navController.navigate(it.route) }
                }

                AdminProtos.AdminMessage.PayloadVariantCase.GET_MODULE_CONFIG_RESPONSE -> {
                    packetResponseState = PacketResponseState.Empty
                    val response = parsed.getModuleConfigResponse
                    moduleConfig = response
                    enumValues<ModuleDest>().find { it.name == "${response.payloadVariantCase}" }
                        ?.let { navController.navigate(it.route) }
                }

                AdminProtos.AdminMessage.PayloadVariantCase.GET_CANNED_MESSAGE_MODULE_MESSAGES_RESPONSE -> {
                    cannedMessageMessages = parsed.getCannedMessageModuleMessagesResponse
                    (packetResponseState as PacketResponseState.Loading).completed++
                    viewModel.getModuleConfig(destNum, ModuleConfigType.CANNEDMSG_CONFIG_VALUE)
                }

                AdminProtos.AdminMessage.PayloadVariantCase.GET_RINGTONE_RESPONSE -> {
                    ringtone = parsed.getRingtoneResponse
                    (packetResponseState as PacketResponseState.Loading).completed++
                    viewModel.getModuleConfig(destNum, ModuleConfigType.EXTNOTIF_CONFIG_VALUE)
                }

                else -> TODO()
            }
        }
    }

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            RadioSettingsScreen(
                enabled = connected && !isWaiting,
                isLocal = isLocal,
                headerText = node.user?.longName ?: stringResource(R.string.unknown_username),
                onRouteClick = { configType ->
                    packetResponseState = PacketResponseState.Loading.apply {
                        total = 1
                        completed = 0
                    }
                    // clearAllConfigs() ?
                    when (configType) {
                        "USER" -> { viewModel.getOwner(destNum) }
                        "CHANNELS" -> {
                            val maxPackets = maxChannels + 1 // for lora config
                            (packetResponseState as PacketResponseState.Loading).total = maxPackets
                            channelList.clear()
                            viewModel.getChannel(destNum, 0)
                        }
                        "IMPORT" -> {
                            packetResponseState = PacketResponseState.Empty
                            viewModel.setDeviceProfile(null)
                            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = "application/*"
                            }
                            importConfigLauncher.launch(intent)
                        }
                        "EXPORT" -> {
                            packetResponseState = PacketResponseState.Empty
                            showEditDeviceProfileDialog = true
                        }

                        "REBOOT" -> {
                            viewModel.requestReboot(destNum)
                        }

                        "SHUTDOWN" -> {
                            viewModel.requestShutdown(destNum)
                        }

                        "FACTORY_RESET" -> {
                            viewModel.requestFactoryReset(destNum)
                        }

                        "NODEDB_RESET" -> {
                            viewModel.requestNodedbReset(destNum)
                        }

                        ConfigType.LORA_CONFIG -> {
                            (packetResponseState as PacketResponseState.Loading).total = 2
                            channelList.clear()
                            viewModel.getChannel(destNum, 0)
                        }
                        is ConfigType -> {
                            viewModel.getConfig(destNum, configType.number)
                        }
                        ModuleConfigType.CANNEDMSG_CONFIG -> {
                            (packetResponseState as PacketResponseState.Loading).total = 2
                            viewModel.getCannedMessages(destNum)
                        }
                        ModuleConfigType.EXTNOTIF_CONFIG -> {
                            (packetResponseState as PacketResponseState.Loading).total = 2
                            viewModel.getRingtone(destNum)
                        }
                        is ModuleConfigType -> {
                            viewModel.getModuleConfig(destNum, configType.number)
                        }
                    }
                },
            )
        }
        composable("channels") {
            ChannelSettingsItemList(
                settingsList = channelList,
                modemPresetName = Channel(Channel.default.settings, radioConfig.lora).name,
                enabled = connected,
                maxChannels = maxChannels,
                focusManager = focusManager,
                onPositiveClicked = { channelListInput ->
                    focusManager.clearFocus()
                    viewModel.updateChannels(destNum, channelList, channelListInput)
                    channelList.clear()
                    channelList.addAll(channelListInput)
                },
            )
        }
        composable("user") {
            UserConfigItemList(
                userConfig = userConfig,
                enabled = connected,
                focusManager = focusManager,
                onSaveClicked = { userInput ->
                    focusManager.clearFocus()
                    viewModel.setRemoteOwner(destNum, userInput)
                    userConfig = userInput
                }
            )
        }
        composable("device") {
            DeviceConfigItemList(
                deviceConfig = radioConfig.device,
                enabled = connected,
                focusManager = focusManager,
                onSaveClicked = { deviceInput ->
                    focusManager.clearFocus()
                    val config = config { device = deviceInput }
                    viewModel.setRemoteConfig(destNum, config)
                    radioConfig = config
                }
            )
        }
        composable("position") {
            PositionConfigItemList(
                isLocal = isLocal,
                location = location,
                positionConfig = radioConfig.position,
                enabled = connected,
                focusManager = focusManager,
                onSaveClicked = { locationInput, positionInput ->
                    focusManager.clearFocus()
                    if (locationInput != node.position && positionInput.fixedPosition) {
                        locationInput?.let { viewModel.requestPosition(destNum, it) }
                        location = locationInput
                    }
                    if (positionInput != radioConfig.position) {
                        val config = config { position = positionInput }
                        viewModel.setRemoteConfig(destNum, config)
                        radioConfig = config
                    }
                }
            )
        }
        composable("power") {
            PowerConfigItemList(
                powerConfig = radioConfig.power,
                enabled = connected,
                focusManager = focusManager,
                onSaveClicked = { powerInput ->
                    focusManager.clearFocus()
                    val config = config { power = powerInput }
                    viewModel.setRemoteConfig(destNum, config)
                    radioConfig = config
                }
            )
        }
        composable("network") {
            NetworkConfigItemList(
                networkConfig = radioConfig.network,
                enabled = connected,
                focusManager = focusManager,
                onSaveClicked = { networkInput ->
                    focusManager.clearFocus()
                    val config = config { network = networkInput }
                    viewModel.setRemoteConfig(destNum, config)
                    radioConfig = config
                }
            )
        }
        composable("display") {
            DisplayConfigItemList(
                displayConfig = radioConfig.display,
                enabled = connected,
                focusManager = focusManager,
                onSaveClicked = { displayInput ->
                    focusManager.clearFocus()
                    val config = config { display = displayInput }
                    viewModel.setRemoteConfig(destNum, config)
                    radioConfig = config
                }
            )
        }
        composable("lora") {
            LoRaConfigItemList(
                loraConfig = radioConfig.lora,
                primarySettings = channelList.getOrNull(0) ?: return@composable,
                enabled = connected,
                focusManager = focusManager,
                onSaveClicked = { loraInput ->
                    focusManager.clearFocus()
                    val config = config { lora = loraInput }
                    viewModel.setRemoteConfig(destNum, config)
                    radioConfig = config
                }
            )
        }
        composable("bluetooth") {
            BluetoothConfigItemList(
                bluetoothConfig = radioConfig.bluetooth,
                enabled = connected,
                focusManager = focusManager,
                onSaveClicked = { bluetoothInput ->
                    focusManager.clearFocus()
                    val config = config { bluetooth = bluetoothInput }
                    viewModel.setRemoteConfig(destNum, config)
                    radioConfig = config
                }
            )
        }
        composable("mqtt") {
            MQTTConfigItemList(
                mqttConfig = moduleConfig.mqtt,
                enabled = connected,
                focusManager = focusManager,
                onSaveClicked = { mqttInput ->
                    focusManager.clearFocus()
                    val config = moduleConfig { mqtt = mqttInput }
                    viewModel.setModuleConfig(destNum, config)
                    moduleConfig = config
                }
            )
        }
        composable("serial") {
            SerialConfigItemList(
                serialConfig = moduleConfig.serial,
                enabled = connected,
                focusManager = focusManager,
                onSaveClicked = { serialInput ->
                    focusManager.clearFocus()
                    val config = moduleConfig { serial = serialInput }
                    viewModel.setModuleConfig(destNum, config)
                    moduleConfig = config
                }
            )
        }
        composable("ext_not") {
            ExternalNotificationConfigItemList(
                ringtone = ringtone,
                extNotificationConfig = moduleConfig.externalNotification,
                enabled = connected,
                focusManager = focusManager,
                onSaveClicked = { ringtoneInput, extNotificationInput ->
                    focusManager.clearFocus()
                    if (ringtoneInput != ringtone) {
                        viewModel.setRingtone(destNum, ringtoneInput)
                        ringtone = ringtoneInput
                    }
                    if (extNotificationInput != moduleConfig.externalNotification) {
                        val config = moduleConfig { externalNotification = extNotificationInput }
                        viewModel.setModuleConfig(destNum, config)
                        moduleConfig = config
                    }
                }
            )
        }
        composable("store_forward") {
            StoreForwardConfigItemList(
                storeForwardConfig = moduleConfig.storeForward,
                enabled = connected,
                focusManager = focusManager,
                onSaveClicked = { storeForwardInput ->
                    focusManager.clearFocus()
                    val config = moduleConfig { storeForward = storeForwardInput }
                    viewModel.setModuleConfig(destNum, config)
                    moduleConfig = config
                }
            )
        }
        composable("range_test") {
            RangeTestConfigItemList(
                rangeTestConfig = moduleConfig.rangeTest,
                enabled = connected,
                focusManager = focusManager,
                onSaveClicked = { rangeTestInput ->
                    focusManager.clearFocus()
                    val config = moduleConfig { rangeTest = rangeTestInput }
                    viewModel.setModuleConfig(destNum, config)
                    moduleConfig = config
                }
            )
        }
        composable("telemetry") {
            TelemetryConfigItemList(
                telemetryConfig = moduleConfig.telemetry,
                enabled = connected,
                focusManager = focusManager,
                onSaveClicked = { telemetryInput ->
                    focusManager.clearFocus()
                    val config = moduleConfig { telemetry = telemetryInput }
                    viewModel.setModuleConfig(destNum, config)
                    moduleConfig = config
                }
            )
        }
        composable("canned_message") {
            CannedMessageConfigItemList(
                messages = cannedMessageMessages,
                cannedMessageConfig = moduleConfig.cannedMessage,
                enabled = connected,
                focusManager = focusManager,
                onSaveClicked = { messagesInput, cannedMessageInput ->
                    focusManager.clearFocus()
                    if (messagesInput != cannedMessageMessages) {
                        viewModel.setCannedMessages(destNum, messagesInput)
                        cannedMessageMessages = messagesInput
                    }
                    if (cannedMessageInput != moduleConfig.cannedMessage) {
                        val config = moduleConfig { cannedMessage = cannedMessageInput }
                        viewModel.setModuleConfig(destNum, config)
                        moduleConfig = config
                    }
                }
            )
        }
        composable("audio") {
            AudioConfigItemList(
                audioConfig = moduleConfig.audio,
                enabled = connected,
                focusManager = focusManager,
                onSaveClicked = { audioInput ->
                    focusManager.clearFocus()
                    val config = moduleConfig { audio = audioInput }
                    viewModel.setModuleConfig(destNum, config)
                    moduleConfig = config
                }
            )
        }
        composable("remote_hardware") {
            RemoteHardwareConfigItemList(
                remoteHardwareConfig = moduleConfig.remoteHardware,
                enabled = connected,
                focusManager = focusManager,
                onSaveClicked = { remoteHardwareInput ->
                    focusManager.clearFocus()
                    val config = moduleConfig { remoteHardware = remoteHardwareInput }
                    viewModel.setModuleConfig(destNum, config)
                    moduleConfig = config
                }
            )
        }
    }
}

@Composable
fun NavCard(
    title: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val color = if (enabled) MaterialTheme.colors.onSurface
    else MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.disabled)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable(enabled = enabled) { onClick() },
        elevation = 4.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.body1,
                color = color,
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.TwoTone.KeyboardArrowRight, "trailingIcon",
                modifier = Modifier.wrapContentSize(),
                tint = color,
            )
        }
    }
}

@Composable
fun NavCard(@StringRes title: Int, enabled: Boolean, onClick: () -> Unit) {
    NavCard(title = stringResource(title), enabled = enabled, onClick = onClick)
}

@Composable
fun NavButton(@StringRes title: Int, enabled: Boolean, onClick: () -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    if (showDialog) AlertDialog(
        onDismissRequest = { },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_twotone_warning_24),
                    "warning",
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = "${stringResource(title)}?\n")
                Icon(
                    painterResource(R.drawable.ic_twotone_warning_24),
                    "warning",
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        },
        buttons = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = { showDialog = false }
                ) { Text(stringResource(R.string.cancel)) }
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        showDialog = false
                        onClick()
                    },
                ) { Text(stringResource(R.string.send)) }
            }
        }
    )

    Column {
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedButton(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            enabled = enabled,
            onClick = { showDialog = true },
            colors = ButtonDefaults.buttonColors(
                disabledContentColor = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.disabled)
            )
        ) { Text(text = stringResource(title)) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RadioSettingsScreen(
    enabled: Boolean = true,
    isLocal: Boolean = true,
    headerText: String = "longName",
    onRouteClick: (Any) -> Unit = {},
) {
    LazyColumn(
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        stickyHeader { TextDividerPreference(headerText) }

        item { PreferenceCategory(stringResource(R.string.device_settings)) }
        item { NavCard("User", enabled = enabled) { onRouteClick("USER") } }
        item { NavCard("Channels", enabled = enabled) { onRouteClick("CHANNELS") } }
        items(ConfigDest.values()) { configs ->
            NavCard(configs.title, enabled = enabled) { onRouteClick(configs.config) }
        }

        item { PreferenceCategory(stringResource(R.string.module_settings)) }
        items(ModuleDest.values()) { modules ->
            NavCard(modules.title, enabled = enabled) { onRouteClick(modules.config) }
        }

        if (isLocal) {
            item { PreferenceCategory("Import / Export") }
            item { NavCard("Import configuration", enabled = enabled) { onRouteClick("IMPORT") } }
            item { NavCard("Export configuration", enabled = enabled) { onRouteClick("EXPORT") } }
        }

        item { NavButton(R.string.reboot, enabled) { onRouteClick("REBOOT") } }
        item { NavButton(R.string.shutdown, enabled) { onRouteClick("SHUTDOWN") } }
        item { NavButton(R.string.factory_reset, enabled) { onRouteClick("FACTORY_RESET") } }
        item { NavButton(R.string.nodedb_reset, enabled) { onRouteClick("NODEDB_RESET") } }
    }
}

@Preview(showBackground = true)
@Composable
fun RadioSettingsScreenPreview() {
    RadioSettingsScreen()
}
