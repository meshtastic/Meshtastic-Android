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

package com.geeksville.mesh.navigation

import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.geeksville.mesh.ui.Route
import com.geeksville.mesh.ui.radioconfig.RadioConfigScreen
import com.geeksville.mesh.ui.radioconfig.RadioConfigViewModel
import com.geeksville.mesh.ui.radioconfig.components.AmbientLightingConfigScreen
import com.geeksville.mesh.ui.radioconfig.components.AudioConfigScreen
import com.geeksville.mesh.ui.radioconfig.components.BluetoothConfigScreen
import com.geeksville.mesh.ui.radioconfig.components.CannedMessageConfigScreen
import com.geeksville.mesh.ui.radioconfig.components.ChannelConfigScreen
import com.geeksville.mesh.ui.radioconfig.components.DetectionSensorConfigScreen
import com.geeksville.mesh.ui.radioconfig.components.DeviceConfigScreen
import com.geeksville.mesh.ui.radioconfig.components.DisplayConfigScreen
import com.geeksville.mesh.ui.radioconfig.components.ExternalNotificationConfigScreen
import com.geeksville.mesh.ui.radioconfig.components.LoRaConfigScreen
import com.geeksville.mesh.ui.radioconfig.components.MQTTConfigScreen
import com.geeksville.mesh.ui.radioconfig.components.NeighborInfoConfigScreen
import com.geeksville.mesh.ui.radioconfig.components.NetworkConfigScreen
import com.geeksville.mesh.ui.radioconfig.components.PaxcounterConfigScreen
import com.geeksville.mesh.ui.radioconfig.components.PositionConfigScreen
import com.geeksville.mesh.ui.radioconfig.components.PowerConfigScreen
import com.geeksville.mesh.ui.radioconfig.components.RangeTestConfigScreen
import com.geeksville.mesh.ui.radioconfig.components.RemoteHardwareConfigScreen
import com.geeksville.mesh.ui.radioconfig.components.SecurityConfigScreen
import com.geeksville.mesh.ui.radioconfig.components.SerialConfigScreen
import com.geeksville.mesh.ui.radioconfig.components.StoreForwardConfigScreen
import com.geeksville.mesh.ui.radioconfig.components.TelemetryConfigScreen
import com.geeksville.mesh.ui.radioconfig.components.UserConfigScreen

@Suppress("LongMethod")
fun NavGraphBuilder.addRadioConfigSection(navController: NavController) {
    composable<Route.RadioConfig> {
        RadioConfigScreen(
            onNavigate = navController::navigate,
        )
    }
    composable<Route.User> {
        val parentEntry = remember { navController.getBackStackEntry<Route.RadioConfig>() }
        UserConfigScreen(
            viewModel = hiltViewModel<RadioConfigViewModel>(parentEntry),
        )
    }
    composable<Route.ChannelConfig> {
        val parentEntry = remember { navController.getBackStackEntry<Route.RadioConfig>() }
        ChannelConfigScreen(
            viewModel = hiltViewModel<RadioConfigViewModel>(parentEntry),
        )
    }
    composable<Route.Device> {
        val parentEntry = remember { navController.getBackStackEntry<Route.RadioConfig>() }
        DeviceConfigScreen(
            viewModel = hiltViewModel<RadioConfigViewModel>(parentEntry),
        )
    }
    composable<Route.Position> {
        val parentEntry = remember { navController.getBackStackEntry<Route.RadioConfig>() }
        PositionConfigScreen(
            viewModel = hiltViewModel<RadioConfigViewModel>(parentEntry),
        )
    }
    composable<Route.Power> {
        val parentEntry = remember { navController.getBackStackEntry<Route.RadioConfig>() }
        PowerConfigScreen(
            viewModel = hiltViewModel<RadioConfigViewModel>(parentEntry),
        )
    }
    composable<Route.Network> {
        val parentEntry = remember { navController.getBackStackEntry<Route.RadioConfig>() }
        NetworkConfigScreen(
            viewModel = hiltViewModel<RadioConfigViewModel>(parentEntry),
        )
    }
    composable<Route.Display> {
        val parentEntry = remember { navController.getBackStackEntry<Route.RadioConfig>() }
        DisplayConfigScreen(
            viewModel = hiltViewModel<RadioConfigViewModel>(parentEntry),
        )
    }
    composable<Route.LoRa> {
        val parentEntry = remember { navController.getBackStackEntry<Route.RadioConfig>() }
        LoRaConfigScreen(
            viewModel = hiltViewModel<RadioConfigViewModel>(parentEntry),
        )
    }
    composable<Route.Bluetooth> {
        val parentEntry = remember { navController.getBackStackEntry<Route.RadioConfig>() }
        BluetoothConfigScreen(
            viewModel = hiltViewModel<RadioConfigViewModel>(parentEntry),
        )
    }
    composable<Route.Security> {
        val parentEntry = remember { navController.getBackStackEntry<Route.RadioConfig>() }
        SecurityConfigScreen(
            viewModel = hiltViewModel<RadioConfigViewModel>(parentEntry),
        )
    }
    composable<Route.MQTT> {
        val parentEntry = remember { navController.getBackStackEntry<Route.RadioConfig>() }
        MQTTConfigScreen(
            viewModel = hiltViewModel<RadioConfigViewModel>(parentEntry),
        )
    }
    composable<Route.Serial> {
        val parentEntry = remember { navController.getBackStackEntry<Route.RadioConfig>() }
        SerialConfigScreen(
            viewModel = hiltViewModel<RadioConfigViewModel>(parentEntry),
        )
    }
    composable<Route.ExtNotification> {
        val parentEntry = remember { navController.getBackStackEntry<Route.RadioConfig>() }
        ExternalNotificationConfigScreen(
            viewModel = hiltViewModel<RadioConfigViewModel>(parentEntry),
        )
    }
    composable<Route.StoreForward> {
        val parentEntry = remember { navController.getBackStackEntry<Route.RadioConfig>() }
        StoreForwardConfigScreen(
            viewModel = hiltViewModel<RadioConfigViewModel>(parentEntry),
        )
    }
    composable<Route.RangeTest> {
        val parentEntry = remember { navController.getBackStackEntry<Route.RadioConfig>() }
        RangeTestConfigScreen(
            viewModel = hiltViewModel<RadioConfigViewModel>(parentEntry),
        )
    }
    composable<Route.Telemetry> {
        val parentEntry = remember { navController.getBackStackEntry<Route.RadioConfig>() }
        TelemetryConfigScreen(
            viewModel = hiltViewModel<RadioConfigViewModel>(parentEntry),
        )
    }
    composable<Route.CannedMessage> {
        val parentEntry = remember { navController.getBackStackEntry<Route.RadioConfig>() }
        CannedMessageConfigScreen(
            viewModel = hiltViewModel<RadioConfigViewModel>(parentEntry),
        )
    }
    composable<Route.Audio> {
        val parentEntry = remember { navController.getBackStackEntry<Route.RadioConfig>() }
        AudioConfigScreen(
            viewModel = hiltViewModel<RadioConfigViewModel>(parentEntry),
        )
    }
    composable<Route.RemoteHardware> {
        val parentEntry = remember { navController.getBackStackEntry<Route.RadioConfig>() }
        RemoteHardwareConfigScreen(
            viewModel = hiltViewModel<RadioConfigViewModel>(parentEntry),
        )
    }
    composable<Route.NeighborInfo> {
        val parentEntry = remember { navController.getBackStackEntry<Route.RadioConfig>() }
        NeighborInfoConfigScreen(
            viewModel = hiltViewModel<RadioConfigViewModel>(parentEntry),
        )
    }
    composable<Route.AmbientLighting> {
        val parentEntry = remember { navController.getBackStackEntry<Route.RadioConfig>() }
        AmbientLightingConfigScreen(
            viewModel = hiltViewModel<RadioConfigViewModel>(parentEntry),
        )
    }
    composable<Route.DetectionSensor> {
        val parentEntry = remember { navController.getBackStackEntry<Route.RadioConfig>() }
        DetectionSensorConfigScreen(
            viewModel = hiltViewModel<RadioConfigViewModel>(parentEntry),
        )
    }
    composable<Route.Paxcounter> {
        val parentEntry = remember { navController.getBackStackEntry<Route.RadioConfig>() }
        PaxcounterConfigScreen(
            viewModel = hiltViewModel<RadioConfigViewModel>(parentEntry),
        )
    }
}
