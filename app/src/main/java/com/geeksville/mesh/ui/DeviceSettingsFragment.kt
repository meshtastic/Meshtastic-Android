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
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
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
import com.geeksville.mesh.ui.components.config.AmbientLightingConfigItemList
import com.geeksville.mesh.ui.components.config.AudioConfigItemList
import com.geeksville.mesh.ui.components.config.BluetoothConfigItemList
import com.geeksville.mesh.ui.components.config.CannedMessageConfigItemList
import com.geeksville.mesh.ui.components.config.ChannelSettingsItemList
import com.geeksville.mesh.ui.components.config.DetectionSensorConfigItemList
import com.geeksville.mesh.ui.components.config.DeviceConfigItemList
import com.geeksville.mesh.ui.components.config.DisplayConfigItemList
import com.geeksville.mesh.ui.components.config.EditDeviceProfileDialog
import com.geeksville.mesh.ui.components.config.ExternalNotificationConfigItemList
import com.geeksville.mesh.ui.components.config.LoRaConfigItemList
import com.geeksville.mesh.ui.components.config.MQTTConfigItemList
import com.geeksville.mesh.ui.components.config.NeighborInfoConfigItemList
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
class DeviceSettingsFragment : ScreenFragment("Radio Configuration"), Logging {

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
                val node by model.destNode.collectAsStateWithLifecycle()

                AppCompatTheme {
                    val navController: NavHostController = rememberNavController()
                    // Get current back stack entry
                    // val backStackEntry by navController.currentBackStackEntryAsState()
                    // Get the name of the current screen
                    // val currentScreen = backStackEntry?.destination?.route?.let { route ->
                    //     enumValues<ConfigDest>().find { it.name == route }?.title ?: "home"
                    // }

                    Scaffold(
                        topBar = {
                            MeshAppBar(
                                currentScreen = node?.user?.longName
                                    ?: stringResource(R.string.unknown_username),
                                // canNavigateBack = navController.previousBackStackEntry != null,
                                // navigateUp = { navController.navigateUp() },
                                canNavigateBack = true,
                                navigateUp = {
                                    if (navController.previousBackStackEntry != null) {
                                        navController.navigateUp()
                                    } else {
                                        parentFragmentManager.popBackStack()
                                    }
                                },
                            )
                        }
                    ) { innerPadding ->
                        RadioConfigNavHost(
                            node!!,
                            model,
                            navController,
                            modifier = Modifier.padding(innerPadding),
                        )
                    }
                }
            }
        }
    }
}

enum class ConfigDest(val title: String) {
    DEVICE("Device"),
    POSITION("Position"),
    POWER("Power"),
    NETWORK("Network"),
    DISPLAY("Display"),
    LORA("LoRa"),
    BLUETOOTH("Bluetooth"),
    ;
}

enum class ModuleDest(val title: String) {
    MQTT("MQTT"),
    SERIAL("Serial"),
    EXTERNAL_NOTIFICATION("External Notification"),
    STORE_FORWARD("Store & Forward"),
    RANGE_TEST("Range Test"),
    TELEMETRY("Telemetry"),
    CANNED_MESSAGE("Canned Message"),
    AUDIO("Audio"),
    REMOTE_HARDWARE("Remote Hardware"),
    NEIGHBOR_INFO("Neighbor Info"),
    AMBIENT_LIGHTING("Ambient Lighting"),
    DETECTION_SENSOR("Detection Sensor"),
    ;
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
private fun MeshAppBar(
    currentScreen: String,
    canNavigateBack: Boolean,
    navigateUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        title = { Text(currentScreen) },
        modifier = modifier,
        navigationIcon = {
            if (canNavigateBack) {
                IconButton(onClick = navigateUp) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        null,
                    )
                }
            }
        }
    )
}

@Composable
fun RadioConfigNavHost(
    node: NodeInfo,
    viewModel: UIViewModel = viewModel(),
    navController: NavHostController = rememberNavController(),
    modifier: Modifier,
) {
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
                        ?.let { navController.navigate(it.name) }
                }

                AdminProtos.AdminMessage.PayloadVariantCase.GET_MODULE_CONFIG_RESPONSE -> {
                    packetResponseState = PacketResponseState.Empty
                    val response = parsed.getModuleConfigResponse
                    moduleConfig = response
                    enumValues<ModuleDest>().find { it.name == "${response.payloadVariantCase}" }
                        ?.let { navController.navigate(it.name) }
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

    NavHost(
        navController = navController,
        startDestination = "home",
        modifier = modifier,
    ) {
        composable("home") {
            RadioSettingsScreen(
                enabled = connected && !isWaiting,
                isLocal = isLocal,
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

                        ConfigDest.LORA -> {
                            (packetResponseState as PacketResponseState.Loading).total = 2
                            channelList.clear()
                            viewModel.getChannel(destNum, 0)
                        }
                        is ConfigDest -> {
                            viewModel.getConfig(destNum, configType.ordinal)
                        }
                        ModuleDest.CANNED_MESSAGE -> {
                            (packetResponseState as PacketResponseState.Loading).total = 2
                            viewModel.getCannedMessages(destNum)
                        }
                        ModuleDest.EXTERNAL_NOTIFICATION -> {
                            (packetResponseState as PacketResponseState.Loading).total = 2
                            viewModel.getRingtone(destNum)
                        }
                        is ModuleDest -> {
                            viewModel.getModuleConfig(destNum, configType.ordinal)
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
                onPositiveClicked = { channelListInput ->
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
                onSaveClicked = { userInput ->
                    viewModel.setRemoteOwner(destNum, userInput)
                    userConfig = userInput
                }
            )
        }
        composable(ConfigDest.DEVICE.name) {
            DeviceConfigItemList(
                deviceConfig = radioConfig.device,
                enabled = connected,
                onSaveClicked = { deviceInput ->
                    val config = config { device = deviceInput }
                    viewModel.setRemoteConfig(destNum, config)
                    radioConfig = config
                }
            )
        }
        composable(ConfigDest.POSITION.name) {
            PositionConfigItemList(
                isLocal = isLocal,
                location = location,
                positionConfig = radioConfig.position,
                enabled = connected,
                onSaveClicked = { locationInput, positionInput ->
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
        composable(ConfigDest.POWER.name) {
            PowerConfigItemList(
                powerConfig = radioConfig.power,
                enabled = connected,
                onSaveClicked = { powerInput ->
                    val config = config { power = powerInput }
                    viewModel.setRemoteConfig(destNum, config)
                    radioConfig = config
                }
            )
        }
        composable(ConfigDest.NETWORK.name) {
            NetworkConfigItemList(
                networkConfig = radioConfig.network,
                enabled = connected,
                onSaveClicked = { networkInput ->
                    val config = config { network = networkInput }
                    viewModel.setRemoteConfig(destNum, config)
                    radioConfig = config
                }
            )
        }
        composable(ConfigDest.DISPLAY.name) {
            DisplayConfigItemList(
                displayConfig = radioConfig.display,
                enabled = connected,
                onSaveClicked = { displayInput ->
                    val config = config { display = displayInput }
                    viewModel.setRemoteConfig(destNum, config)
                    radioConfig = config
                }
            )
        }
        composable(ConfigDest.LORA.name) {
            LoRaConfigItemList(
                loraConfig = radioConfig.lora,
                primarySettings = channelList.getOrNull(0) ?: return@composable,
                enabled = connected,
                onSaveClicked = { loraInput ->
                    val config = config { lora = loraInput }
                    viewModel.setRemoteConfig(destNum, config)
                    radioConfig = config
                }
            )
        }
        composable(ConfigDest.BLUETOOTH.name) {
            BluetoothConfigItemList(
                bluetoothConfig = radioConfig.bluetooth,
                enabled = connected,
                onSaveClicked = { bluetoothInput ->
                    val config = config { bluetooth = bluetoothInput }
                    viewModel.setRemoteConfig(destNum, config)
                    radioConfig = config
                }
            )
        }
        composable(ModuleDest.MQTT.name) {
            MQTTConfigItemList(
                mqttConfig = moduleConfig.mqtt,
                enabled = connected,
                onSaveClicked = { mqttInput ->
                    val config = moduleConfig { mqtt = mqttInput }
                    viewModel.setModuleConfig(destNum, config)
                    moduleConfig = config
                }
            )
        }
        composable(ModuleDest.SERIAL.name) {
            SerialConfigItemList(
                serialConfig = moduleConfig.serial,
                enabled = connected,
                onSaveClicked = { serialInput ->
                    val config = moduleConfig { serial = serialInput }
                    viewModel.setModuleConfig(destNum, config)
                    moduleConfig = config
                }
            )
        }
        composable(ModuleDest.EXTERNAL_NOTIFICATION.name) {
            ExternalNotificationConfigItemList(
                ringtone = ringtone,
                extNotificationConfig = moduleConfig.externalNotification,
                enabled = connected,
                onSaveClicked = { ringtoneInput, extNotificationInput ->
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
        composable(ModuleDest.STORE_FORWARD.name) {
            StoreForwardConfigItemList(
                storeForwardConfig = moduleConfig.storeForward,
                enabled = connected,
                onSaveClicked = { storeForwardInput ->
                    val config = moduleConfig { storeForward = storeForwardInput }
                    viewModel.setModuleConfig(destNum, config)
                    moduleConfig = config
                }
            )
        }
        composable(ModuleDest.RANGE_TEST.name) {
            RangeTestConfigItemList(
                rangeTestConfig = moduleConfig.rangeTest,
                enabled = connected,
                onSaveClicked = { rangeTestInput ->
                    val config = moduleConfig { rangeTest = rangeTestInput }
                    viewModel.setModuleConfig(destNum, config)
                    moduleConfig = config
                }
            )
        }
        composable(ModuleDest.TELEMETRY.name) {
            TelemetryConfigItemList(
                telemetryConfig = moduleConfig.telemetry,
                enabled = connected,
                onSaveClicked = { telemetryInput ->
                    val config = moduleConfig { telemetry = telemetryInput }
                    viewModel.setModuleConfig(destNum, config)
                    moduleConfig = config
                }
            )
        }
        composable(ModuleDest.CANNED_MESSAGE.name) {
            CannedMessageConfigItemList(
                messages = cannedMessageMessages,
                cannedMessageConfig = moduleConfig.cannedMessage,
                enabled = connected,
                onSaveClicked = { messagesInput, cannedMessageInput ->
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
        composable(ModuleDest.AUDIO.name) {
            AudioConfigItemList(
                audioConfig = moduleConfig.audio,
                enabled = connected,
                onSaveClicked = { audioInput ->
                    val config = moduleConfig { audio = audioInput }
                    viewModel.setModuleConfig(destNum, config)
                    moduleConfig = config
                }
            )
        }
        composable(ModuleDest.REMOTE_HARDWARE.name) {
            RemoteHardwareConfigItemList(
                remoteHardwareConfig = moduleConfig.remoteHardware,
                enabled = connected,
                onSaveClicked = { remoteHardwareInput ->
                    val config = moduleConfig { remoteHardware = remoteHardwareInput }
                    viewModel.setModuleConfig(destNum, config)
                    moduleConfig = config
                }
            )
        }
        composable(ModuleDest.NEIGHBOR_INFO.name) {
            NeighborInfoConfigItemList(
                neighborInfoConfig = moduleConfig.neighborInfo,
                enabled = connected,
                onSaveClicked = { neighborInfoInput ->
                    val config = moduleConfig { neighborInfo = neighborInfoInput }
                    viewModel.setModuleConfig(destNum, config)
                    moduleConfig = config
                }
            )
        }
        composable(ModuleDest.AMBIENT_LIGHTING.name) {
            AmbientLightingConfigItemList(
                ambientLightingConfig = moduleConfig.ambientLighting,
                enabled = connected,
                onSaveClicked = { ambientLightingInput ->
                    val config = moduleConfig { ambientLighting = ambientLightingInput }
                    viewModel.setModuleConfig(destNum, config)
                    moduleConfig = config
                }
            )
        }
        composable(ModuleDest.DETECTION_SENSOR.name) {
            DetectionSensorConfigItemList(
                detectionSensorConfig = moduleConfig.detectionSensor,
                enabled = connected,
                onSaveClicked = { detectionSensorInput ->
                    val config = moduleConfig { detectionSensor = detectionSensorInput }
                    viewModel.setModuleConfig(destNum, config)
                    moduleConfig = config
                }
            )
        }
    }
}

@Composable
private fun NavCard(
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
private fun NavCard(@StringRes title: Int, enabled: Boolean, onClick: () -> Unit) {
    NavCard(title = stringResource(title), enabled = enabled, onClick = onClick)
}

@Composable
private fun NavButton(@StringRes title: Int, enabled: Boolean, onClick: () -> Unit) {
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

@Composable
private fun RadioSettingsScreen(
    enabled: Boolean = true,
    isLocal: Boolean = true,
    onRouteClick: (Any) -> Unit = {},
) {
    LazyColumn(
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        item { PreferenceCategory(stringResource(R.string.device_settings)) }
        item { NavCard("User", enabled = enabled) { onRouteClick("USER") } }
        item { NavCard("Channels", enabled = enabled) { onRouteClick("CHANNELS") } }
        items(ConfigDest.values()) { config ->
            NavCard(config.title, enabled = enabled) { onRouteClick(config) }
        }

        item { PreferenceCategory(stringResource(R.string.module_settings)) }
        items(ModuleDest.values()) { module ->
            NavCard(module.title, enabled = enabled) { onRouteClick(module) }
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
private fun RadioSettingsScreenPreview() {
    RadioSettingsScreen()
}
