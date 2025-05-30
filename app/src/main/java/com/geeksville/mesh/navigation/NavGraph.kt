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

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import androidx.navigation.toRoute
import com.geeksville.mesh.R
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.ui.TopLevelDestination.Companion.isTopLevel
import com.geeksville.mesh.ui.contact.ContactsScreen
import com.geeksville.mesh.ui.debug.DebugScreen
import com.geeksville.mesh.ui.map.MapView
import com.geeksville.mesh.ui.message.MessageScreen
import com.geeksville.mesh.ui.message.QuickChatScreen
import com.geeksville.mesh.ui.node.NodeScreen
import com.geeksville.mesh.ui.settings.SettingsScreen
import com.geeksville.mesh.ui.sharing.ShareScreen
import kotlinx.serialization.Serializable

enum class AdminRoute(@StringRes val title: Int) {
    REBOOT(R.string.reboot),
    SHUTDOWN(R.string.shutdown),
    FACTORY_RESET(R.string.factory_reset),
    NODEDB_RESET(R.string.nodedb_reset),
}

const val DEEP_LINK_BASE_URI = "meshtastic://meshtastic"

@Serializable
sealed interface Graph : Route {
    @Serializable
    data class ChannelsGraph(val destNum: Int?)

    @Serializable
    data class NodeDetailGraph(val destNum: Int) : Graph

    @Serializable
    data class RadioConfigGraph(val destNum: Int? = null) : Graph
}

@Serializable
sealed interface Route {
    @Serializable
    data object Contacts : Route

    @Serializable
    data object Nodes : Route

    @Serializable
    data object Map : Route

    @Serializable
    data object Channels : Route

    @Serializable
    data object Settings : Route

    @Serializable
    data object DebugPanel : Route

    @Serializable
    data class Messages(val contactKey: String, val message: String = "") : Route

    @Serializable
    data object QuickChat : Route

    @Serializable
    data class Share(val message: String) : Route

    @Serializable
    data class RadioConfig(val destNum: Int? = null) : Route

    @Serializable
    data object User : Route

    @Serializable
    data object ChannelConfig : Route

    @Serializable
    data object Device : Route

    @Serializable
    data object Position : Route

    @Serializable
    data object Power : Route

    @Serializable
    data object Network : Route

    @Serializable
    data object Display : Route

    @Serializable
    data object LoRa : Route

    @Serializable
    data object Bluetooth : Route

    @Serializable
    data object Security : Route

    @Serializable
    data object MQTT : Route

    @Serializable
    data object Serial : Route

    @Serializable
    data object ExtNotification : Route

    @Serializable
    data object StoreForward : Route

    @Serializable
    data object RangeTest : Route

    @Serializable
    data object Telemetry : Route

    @Serializable
    data object CannedMessage : Route

    @Serializable
    data object Audio : Route

    @Serializable
    data object RemoteHardware : Route

    @Serializable
    data object NeighborInfo : Route

    @Serializable
    data object AmbientLighting : Route

    @Serializable
    data object DetectionSensor : Route

    @Serializable
    data object Paxcounter : Route

    @Serializable
    data class NodeDetail(val destNum: Int? = null) : Route

    @Serializable
    data object DeviceMetrics : Route

    @Serializable
    data object NodeMap : Route

    @Serializable
    data object PositionLog : Route

    @Serializable
    data object EnvironmentMetrics : Route

    @Serializable
    data object SignalMetrics : Route

    @Serializable
    data object PowerMetrics : Route

    @Serializable
    data object TracerouteLog : Route

    @Serializable
    data object HostMetricsLog : Route
}

fun NavDestination.isConfigRoute(): Boolean {
    return ConfigRoute.entries.any { hasRoute(it.route::class) } ||
            ModuleRoute.entries.any { hasRoute(it.route::class) }
}

fun NavDestination.isNodeDetailRoute(): Boolean {
    return NodeDetailRoute.entries.any { hasRoute(it.route::class) }
}

fun NavDestination.showLongNameTitle(): Boolean {

    return !this.isTopLevel() && (
            this.hasRoute<Route.RadioConfig>() ||
                    this.hasRoute<Route.NodeDetail>() ||
                    this.isConfigRoute() ||
                    this.isNodeDetailRoute()
            )
}

@Suppress("LongMethod")
@Composable
fun NavGraph(
    modifier: Modifier = Modifier,
    uIViewModel: UIViewModel = hiltViewModel(),
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = if (uIViewModel.bondedAddress.isNullOrBlank()) {
            Route.Settings
        } else {
            Route.Contacts
        },
        modifier = modifier,
    ) {
        composable<Route.Contacts> {
            ContactsScreen(
                uIViewModel,
                onNavigate = { navController.navigate(Route.Messages(it)) }
            )
        }
        composable<Route.Nodes> {
            NodeScreen(
                model = uIViewModel,
                navigateToMessages = { navController.navigate(Route.Messages(it)) },
                navigateToNodeDetails = { navController.navigate(Route.NodeDetail(it)) },
            )
        }
        composable<Route.Map> {
            MapView(uIViewModel)
        }

        channelsGraph(navController, uIViewModel)

        composable<Route.Settings>(
            deepLinks = listOf(
                navDeepLink {
                    uriPattern = "$DEEP_LINK_BASE_URI/settings"
                    action = "android.intent.action.VIEW"
                }
            )
        ) { backStackEntry ->
            SettingsScreen(
                uIViewModel,
                onNavigateToRadioConfig = {
                    navController.navigate(Route.RadioConfig()) {
                        popUpTo(Route.Settings) {
                            inclusive = false
                        }
                    }
                },
                onNavigateToNodeDetails = { navController.navigate(Route.NodeDetail(it)) }
            )
        }
        composable<Route.DebugPanel> {
            DebugScreen()
        }
        composable<Route.Messages>(
            deepLinks = listOf(
                navDeepLink {
                    uriPattern = "$DEEP_LINK_BASE_URI/messages/{contactKey}?message={message}"
                    action = "android.intent.action.VIEW"
                },
            )
        ) { backStackEntry ->
            val args = backStackEntry.toRoute<Route.Messages>()
            MessageScreen(
                contactKey = args.contactKey,
                message = args.message,
                viewModel = uIViewModel,
                navigateToMessages = { navController.navigate(Route.Messages(it)) },
                navigateToNodeDetails = { navController.navigate(Route.NodeDetail(it)) },
                onNavigateBack = navController::navigateUp,
            )
        }
        composable<Route.QuickChat> {
            QuickChatScreen()
        }
        nodeDetailGraph(
            navController,
            uIViewModel,
        )
        radioConfigGraph(navController, uIViewModel)
        composable<Route.Share>(
            deepLinks = listOf(
                navDeepLink {
                    uriPattern = "$DEEP_LINK_BASE_URI/share?message={message}"
                    action = "android.intent.action.VIEW"
                }
            )
        ) { backStackEntry ->
            val message = backStackEntry.toRoute<Route.Share>().message
            ShareScreen(uIViewModel) {
                navController.navigate(Route.Messages(it, message)) {
                    popUpTo<Route.Share> { inclusive = true }
                }
            }
        }
    }
}
