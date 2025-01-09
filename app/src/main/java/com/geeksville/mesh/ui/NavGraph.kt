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

package com.geeksville.mesh.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.geeksville.mesh.R
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.model.MetricsViewModel
import com.geeksville.mesh.navigation.addRadioConfigSection
import com.geeksville.mesh.ui.components.BaseScaffold
import com.geeksville.mesh.ui.components.DeviceMetricsScreen
import com.geeksville.mesh.ui.components.EnvironmentMetricsScreen
import com.geeksville.mesh.ui.components.NodeMapScreen
import com.geeksville.mesh.ui.components.PositionLogScreen
import com.geeksville.mesh.ui.components.SignalMetricsScreen
import com.geeksville.mesh.ui.components.TracerouteLogScreen
import com.geeksville.mesh.ui.radioconfig.RadioConfigViewModel
import com.geeksville.mesh.ui.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.serialization.Serializable

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
        @Suppress("DEPRECATION")
        val destNum = arguments?.getSerializable("destNum") as? Int
        val startDestination: Any = when (arguments?.getString("startDestination")) {
            "NodeDetails" -> Route.NodeDetail(destNum!!)
            else -> Route.RadioConfig(destNum)
        }

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val node by model.destNode.collectAsStateWithLifecycle()

                AppTheme {
                    val navController: NavHostController = rememberNavController()
                    BaseScaffold(
                        title = node?.user?.longName
                            ?: stringResource(R.string.unknown_username),
                        canNavigateBack = true,
                        navigateUp = {
                            if (navController.previousBackStackEntry != null) {
                                navController.navigateUp()
                            } else {
                                parentFragmentManager.popBackStack()
                            }
                        },
                    ) {
                        NavGraph(
                            navController = navController,
                            startDestination = startDestination,
                        )
                    }
                }
            }
        }
    }
}

sealed interface Route {
    @Serializable
    data class Messages(val contactKey: String, val message: String = "") : Route
    @Serializable
    data class Share(val message: String) : Route

    @Serializable
    data class RadioConfig(val destNum: Int? = null) : Route
    @Serializable data object User : Route
    @Serializable data object ChannelConfig : Route
    @Serializable data object Device : Route
    @Serializable data object Position : Route
    @Serializable data object Power : Route
    @Serializable data object Network : Route
    @Serializable data object Display : Route
    @Serializable data object LoRa : Route
    @Serializable data object Bluetooth : Route
    @Serializable data object Security : Route

    @Serializable data object MQTT : Route
    @Serializable data object Serial : Route
    @Serializable data object ExtNotification : Route
    @Serializable data object StoreForward : Route
    @Serializable data object RangeTest : Route
    @Serializable data object Telemetry : Route
    @Serializable data object CannedMessage : Route
    @Serializable data object Audio : Route
    @Serializable data object RemoteHardware : Route
    @Serializable data object NeighborInfo : Route
    @Serializable data object AmbientLighting : Route
    @Serializable data object DetectionSensor : Route
    @Serializable data object Paxcounter : Route

    @Serializable
    data class NodeDetail(val destNum: Int) : Route
    @Serializable data object DeviceMetrics : Route
    @Serializable data object NodeMap : Route
    @Serializable data object PositionLog : Route
    @Serializable data object EnvironmentMetrics : Route
    @Serializable data object SignalMetrics : Route
    @Serializable data object TracerouteLog : Route
}

@Composable
fun NavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: Any,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable<Route.NodeDetail> {
            NodeDetailScreen { navController.navigate(route = it) }
        }
        composable<Route.DeviceMetrics> {
            val parentEntry = remember { navController.getBackStackEntry<Route.NodeDetail>() }
            DeviceMetricsScreen(hiltViewModel<MetricsViewModel>(parentEntry))
        }
        composable<Route.NodeMap> {
            val parentEntry = remember { navController.getBackStackEntry<Route.NodeDetail>() }
            NodeMapScreen(hiltViewModel<MetricsViewModel>(parentEntry))
        }
        composable<Route.PositionLog> {
            val parentEntry = remember { navController.getBackStackEntry<Route.NodeDetail>() }
            PositionLogScreen(hiltViewModel<MetricsViewModel>(parentEntry))
        }
        composable<Route.EnvironmentMetrics> {
            val parentEntry = remember { navController.getBackStackEntry<Route.NodeDetail>() }
            EnvironmentMetricsScreen(hiltViewModel<MetricsViewModel>(parentEntry))
        }
        composable<Route.SignalMetrics> {
            val parentEntry = remember { navController.getBackStackEntry<Route.NodeDetail>() }
            SignalMetricsScreen(hiltViewModel<MetricsViewModel>(parentEntry))
        }
        composable<Route.TracerouteLog> {
            val parentEntry = remember { navController.getBackStackEntry<Route.NodeDetail>() }
            TracerouteLogScreen(hiltViewModel<MetricsViewModel>(parentEntry))
        }
        addRadioConfigSection(navController)
        composable<Route.Share> { backStackEntry ->
            val message = backStackEntry.toRoute<Route.Share>().message
            ShareScreen(
                navigateUp = navController::navigateUp,
            ) {
                navController.navigate(Route.Messages(it, message)) {
                    popUpTo<Route.Share> { inclusive = true }
                }
            }
        }
    }
}
