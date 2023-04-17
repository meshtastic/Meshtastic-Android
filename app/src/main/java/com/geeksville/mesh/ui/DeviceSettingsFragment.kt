package com.geeksville.mesh.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.ViewCompositionStrategy
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
import com.geeksville.mesh.R
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.service.MeshService
import com.geeksville.mesh.ui.components.PreferenceCategory
import com.geeksville.mesh.ui.components.config.AudioConfigItemList
import com.geeksville.mesh.ui.components.config.BluetoothConfigItemList
import com.geeksville.mesh.ui.components.config.CannedMessageConfigItemList
import com.geeksville.mesh.ui.components.config.DeviceConfigItemList
import com.geeksville.mesh.ui.components.config.DisplayConfigItemList
import com.geeksville.mesh.ui.components.config.ExternalNotificationConfigItemList
import com.geeksville.mesh.ui.components.config.LoRaConfigItemList
import com.geeksville.mesh.ui.components.config.MQTTConfigItemList
import com.geeksville.mesh.ui.components.config.NetworkConfigItemList
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
class DeviceSettingsFragment : ScreenFragment("Device Settings"), Logging {

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
                    RadioConfigNavHost(model)
                }
            }
        }
    }
}

enum class ConfigDest(val title: String, val route: String) {
    USER("User", "user"),
    DEVICE("Device", "device"),
    POSITION("Position", "position"),
    POWER("Power", "power"),
    NETWORK("Network", "network"),
    DISPLAY("Display", "display"),
    LORA("LoRa", "lora"),
    BLUETOOTH("Bluetooth", "bluetooth")
}

enum class ModuleDest(val title: String, val route: String) {
    MQTT("MQTT", "mqtt"),
    SERIAL("Serial", "serial"),
    EXT_NOTIFICATION("External Notification", "ext_notification"),
    STORE_FORWARD("Store & Forward", "store_forward"),
    RANGE_TEST("Range Test", "range_test"),
    TELEMETRY("Telemetry", "telemetry"),
    CANNED_MESSAGE("Canned Message", "canned_message"),
    AUDIO("Audio", "audio"),
    REMOTE_HARDWARE("Remote Hardware", "remote_hardware")
}

@Composable
fun RadioConfigNavHost(viewModel: UIViewModel = viewModel()) {
    val navController = rememberNavController()
    val focusManager = LocalFocusManager.current

    val connectionState by viewModel.connectionState.observeAsState()
    val connected = connectionState == MeshService.ConnectionState.CONNECTED

    val ourNodeInfo by viewModel.ourNodeInfo.collectAsStateWithLifecycle()
    val localConfig by viewModel.localConfig.collectAsStateWithLifecycle()
    val moduleConfig by viewModel.moduleConfig.collectAsStateWithLifecycle()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") { RadioSettingsScreen(navController) }
        composable("user") {
            UserConfigItemList(
                userConfig = ourNodeInfo?.user!!,
                enabled = connected,
                focusManager = focusManager,
                onSaveClicked = { userInput ->
                    focusManager.clearFocus()
                    viewModel.setOwner(userInput)
                }
            )
        }
        composable("device") {
            DeviceConfigItemList(
                deviceConfig = localConfig.device,
                enabled = connected,
                focusManager = focusManager,
                onSaveClicked = { deviceInput ->
                    focusManager.clearFocus()
                    viewModel.updateDeviceConfig { deviceInput }
                }
            )
        }
        composable("position") {
            PositionConfigItemList(
                positionInfo = ourNodeInfo?.position,
                positionConfig = localConfig.position,
                enabled = connected,
                focusManager = focusManager,
                onSaveClicked = { positionPair ->
                    focusManager.clearFocus()
                    val (locationInput, positionInput) = positionPair
                    if (locationInput != ourNodeInfo?.position && positionInput.fixedPosition)
                        locationInput?.let { viewModel.requestPosition(0, it) }
                    if (positionInput != localConfig.position) viewModel.updatePositionConfig { positionInput }
                }
            )
        }
        composable("power") {
            PowerConfigItemList(
                powerConfig = localConfig.power,
                enabled = connected,
                focusManager = focusManager,
                onSaveClicked = { powerInput ->
                    focusManager.clearFocus()
                    viewModel.updatePowerConfig { powerInput }
                }
            )
        }
        composable("network") {
            NetworkConfigItemList(
                networkConfig = localConfig.network,
                enabled = connected,
                focusManager = focusManager,
                onSaveClicked = { networkInput ->
                    focusManager.clearFocus()
                    viewModel.updateNetworkConfig { networkInput }
                }
            )
        }
        composable("display") {
            DisplayConfigItemList(
                displayConfig = localConfig.display,
                enabled = connected,
                focusManager = focusManager,
                onSaveClicked = { displayInput ->
                    focusManager.clearFocus()
                    viewModel.updateDisplayConfig { displayInput }
                }
            )
        }
        composable("lora") {
            LoRaConfigItemList(
                loraConfig = localConfig.lora,
                enabled = connected,
                focusManager = focusManager,
                onSaveClicked = { loraInput ->
                    focusManager.clearFocus()
                    viewModel.updateLoraConfig { loraInput }
                }
            )
        }
        composable("bluetooth") {
            BluetoothConfigItemList(
                bluetoothConfig = localConfig.bluetooth,
                enabled = connected,
                focusManager = focusManager,
                onSaveClicked = { bluetoothInput ->
                    focusManager.clearFocus()
                    viewModel.updateBluetoothConfig { bluetoothInput }
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
                    viewModel.updateMQTTConfig { mqttInput }
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
                    viewModel.updateSerialConfig { serialInput }
                }
            )
        }
        composable("ext_notification") {
            ExternalNotificationConfigItemList(
                externalNotificationConfig = moduleConfig.externalNotification,
                enabled = connected,
                focusManager = focusManager,
                onSaveClicked = { externalNotificationInput ->
                    focusManager.clearFocus()
                    viewModel.updateExternalNotificationConfig { externalNotificationInput }
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
                    viewModel.updateStoreForwardConfig { storeForwardInput }
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
                    viewModel.updateRangeTestConfig { rangeTestInput }
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
                    viewModel.updateTelemetryConfig { telemetryInput }
                }
            )
        }
        composable("canned_message") {
            CannedMessageConfigItemList(
                cannedMessageConfig = moduleConfig.cannedMessage,
                enabled = connected,
                focusManager = focusManager,
                onSaveClicked = { cannedMessageInput ->
                    focusManager.clearFocus()
                    viewModel.updateCannedMessageConfig { cannedMessageInput }
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
                    viewModel.updateAudioConfig { audioInput }
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
                    viewModel.updateRemoteHardwareConfig { remoteHardwareInput }
                }
            )
        }
    }
}

@Composable
fun NavCard(title: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp, horizontal = 16.dp)
            .clickable { onClick() },
        elevation = 4.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 16.dp, horizontal = 16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.body1,
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.TwoTone.KeyboardArrowRight, "trailingIcon",
                modifier = Modifier.wrapContentSize(),
            )
        }
    }
}

@Composable
fun RadioSettingsScreen(navController: NavHostController) {
    LazyColumn {
        item {
            PreferenceCategory(
                stringResource(id = R.string.device_settings), Modifier.padding(horizontal = 16.dp)
            )
        }
        items(ConfigDest.values()) { configs ->
            NavCard(configs.title) { navController.navigate(configs.route) }
        }

        item {
            PreferenceCategory(
                stringResource(id = R.string.module_settings), Modifier.padding(horizontal = 16.dp)
            )
        }

        items(ModuleDest.values()) { modules ->
            NavCard(modules.title) { navController.navigate(modules.route) }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun RadioSettingsScreenPreview(){
    RadioSettingsScreen(NavHostController(LocalContext.current))
}
