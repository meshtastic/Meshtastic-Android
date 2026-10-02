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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.meshtastic.core.common.state.LaunchOptions
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

/** Navigation graph for for the top level ConnectionsScreen - [ConnectionsRoute.Connections]. */
fun EntryProviderScope<NavKey>.connectionsGraph(backStack: NavBackStack<NavKey>) {
    entry<ConnectionsRoute.Connections> { key ->
        val scanModel = koinViewModel<ScannerViewModel>()

        // A deep link (e.g. from AI/automation tooling) may name a device address, or `n` to disconnect. The
        // `connections` path is a verified https://meshtastic.org app link, so any web page can fire one at us —
        // confirm before re-pointing or dropping the radio connection. Only a debug launch switch skips it.
        var pendingAddress by rememberSaveable(key.address) { mutableStateOf(key.address?.takeIf(String::isNotBlank)) }
        val launchOptions = koinInject<LaunchOptions>()

        pendingAddress?.let { address ->
            DeepLinkConnectPrompt(
                address = address,
                skipConfirmation = launchOptions.skipDeepLinkConfirmation,
                onApply = {
                    if (it == NO_DEVICE_SELECTED) scanModel.disconnect() else scanModel.changeDeviceAddress(it)
                },
                onDone = { pendingAddress = null },
            )
        }

        ConnectionsScreen(
            scanModel = scanModel,
            onClickNodeChip = { id -> backStack.add(NodesRoute.NodeDetail(id)) },
            onNavigateToNodeDetails = { id -> backStack.add(NodesRoute.NodeDetail(id)) },
            onConfigNavigate = { route -> backStack.add(route) },
        )
    }
}

/**
 * The trust step for a `connections` deep link. The address is applied only once the user confirms, unless
 * [skipConfirmation], a debug launch switch, says to apply it straight away.
 */
@Composable
internal fun DeepLinkConnectPrompt(
    address: String,
    skipConfirmation: Boolean,
    onApply: (String) -> Unit,
    onDone: () -> Unit,
) {
    if (skipConfirmation) {
        val apply by rememberUpdatedState(onApply)
        val done by rememberUpdatedState(onDone)
        LaunchedEffect(address) {
            apply(address)
            done()
        }
    } else {
        val isDisconnect = address == NO_DEVICE_SELECTED
        MeshtasticDialog(
            titleRes = if (isDisconnect) Res.string.deep_link_disconnect_title else Res.string.deep_link_connect_title,
            message =
            if (isDisconnect) {
                stringResource(Res.string.deep_link_disconnect_message)
            } else {
                stringResource(Res.string.deep_link_connect_message, address)
            },
            confirmTextRes = if (isDisconnect) Res.string.disconnect else Res.string.connect,
            onConfirm = {
                onApply(address)
                onDone()
            },
            dismissTextRes = Res.string.cancel,
            onDismiss = onDone,
        )
    }
}
