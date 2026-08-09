/*
 * Copyright (c) 2026 Meshtastic LLC
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
package org.meshtastic.feature.connections.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.meshtastic.core.navigation.ConnectionsRoute
import org.meshtastic.core.navigation.NodesRoute
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.cancel
import org.meshtastic.core.resources.connect
import org.meshtastic.core.resources.deep_link_connect_message
import org.meshtastic.core.resources.deep_link_connect_title
import org.meshtastic.core.resources.deep_link_disconnect_message
import org.meshtastic.core.resources.deep_link_disconnect_title
import org.meshtastic.core.resources.disconnect
import org.meshtastic.core.ui.component.MeshtasticDialog
import org.meshtastic.feature.connections.NO_DEVICE_SELECTED
import org.meshtastic.feature.connections.ScannerViewModel
import org.meshtastic.feature.connections.ui.ConnectionsScreen
import org.meshtastic.feature.settings.radio.RadioConfigViewModel

/** Navigation graph for for the top level ConnectionsScreen - [ConnectionsRoute.Connections]. */
fun EntryProviderScope<NavKey>.connectionsGraph(backStack: NavBackStack<NavKey>) {
    entry<ConnectionsRoute.Connections> { key ->
        val scanModel = koinViewModel<ScannerViewModel>()

        // A deep link (e.g. from AI/automation tooling) may name a device address, or `n` to disconnect. The
        // `connections` path is a verified https://meshtastic.org app link, so any web page can fire one at us —
        // always confirm before re-pointing or dropping the radio connection.
        var pendingAddress by rememberSaveable(key.address) { mutableStateOf(key.address?.takeIf(String::isNotBlank)) }

        pendingAddress?.let { address ->
            val isDisconnect = address == NO_DEVICE_SELECTED
            MeshtasticDialog(
                titleRes =
                if (isDisconnect) Res.string.deep_link_disconnect_title else Res.string.deep_link_connect_title,
                message =
                if (isDisconnect) {
                    stringResource(Res.string.deep_link_disconnect_message)
                } else {
                    stringResource(Res.string.deep_link_connect_message, address)
                },
                confirmTextRes = if (isDisconnect) Res.string.disconnect else Res.string.connect,
                onConfirm = {
                    if (isDisconnect) scanModel.disconnect() else scanModel.changeDeviceAddress(address)
                    pendingAddress = null
                },
                dismissTextRes = Res.string.cancel,
                onDismiss = { pendingAddress = null },
            )
        }

        ConnectionsScreen(
            scanModel = scanModel,
            radioConfigViewModel = koinViewModel<RadioConfigViewModel>(),
            onClickNodeChip = { id -> backStack.add(NodesRoute.NodeDetail(id)) },
            onNavigateToNodeDetails = { id -> backStack.add(NodesRoute.NodeDetail(id)) },
            onConfigNavigate = { route -> backStack.add(route) },
        )
    }
}
