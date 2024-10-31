package com.geeksville.mesh.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.DisplaySettings
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PermScanWifi
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.geeksville.mesh.Position
import com.geeksville.mesh.R
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.config
import com.geeksville.mesh.database.entity.NodeEntity
import com.geeksville.mesh.model.Channel
import com.geeksville.mesh.model.MetricsViewModel
import com.geeksville.mesh.model.RadioConfigViewModel
import com.geeksville.mesh.moduleConfig
import com.geeksville.mesh.ui.components.DeviceMetricsScreen
import com.geeksville.mesh.ui.components.EnvironmentMetricsScreen
import com.geeksville.mesh.ui.components.SignalMetricsScreen
import com.geeksville.mesh.ui.components.TracerouteLogScreen
import com.geeksville.mesh.ui.components.config.AmbientLightingConfigItemList
import com.geeksville.mesh.ui.components.config.AudioConfigItemList
import com.geeksville.mesh.ui.components.config.BluetoothConfigItemList
import com.geeksville.mesh.ui.components.config.CannedMessageConfigItemList
import com.geeksville.mesh.ui.components.config.ChannelSettingsItemList
import com.geeksville.mesh.ui.components.config.DetectionSensorConfigItemList
import com.geeksville.mesh.ui.components.config.DeviceConfigItemList
import com.geeksville.mesh.ui.components.config.DisplayConfigItemList
import com.geeksville.mesh.ui.components.config.ExternalNotificationConfigItemList
import com.geeksville.mesh.ui.components.config.LoRaConfigItemList
import com.geeksville.mesh.ui.components.config.MQTTConfigItemList
import com.geeksville.mesh.ui.components.config.NeighborInfoConfigItemList
import com.geeksville.mesh.ui.components.config.NetworkConfigItemList
import com.geeksville.mesh.ui.components.config.PacketResponseStateDialog
import com.geeksville.mesh.ui.components.config.PaxcounterConfigItemList
import com.geeksville.mesh.ui.components.config.PositionConfigItemList
import com.geeksville.mesh.ui.components.config.PowerConfigItemList
import com.geeksville.mesh.ui.components.config.RangeTestConfigItemList
import com.geeksville.mesh.ui.components.config.RemoteHardwareConfigItemList
import com.geeksville.mesh.ui.components.config.SecurityConfigItemList
import com.geeksville.mesh.ui.components.config.SerialConfigItemList
import com.geeksville.mesh.ui.components.config.StoreForwardConfigItemList
import com.geeksville.mesh.ui.components.config.TelemetryConfigItemList
import com.geeksville.mesh.ui.components.config.UserConfigItemList
import com.google.accompanist.themeadapter.appcompat.AppCompatTheme
import dagger.hilt.android.AndroidEntryPoint

internal fun FragmentManager.navigateToNavGraph(
    destNum: Int? = null,
    startDestination: String = "RadioConfig",
) {
    val radioConfigFragment = NavGraphFragment().apply {
        arguments = bundleOf("destNum" to destNum, "startDestination" to startDestination)
    }
    beginTransaction()
        .replace(R.id.mainActivityLayout, radioConfigFragment)
        .addToBackStack(null)
        .commit()
}

@AndroidEntryPoint
class NavGraphFragment : ScreenFragment("NavGraph"), Logging {

    private val model: RadioConfigViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val destNum = arguments?.getInt("destNum")
        val startDestination = arguments?.getString("startDestination") ?: "RadioConfig"
        model.setDestNum(destNum)

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setBackgroundColor(ContextCompat.getColor(context, R.color.colorAdvancedBackground))
            setContent {
                val node by model.destNode.collectAsStateWithLifecycle()

                AppCompatTheme {
                    val navController: NavHostController = rememberNavController()
                    Scaffold(
                        topBar = {
                            MeshAppBar(
                                currentScreen = node?.user?.longName
                                    ?: stringResource(R.string.unknown_username),
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
                        NavGraph(
                            node = node,
                            viewModel = model,
                            navController = navController,
                            startDestination = startDestination,
                            modifier = Modifier.padding(innerPadding),
                        )
                    }
                }
            }
        }
    }
}

enum class AdminRoute(@StringRes val title: Int) {
    REBOOT(R.string.reboot),
    SHUTDOWN(R.string.shutdown),
    FACTORY_RESET(R.string.factory_reset),
    NODEDB_RESET(R.string.nodedb_reset),
}

// Config (configType = AdminProtos.AdminMessage.ConfigType)
enum class ConfigRoute(val title: String, val icon: ImageVector?, val configType: Int = 0) {
    USER("User", Icons.Default.Person, 0),
    CHANNELS("Channels", Icons.AutoMirrored.Default.List, 0),
    DEVICE("Device", Icons.Default.Router, 0),
    POSITION("Position", Icons.Default.LocationOn, 1),
    POWER("Power", Icons.Default.Power, 2),
    NETWORK("Network", Icons.Default.Wifi, 3),
    DISPLAY("Display", Icons.Default.DisplaySettings, 4),
    LORA("LoRa", Icons.Default.CellTower, 5),
    BLUETOOTH("Bluetooth", Icons.Default.Bluetooth, 6),
    SECURITY("Security", Icons.Default.Security, configType = 7),
}

// ModuleConfig (configType = AdminProtos.AdminMessage.ModuleConfigType)
enum class ModuleRoute(val title: String, val icon: ImageVector?, val configType: Int = 0) {
    MQTT("MQTT", Icons.Default.Cloud, 0),
    SERIAL("Serial", Icons.Default.Usb, 1),
    EXTERNAL_NOTIFICATION("External Notification", Icons.Default.Notifications, 2),
    STORE_FORWARD("Store & Forward", Icons.AutoMirrored.Default.Forward, 3),
    RANGE_TEST("Range Test", Icons.Default.Speed, 4),
    TELEMETRY("Telemetry", Icons.Default.DataUsage, 5),
    CANNED_MESSAGE("Canned Message", Icons.AutoMirrored.Default.Message, 6),
    AUDIO("Audio", Icons.AutoMirrored.Default.VolumeUp, 7),
    REMOTE_HARDWARE("Remote Hardware", Icons.Default.SettingsRemote, 8),
    NEIGHBOR_INFO("Neighbor Info", Icons.Default.People, 9),
    AMBIENT_LIGHTING("Ambient Lighting", Icons.Default.LightMode, 10),
    DETECTION_SENSOR("Detection Sensor", Icons.Default.Sensors, 11),
    PAXCOUNTER("Paxcounter", Icons.Default.PermScanWifi, 12),
}

/**
 * Generic sealed class defines each possible state of a response.
 */
sealed class ResponseState<out T> {
    data object Empty : ResponseState<Nothing>()
    data class Loading(var total: Int = 1, var completed: Int = 0) : ResponseState<Nothing>()
    data class Success<T>(val result: T) : ResponseState<T>()
    data class Error(val error: String) : ResponseState<Nothing>()

    fun isWaiting() = this !is Empty
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
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(id = R.string.navigate_back),
                    )
                }
            }
        }
    )
}

@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun NavGraph(
    node: NodeEntity?,
    viewModel: RadioConfigViewModel = hiltViewModel(),
    navController: NavHostController = rememberNavController(),
    startDestination: String,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()
    if (state.responseState.isWaiting()) {
        PacketResponseStateDialog(
            state = state.responseState,
            onDismiss = viewModel::clearPacketResponse,
            onComplete = {
                val route = state.route
                if (ConfigRoute.entries.any { it.name == route } ||
                    ModuleRoute.entries.any { it.name == route }) {
                    navController.navigate(route = route)
                    viewModel.clearPacketResponse()
                }
            },
        )
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable("NodeDetails") {
            NodeDetailScreen(
                node = node,
            ) { navController.navigate(route = it) }
        }
        composable("DeviceMetrics") {
            val parentEntry = remember { navController.getBackStackEntry("NodeDetails") }
            DeviceMetricsScreen(hiltViewModel<MetricsViewModel>(parentEntry))
        }
        composable("EnvironmentMetrics") {
            val parentEntry = remember { navController.getBackStackEntry("NodeDetails") }
            EnvironmentMetricsScreen(hiltViewModel<MetricsViewModel>(parentEntry))
        }
        composable("SignalMetrics") {
            val parentEntry = remember { navController.getBackStackEntry("NodeDetails") }
            SignalMetricsScreen(hiltViewModel<MetricsViewModel>(parentEntry))
        }
        composable("TracerouteList") {
            val parentEntry = remember { navController.getBackStackEntry("NodeDetails") }
            TracerouteLogScreen(hiltViewModel<MetricsViewModel>(parentEntry))
        }
        composable("RadioConfig") {
            RadioConfigScreen(
                node = node,
                viewModel = viewModel,
            )
        }
        composable(ConfigRoute.USER.name) {
            UserConfigItemList(
                userConfig = state.userConfig,
                enabled = state.connected,
                onSaveClicked = { userInput ->
                    viewModel.setOwner(userInput)
                }
            )
        }
        composable(ConfigRoute.CHANNELS.name) {
            ChannelSettingsItemList(
                settingsList = state.channelList,
                modemPresetName = Channel(loraConfig = state.radioConfig.lora).name,
                enabled = state.connected,
                maxChannels = viewModel.maxChannels,
                onPositiveClicked = { channelListInput ->
                    viewModel.updateChannels(channelListInput, state.channelList)
                },
            )
        }
        composable(ConfigRoute.DEVICE.name) {
            DeviceConfigItemList(
                deviceConfig = state.radioConfig.device,
                enabled = state.connected,
                onSaveClicked = { deviceInput ->
                    val config = config { device = deviceInput }
                    viewModel.setConfig(config)
                }
            )
        }
        composable(ConfigRoute.POSITION.name) {
            val currentPosition = Position(
                latitude = node?.latitude ?: 0.0,
                longitude = node?.longitude ?: 0.0,
                altitude = node?.position?.altitude ?: 0,
                time = 1, // ignore time for fixed_position
            )
            PositionConfigItemList(
                location = currentPosition,
                positionConfig = state.radioConfig.position,
                enabled = state.connected,
                onSaveClicked = { locationInput, positionInput ->
                    if (positionInput.fixedPosition) {
                        if (locationInput != currentPosition) {
                            viewModel.setFixedPosition(locationInput)
                        }
                    } else {
                        if (state.radioConfig.position.fixedPosition) {
                            // fixed position changed from enabled to disabled
                            viewModel.removeFixedPosition()
                        }
                    }
                    val config = config { position = positionInput }
                    viewModel.setConfig(config)
                }
            )
        }
        composable(ConfigRoute.POWER.name) {
            PowerConfigItemList(
                powerConfig = state.radioConfig.power,
                enabled = state.connected,
                onSaveClicked = { powerInput ->
                    val config = config { power = powerInput }
                    viewModel.setConfig(config)
                }
            )
        }
        composable(ConfigRoute.NETWORK.name) {
            NetworkConfigItemList(
                networkConfig = state.radioConfig.network,
                enabled = state.connected,
                onSaveClicked = { networkInput ->
                    val config = config { network = networkInput }
                    viewModel.setConfig(config)
                }
            )
        }
        composable(ConfigRoute.DISPLAY.name) {
            DisplayConfigItemList(
                displayConfig = state.radioConfig.display,
                enabled = state.connected,
                onSaveClicked = { displayInput ->
                    val config = config { display = displayInput }
                    viewModel.setConfig(config)
                }
            )
        }
        composable(ConfigRoute.LORA.name) {
            LoRaConfigItemList(
                loraConfig = state.radioConfig.lora,
                primarySettings = state.channelList.getOrNull(0) ?: return@composable,
                enabled = state.connected,
                onSaveClicked = { loraInput ->
                    val config = config { lora = loraInput }
                    viewModel.setConfig(config)
                },
                hasPaFan = viewModel.hasPaFan,
            )
        }
        composable(ConfigRoute.BLUETOOTH.name) {
            BluetoothConfigItemList(
                bluetoothConfig = state.radioConfig.bluetooth,
                enabled = state.connected,
                onSaveClicked = { bluetoothInput ->
                    val config = config { bluetooth = bluetoothInput }
                    viewModel.setConfig(config)
                }
            )
        }
        composable(ConfigRoute.SECURITY.name) {
            SecurityConfigItemList(
                securityConfig = state.radioConfig.security,
                enabled = state.connected,
                onConfirm = { securityInput ->
                    val config = config { security = securityInput }
                    viewModel.setConfig(config)
                }
            )
        }
        composable(ModuleRoute.MQTT.name) {
            MQTTConfigItemList(
                mqttConfig = state.moduleConfig.mqtt,
                enabled = state.connected,
                onSaveClicked = { mqttInput ->
                    val config = moduleConfig { mqtt = mqttInput }
                    viewModel.setModuleConfig(config)
                }
            )
        }
        composable(ModuleRoute.SERIAL.name) {
            SerialConfigItemList(
                serialConfig = state.moduleConfig.serial,
                enabled = state.connected,
                onSaveClicked = { serialInput ->
                    val config = moduleConfig { serial = serialInput }
                    viewModel.setModuleConfig(config)
                }
            )
        }
        composable(ModuleRoute.EXTERNAL_NOTIFICATION.name) {
            ExternalNotificationConfigItemList(
                ringtone = state.ringtone,
                extNotificationConfig = state.moduleConfig.externalNotification,
                enabled = state.connected,
                onSaveClicked = { ringtoneInput, extNotificationInput ->
                    if (ringtoneInput != state.ringtone) {
                        viewModel.setRingtone(ringtoneInput)
                    }
                    if (extNotificationInput != state.moduleConfig.externalNotification) {
                        val config = moduleConfig { externalNotification = extNotificationInput }
                        viewModel.setModuleConfig(config)
                    }
                }
            )
        }
        composable(ModuleRoute.STORE_FORWARD.name) {
            StoreForwardConfigItemList(
                storeForwardConfig = state.moduleConfig.storeForward,
                enabled = state.connected,
                onSaveClicked = { storeForwardInput ->
                    val config = moduleConfig { storeForward = storeForwardInput }
                    viewModel.setModuleConfig(config)
                }
            )
        }
        composable(ModuleRoute.RANGE_TEST.name) {
            RangeTestConfigItemList(
                rangeTestConfig = state.moduleConfig.rangeTest,
                enabled = state.connected,
                onSaveClicked = { rangeTestInput ->
                    val config = moduleConfig { rangeTest = rangeTestInput }
                    viewModel.setModuleConfig(config)
                }
            )
        }
        composable(ModuleRoute.TELEMETRY.name) {
            TelemetryConfigItemList(
                telemetryConfig = state.moduleConfig.telemetry,
                enabled = state.connected,
                onSaveClicked = { telemetryInput ->
                    val config = moduleConfig { telemetry = telemetryInput }
                    viewModel.setModuleConfig(config)
                }
            )
        }
        composable(ModuleRoute.CANNED_MESSAGE.name) {
            CannedMessageConfigItemList(
                messages = state.cannedMessageMessages,
                cannedMessageConfig = state.moduleConfig.cannedMessage,
                enabled = state.connected,
                onSaveClicked = { messagesInput, cannedMessageInput ->
                    if (messagesInput != state.cannedMessageMessages) {
                        viewModel.setCannedMessages(messagesInput)
                    }
                    if (cannedMessageInput != state.moduleConfig.cannedMessage) {
                        val config = moduleConfig { cannedMessage = cannedMessageInput }
                        viewModel.setModuleConfig(config)
                    }
                }
            )
        }
        composable(ModuleRoute.AUDIO.name) {
            AudioConfigItemList(
                audioConfig = state.moduleConfig.audio,
                enabled = state.connected,
                onSaveClicked = { audioInput ->
                    val config = moduleConfig { audio = audioInput }
                    viewModel.setModuleConfig(config)
                }
            )
        }
        composable(ModuleRoute.REMOTE_HARDWARE.name) {
            RemoteHardwareConfigItemList(
                remoteHardwareConfig = state.moduleConfig.remoteHardware,
                enabled = state.connected,
                onSaveClicked = { remoteHardwareInput ->
                    val config = moduleConfig { remoteHardware = remoteHardwareInput }
                    viewModel.setModuleConfig(config)
                }
            )
        }
        composable(ModuleRoute.NEIGHBOR_INFO.name) {
            NeighborInfoConfigItemList(
                neighborInfoConfig = state.moduleConfig.neighborInfo,
                enabled = state.connected,
                onSaveClicked = { neighborInfoInput ->
                    val config = moduleConfig { neighborInfo = neighborInfoInput }
                    viewModel.setModuleConfig(config)
                }
            )
        }
        composable(ModuleRoute.AMBIENT_LIGHTING.name) {
            AmbientLightingConfigItemList(
                ambientLightingConfig = state.moduleConfig.ambientLighting,
                enabled = state.connected,
                onSaveClicked = { ambientLightingInput ->
                    val config = moduleConfig { ambientLighting = ambientLightingInput }
                    viewModel.setModuleConfig(config)
                }
            )
        }
        composable(ModuleRoute.DETECTION_SENSOR.name) {
            DetectionSensorConfigItemList(
                detectionSensorConfig = state.moduleConfig.detectionSensor,
                enabled = state.connected,
                onSaveClicked = { detectionSensorInput ->
                    val config = moduleConfig { detectionSensor = detectionSensorInput }
                    viewModel.setModuleConfig(config)
                }
            )
        }
        composable(ModuleRoute.PAXCOUNTER.name) {
            PaxcounterConfigItemList(
                paxcounterConfig = state.moduleConfig.paxcounter,
                enabled = state.connected,
                onSaveClicked = { paxcounterConfigInput ->
                    val config = moduleConfig { paxcounter = paxcounterConfigInput }
                    viewModel.setModuleConfig(config)
                }
            )
        }
    }
}
