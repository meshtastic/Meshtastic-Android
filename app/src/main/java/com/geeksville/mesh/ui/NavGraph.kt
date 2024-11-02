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
import com.geeksville.mesh.R
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.database.entity.NodeEntity
import com.geeksville.mesh.model.MetricsViewModel
import com.geeksville.mesh.model.RadioConfigViewModel
import com.geeksville.mesh.ui.components.DeviceMetricsScreen
import com.geeksville.mesh.ui.components.EnvironmentMetricsScreen
import com.geeksville.mesh.ui.components.PositionLogScreen
import com.geeksville.mesh.ui.components.SignalMetricsScreen
import com.geeksville.mesh.ui.components.TracerouteLogScreen
import com.geeksville.mesh.ui.components.config.AmbientLightingConfigScreen
import com.geeksville.mesh.ui.components.config.AudioConfigScreen
import com.geeksville.mesh.ui.components.config.BluetoothConfigScreen
import com.geeksville.mesh.ui.components.config.CannedMessageConfigScreen
import com.geeksville.mesh.ui.components.config.ChannelConfigScreen
import com.geeksville.mesh.ui.components.config.DetectionSensorConfigScreen
import com.geeksville.mesh.ui.components.config.DeviceConfigScreen
import com.geeksville.mesh.ui.components.config.DisplayConfigScreen
import com.geeksville.mesh.ui.components.config.ExternalNotificationConfigScreen
import com.geeksville.mesh.ui.components.config.LoRaConfigScreen
import com.geeksville.mesh.ui.components.config.MQTTConfigScreen
import com.geeksville.mesh.ui.components.config.NeighborInfoConfigScreen
import com.geeksville.mesh.ui.components.config.NetworkConfigScreen
import com.geeksville.mesh.ui.components.config.PaxcounterConfigScreen
import com.geeksville.mesh.ui.components.config.PositionConfigScreen
import com.geeksville.mesh.ui.components.config.PowerConfigScreen
import com.geeksville.mesh.ui.components.config.RangeTestConfigScreen
import com.geeksville.mesh.ui.components.config.RemoteHardwareConfigScreen
import com.geeksville.mesh.ui.components.config.SecurityConfigScreen
import com.geeksville.mesh.ui.components.config.SerialConfigScreen
import com.geeksville.mesh.ui.components.config.StoreForwardConfigScreen
import com.geeksville.mesh.ui.components.config.TelemetryConfigScreen
import com.geeksville.mesh.ui.components.config.UserConfigScreen
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

@Suppress("LongMethod")
@Composable
fun NavGraph(
    node: NodeEntity?,
    viewModel: RadioConfigViewModel = hiltViewModel(),
    navController: NavHostController = rememberNavController(),
    startDestination: String,
    modifier: Modifier = Modifier,
) {
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
        composable("PositionLog") {
            val parentEntry = remember { navController.getBackStackEntry("NodeDetails") }
            PositionLogScreen(hiltViewModel<MetricsViewModel>(parentEntry))
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
            ) { navController.navigate(route = it) }
        }
        composable(ConfigRoute.USER.name) {
            UserConfigScreen(viewModel)
        }
        composable(ConfigRoute.CHANNELS.name) {
            ChannelConfigScreen(viewModel)
        }
        composable(ConfigRoute.DEVICE.name) {
            DeviceConfigScreen(viewModel)
        }
        composable(ConfigRoute.POSITION.name) {
            PositionConfigScreen(viewModel)
        }
        composable(ConfigRoute.POWER.name) {
            PowerConfigScreen(viewModel)
        }
        composable(ConfigRoute.NETWORK.name) {
            NetworkConfigScreen(viewModel)
        }
        composable(ConfigRoute.DISPLAY.name) {
            DisplayConfigScreen(viewModel)
        }
        composable(ConfigRoute.LORA.name) {
            LoRaConfigScreen(viewModel)
        }
        composable(ConfigRoute.BLUETOOTH.name) {
            BluetoothConfigScreen(viewModel)
        }
        composable(ConfigRoute.SECURITY.name) {
            SecurityConfigScreen(viewModel)
        }
        composable(ModuleRoute.MQTT.name) {
            MQTTConfigScreen(viewModel)
        }
        composable(ModuleRoute.SERIAL.name) {
            SerialConfigScreen(viewModel)
        }
        composable(ModuleRoute.EXTERNAL_NOTIFICATION.name) {
            ExternalNotificationConfigScreen(viewModel)
        }
        composable(ModuleRoute.STORE_FORWARD.name) {
            StoreForwardConfigScreen(viewModel)
        }
        composable(ModuleRoute.RANGE_TEST.name) {
            RangeTestConfigScreen(viewModel)
        }
        composable(ModuleRoute.TELEMETRY.name) {
            TelemetryConfigScreen(viewModel)
        }
        composable(ModuleRoute.CANNED_MESSAGE.name) {
            CannedMessageConfigScreen(viewModel)
        }
        composable(ModuleRoute.AUDIO.name) {
            AudioConfigScreen(viewModel)
        }
        composable(ModuleRoute.REMOTE_HARDWARE.name) {
            RemoteHardwareConfigScreen(viewModel)
        }
        composable(ModuleRoute.NEIGHBOR_INFO.name) {
            NeighborInfoConfigScreen(viewModel)
        }
        composable(ModuleRoute.AMBIENT_LIGHTING.name) {
            AmbientLightingConfigScreen(viewModel)
        }
        composable(ModuleRoute.DETECTION_SENSOR.name) {
            DetectionSensorConfigScreen(viewModel)
        }
        composable(ModuleRoute.PAXCOUNTER.name) {
            PaxcounterConfigScreen(viewModel)
        }
    }
}
