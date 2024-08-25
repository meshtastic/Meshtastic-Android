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
import androidx.compose.material.Card
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.twotone.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.geeksville.mesh.NodeInfo
import com.geeksville.mesh.R
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.config
import com.geeksville.mesh.model.Channel
import com.geeksville.mesh.model.RadioConfigViewModel
import com.geeksville.mesh.moduleConfig
import com.geeksville.mesh.service.MeshService.ConnectionState
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

internal fun FragmentManager.navigateToRadioConfig(destNum: Int? = null) {
    val radioConfigFragment = DeviceSettingsFragment().apply {
        arguments = bundleOf("destNum" to destNum)
    }
    beginTransaction()
        .replace(R.id.mainActivityLayout, radioConfigFragment)
        .addToBackStack(null)
        .commit()
}

@AndroidEntryPoint
class DeviceSettingsFragment : ScreenFragment("Radio Configuration"), Logging {

    private val model: RadioConfigViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val destNum = arguments?.getInt("destNum")
        model.setDestNum(destNum)

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
                    //     NavRoute.entries.find { it.name == route }?.title
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
                            node = node,
                            viewModel = model,
                            navController = navController,
                            modifier = Modifier.padding(innerPadding),
                        )
                    }
                }
            }
        }
    }
}

// Config (configType = AdminProtos.AdminMessage.ConfigType)
enum class ConfigRoute(val title: String, val configType: Int = 0) {
    USER("User"),
    CHANNELS("Channels"),
    DEVICE("Device", 0),
    POSITION("Position", 1),
    POWER("Power", 2),
    NETWORK("Network", 3),
    DISPLAY("Display", 4),
    LORA("LoRa", 5),
    BLUETOOTH("Bluetooth", 6),
    SECURITY("Security", 7),
    ;
}

// ModuleConfig (configType = AdminProtos.AdminMessage.ModuleConfigType)
enum class ModuleRoute(val title: String, val configType: Int = 0) {
    MQTT("MQTT", 0),
    SERIAL("Serial", 1),
    EXTERNAL_NOTIFICATION("External Notification", 2),
    STORE_FORWARD("Store & Forward", 3),
    RANGE_TEST("Range Test", 4),
    TELEMETRY("Telemetry", 5),
    CANNED_MESSAGE("Canned Message", 6),
    AUDIO("Audio", 7),
    REMOTE_HARDWARE("Remote Hardware", 8),
    NEIGHBOR_INFO("Neighbor Info", 9),
    AMBIENT_LIGHTING("Ambient Lighting", 10),
    DETECTION_SENSOR("Detection Sensor", 11),
    PAXCOUNTER("Paxcounter", 12),
    ;
}

private fun getName(route: Any): String = when (route) {
    is ConfigRoute -> route.name
    is ModuleRoute -> route.name
    else -> ""
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
                        null,
                    )
                }
            }
        }
    )
}

@Composable
fun RadioConfigNavHost(
    node: NodeInfo?,
    viewModel: RadioConfigViewModel = hiltViewModel(),
    navController: NavHostController = rememberNavController(),
    modifier: Modifier,
) {
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val connected = connectionState == ConnectionState.CONNECTED && node != null

    val destNum = node?.num ?: 0
    val isLocal = destNum == viewModel.myNodeNum

    val radioConfigState by viewModel.radioConfigState.collectAsStateWithLifecycle()

    val deviceProfile by viewModel.deviceProfile.collectAsStateWithLifecycle()
    val isWaiting = radioConfigState.responseState.isWaiting()
    var showEditDeviceProfileDialog by remember { mutableStateOf(false) }

    val importConfigLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == Activity.RESULT_OK) {
            showEditDeviceProfileDialog = true
            it.data?.data?.let { uri -> viewModel.importProfile(uri) }
        }
    }

    val exportConfigLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == Activity.RESULT_OK) {
            it.data?.data?.let { uri -> viewModel.exportProfile(uri) }
        }
    }

    if (showEditDeviceProfileDialog) EditDeviceProfileDialog(
        title = if (deviceProfile != null) "Import configuration" else "Export configuration",
        deviceProfile = deviceProfile ?: viewModel.currentDeviceProfile,
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
        radioConfigState.responseState,
        onDismiss = {
            showEditDeviceProfileDialog = false
            viewModel.clearPacketResponse()
        },
        onComplete = {
            val route = radioConfigState.route
            if (route.isNotEmpty()) navController.navigate(route)
            viewModel.clearPacketResponse()
        }
    )

    NavHost(
        navController = navController,
        startDestination = "home",
        modifier = modifier,
    ) {
        composable("home") {
            RadioSettingsScreen(
                enabled = connected && !isWaiting,
                isLocal = isLocal,
                onRouteClick = { route ->
                    viewModel.setResponseStateLoading(getName(route))
                    when (route) {
                        ConfigRoute.USER -> {
                            viewModel.getOwner(destNum)
                        }

                        ConfigRoute.CHANNELS -> {
                            viewModel.getChannel(destNum, 0)
                            viewModel.getConfig(destNum, ConfigRoute.LORA.configType)
                        }

                        "IMPORT" -> {
                            viewModel.clearPacketResponse()
                            viewModel.setDeviceProfile(null)
                            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = "application/*"
                            }
                            importConfigLauncher.launch(intent)
                        }

                        "EXPORT" -> {
                            viewModel.clearPacketResponse()
                            viewModel.setDeviceProfile(null)
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

                        is ConfigRoute -> {
                            if (route == ConfigRoute.LORA) {
                                viewModel.getChannel(destNum, 0)
                            }
                            viewModel.getConfig(destNum, route.configType)
                        }

                        is ModuleRoute -> {
                            if (route == ModuleRoute.CANNED_MESSAGE) {
                                viewModel.getCannedMessages(destNum)
                            }
                            if (route == ModuleRoute.EXTERNAL_NOTIFICATION) {
                                viewModel.getRingtone(destNum)
                            }
                            viewModel.getModuleConfig(destNum, route.configType)
                        }
                    }
                },
            )
        }
        composable(ConfigRoute.USER.name) {
            UserConfigItemList(
                userConfig = radioConfigState.userConfig,
                enabled = connected,
                onSaveClicked = { userInput ->
                    viewModel.setRemoteOwner(destNum, userInput)
                }
            )
        }
        composable(ConfigRoute.CHANNELS.name) {
            ChannelSettingsItemList(
                settingsList = radioConfigState.channelList,
                modemPresetName = Channel(loraConfig = radioConfigState.radioConfig.lora).name,
                enabled = connected,
                maxChannels = viewModel.maxChannels,
                onPositiveClicked = { channelListInput ->
                    viewModel.updateChannels(destNum, channelListInput, radioConfigState.channelList)
                },
            )
        }
        composable(ConfigRoute.DEVICE.name) {
            DeviceConfigItemList(
                deviceConfig = radioConfigState.radioConfig.device,
                enabled = connected,
                onSaveClicked = { deviceInput ->
                    val config = config { device = deviceInput }
                    viewModel.setRemoteConfig(destNum, config)
                }
            )
        }
        composable(ConfigRoute.POSITION.name) {
            PositionConfigItemList(
                isLocal = isLocal,
                location = node?.position,
                positionConfig = radioConfigState.radioConfig.position,
                enabled = connected,
                onSaveClicked = { locationInput, positionInput ->
                    if (positionInput.fixedPosition) {
                        if (locationInput != null && locationInput != node?.position) {
                            viewModel.setFixedPosition(locationInput)
                        }
                    } else {
                        if (radioConfigState.radioConfig.position.fixedPosition) {
                            // fixed position changed from enabled to disabled
                            viewModel.removeFixedPosition()
                        }
                    }
                    val config = config { position = positionInput }
                    viewModel.setRemoteConfig(destNum, config)
                }
            )
        }
        composable(ConfigRoute.POWER.name) {
            PowerConfigItemList(
                powerConfig = radioConfigState.radioConfig.power,
                enabled = connected,
                onSaveClicked = { powerInput ->
                    val config = config { power = powerInput }
                    viewModel.setRemoteConfig(destNum, config)
                }
            )
        }
        composable(ConfigRoute.NETWORK.name) {
            NetworkConfigItemList(
                networkConfig = radioConfigState.radioConfig.network,
                enabled = connected,
                onSaveClicked = { networkInput ->
                    val config = config { network = networkInput }
                    viewModel.setRemoteConfig(destNum, config)
                }
            )
        }
        composable(ConfigRoute.DISPLAY.name) {
            DisplayConfigItemList(
                displayConfig = radioConfigState.radioConfig.display,
                enabled = connected,
                onSaveClicked = { displayInput ->
                    val config = config { display = displayInput }
                    viewModel.setRemoteConfig(destNum, config)
                }
            )
        }
        composable(ConfigRoute.LORA.name) {
            LoRaConfigItemList(
                loraConfig = radioConfigState.radioConfig.lora,
                primarySettings = radioConfigState.channelList.getOrNull(0) ?: return@composable,
                enabled = connected,
                onSaveClicked = { loraInput ->
                    val config = config { lora = loraInput }
                    viewModel.setRemoteConfig(destNum, config)
                }
            )
        }
        composable(ConfigRoute.BLUETOOTH.name) {
            BluetoothConfigItemList(
                bluetoothConfig = radioConfigState.radioConfig.bluetooth,
                enabled = connected,
                onSaveClicked = { bluetoothInput ->
                    val config = config { bluetooth = bluetoothInput }
                    viewModel.setRemoteConfig(destNum, config)
                }
            )
        }
        composable(ConfigRoute.SECURITY.name) {
            SecurityConfigItemList(
                securityConfig = radioConfigState.radioConfig.security,
                enabled = connected,
                onConfirm = { securityInput ->
                    val config = config { security = securityInput }
                    viewModel.setRemoteConfig(destNum, config)
                }
            )
        }
        composable(ModuleRoute.MQTT.name) {
            MQTTConfigItemList(
                mqttConfig = radioConfigState.moduleConfig.mqtt,
                enabled = connected,
                onSaveClicked = { mqttInput ->
                    val config = moduleConfig { mqtt = mqttInput }
                    viewModel.setModuleConfig(destNum, config)
                }
            )
        }
        composable(ModuleRoute.SERIAL.name) {
            SerialConfigItemList(
                serialConfig = radioConfigState.moduleConfig.serial,
                enabled = connected,
                onSaveClicked = { serialInput ->
                    val config = moduleConfig { serial = serialInput }
                    viewModel.setModuleConfig(destNum, config)
                }
            )
        }
        composable(ModuleRoute.EXTERNAL_NOTIFICATION.name) {
            ExternalNotificationConfigItemList(
                ringtone = radioConfigState.ringtone,
                extNotificationConfig = radioConfigState.moduleConfig.externalNotification,
                enabled = connected,
                onSaveClicked = { ringtoneInput, extNotificationInput ->
                    if (ringtoneInput != radioConfigState.ringtone) {
                        viewModel.setRingtone(destNum, ringtoneInput)
                    }
                    if (extNotificationInput != radioConfigState.moduleConfig.externalNotification) {
                        val config = moduleConfig { externalNotification = extNotificationInput }
                        viewModel.setModuleConfig(destNum, config)
                    }
                }
            )
        }
        composable(ModuleRoute.STORE_FORWARD.name) {
            StoreForwardConfigItemList(
                storeForwardConfig = radioConfigState.moduleConfig.storeForward,
                enabled = connected,
                onSaveClicked = { storeForwardInput ->
                    val config = moduleConfig { storeForward = storeForwardInput }
                    viewModel.setModuleConfig(destNum, config)
                }
            )
        }
        composable(ModuleRoute.RANGE_TEST.name) {
            RangeTestConfigItemList(
                rangeTestConfig = radioConfigState.moduleConfig.rangeTest,
                enabled = connected,
                onSaveClicked = { rangeTestInput ->
                    val config = moduleConfig { rangeTest = rangeTestInput }
                    viewModel.setModuleConfig(destNum, config)
                }
            )
        }
        composable(ModuleRoute.TELEMETRY.name) {
            TelemetryConfigItemList(
                telemetryConfig = radioConfigState.moduleConfig.telemetry,
                enabled = connected,
                onSaveClicked = { telemetryInput ->
                    val config = moduleConfig { telemetry = telemetryInput }
                    viewModel.setModuleConfig(destNum, config)
                }
            )
        }
        composable(ModuleRoute.CANNED_MESSAGE.name) {
            CannedMessageConfigItemList(
                messages = radioConfigState.cannedMessageMessages,
                cannedMessageConfig = radioConfigState.moduleConfig.cannedMessage,
                enabled = connected,
                onSaveClicked = { messagesInput, cannedMessageInput ->
                    if (messagesInput != radioConfigState.cannedMessageMessages) {
                        viewModel.setCannedMessages(destNum, messagesInput)
                    }
                    if (cannedMessageInput != radioConfigState.moduleConfig.cannedMessage) {
                        val config = moduleConfig { cannedMessage = cannedMessageInput }
                        viewModel.setModuleConfig(destNum, config)
                    }
                }
            )
        }
        composable(ModuleRoute.AUDIO.name) {
            AudioConfigItemList(
                audioConfig = radioConfigState.moduleConfig.audio,
                enabled = connected,
                onSaveClicked = { audioInput ->
                    val config = moduleConfig { audio = audioInput }
                    viewModel.setModuleConfig(destNum, config)
                }
            )
        }
        composable(ModuleRoute.REMOTE_HARDWARE.name) {
            RemoteHardwareConfigItemList(
                remoteHardwareConfig = radioConfigState.moduleConfig.remoteHardware,
                enabled = connected,
                onSaveClicked = { remoteHardwareInput ->
                    val config = moduleConfig { remoteHardware = remoteHardwareInput }
                    viewModel.setModuleConfig(destNum, config)
                }
            )
        }
        composable(ModuleRoute.NEIGHBOR_INFO.name) {
            NeighborInfoConfigItemList(
                neighborInfoConfig = radioConfigState.moduleConfig.neighborInfo,
                enabled = connected,
                onSaveClicked = { neighborInfoInput ->
                    val config = moduleConfig { neighborInfo = neighborInfoInput }
                    viewModel.setModuleConfig(destNum, config)
                }
            )
        }
        composable(ModuleRoute.AMBIENT_LIGHTING.name) {
            AmbientLightingConfigItemList(
                ambientLightingConfig = radioConfigState.moduleConfig.ambientLighting,
                enabled = connected,
                onSaveClicked = { ambientLightingInput ->
                    val config = moduleConfig { ambientLighting = ambientLightingInput }
                    viewModel.setModuleConfig(destNum, config)
                }
            )
        }
        composable(ModuleRoute.DETECTION_SENSOR.name) {
            DetectionSensorConfigItemList(
                detectionSensorConfig = radioConfigState.moduleConfig.detectionSensor,
                enabled = connected,
                onSaveClicked = { detectionSensorInput ->
                    val config = moduleConfig { detectionSensor = detectionSensorInput }
                    viewModel.setModuleConfig(destNum, config)
                }
            )
        }
        composable(ModuleRoute.PAXCOUNTER.name) {
            PaxcounterConfigItemList(
                paxcounterConfig = radioConfigState.moduleConfig.paxcounter,
                enabled = connected,
                onSaveClicked = { paxcounterConfigInput ->
                    val config = moduleConfig { paxcounter = paxcounterConfigInput }
                    viewModel.setModuleConfig(destNum, config)
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
                Icons.AutoMirrored.TwoTone.KeyboardArrowRight, "trailingIcon",
                modifier = Modifier.wrapContentSize(),
                tint = color,
            )
        }
    }
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
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = { showDialog = false },
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
        Button(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            enabled = enabled,
            onClick = { showDialog = true },
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
        items(ConfigRoute.entries) { NavCard(it.title, enabled = enabled) { onRouteClick(it) } }

        item { PreferenceCategory(stringResource(R.string.module_settings)) }
        items(ModuleRoute.entries) { NavCard(it.title, enabled = enabled) { onRouteClick(it) } }

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
