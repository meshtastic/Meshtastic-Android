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
package org.meshtastic.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.meshtastic.core.model.Node
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.administration
import org.meshtastic.core.resources.preserve_favorites
import org.meshtastic.core.resources.remotely_administrating
import org.meshtastic.core.ui.component.ListItem
import org.meshtastic.core.ui.component.MainAppBar
import org.meshtastic.feature.settings.component.ExpressiveSection
import org.meshtastic.feature.settings.radio.AdminRoute
import org.meshtastic.feature.settings.radio.RadioConfigState
import org.meshtastic.feature.settings.radio.RadioConfigViewModel
import org.meshtastic.feature.settings.radio.ResponseState
import org.meshtastic.feature.settings.radio.component.LoadingOverlay
import org.meshtastic.feature.settings.radio.component.PacketResponseStateDialog
import org.meshtastic.feature.settings.radio.component.ShutdownConfirmationDialog
import org.meshtastic.feature.settings.radio.component.WarningDialog

@Composable
fun AdministrationScreen(viewModel: RadioConfigViewModel, onBack: () -> Unit) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()
    val destNode by viewModel.destNode.collectAsStateWithLifecycle()
    val enabled = state.connected && !state.responseState.isWaiting()

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                MainAppBar(
                    title = stringResource(Res.string.administration),
                    subtitle =
                    if (state.isLocal) {
                        destNode?.user?.long_name
                    } else {
                        val remoteName = destNode?.user?.long_name ?: ""
                        stringResource(Res.string.remotely_administrating, remoteName)
                    },
                    ourNode = null,
                    showNodeChip = false,
                    canNavigateUp = true,
                    onNavigateUp = onBack,
                    actions = {},
                    onClickChip = {},
                )
            },
        ) { paddingValues ->
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(paddingValues).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ExpressiveSection(
                    title = stringResource(Res.string.administration),
                    titleColor = MaterialTheme.colorScheme.error,
                ) {
                    AdminRouteItems(viewModel = viewModel, enabled = enabled, state = state, destNode = destNode)
                }
            }
        }

        LoadingOverlay(state = state.responseState)

        if (state.responseState is ResponseState.Success || state.responseState is ResponseState.Error) {
            PacketResponseStateDialog(
                state = state.responseState,
                onDismiss = { viewModel.clearPacketResponse() },
                onComplete = {
                    viewModel.clearPacketResponse()
                    onBack()
                },
            )
        }
    }
}

@Composable
private fun AdminRouteItems(
    viewModel: RadioConfigViewModel,
    enabled: Boolean,
    state: RadioConfigState,
    destNode: Node?,
) {
    AdminRoute.entries.forEach { route ->
        var showDialog by remember { mutableStateOf(false) }
        if (showDialog) {
            AdminActionDialog(
                route = route,
                destNode = destNode,
                enabled = enabled,
                state = state,
                onDismiss = { showDialog = false },
                onConfirm = { viewModel.setResponseStateLoading(route) },
                onPreserveFavoritesChange = { viewModel.setPreserveFavorites(it) },
            )
        }

        ListItem(
            enabled = enabled,
            text = stringResource(route.title),
            leadingIcon = vectorResource(route.icon),
            leadingIconTint = MaterialTheme.colorScheme.error,
            textColor = MaterialTheme.colorScheme.error,
            trailingIcon = null,
        ) {
            showDialog = true
        }
    }
}

@Composable
private fun AdminActionDialog(
    route: AdminRoute,
    destNode: Node?,
    enabled: Boolean,
    state: RadioConfigState,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onPreserveFavoritesChange: (Boolean) -> Unit,
) {
    if (route == AdminRoute.SHUTDOWN || route == AdminRoute.REBOOT) {
        ShutdownConfirmationDialog(
            title = "${stringResource(route.title)}?",
            node = destNode,
            onDismiss = onDismiss,
            isShutdown = route == AdminRoute.SHUTDOWN,
            onConfirm = onConfirm,
        )
    } else {
        WarningDialog(
            title = "${stringResource(route.title)}?",
            text = {
                if (route == AdminRoute.NODEDB_RESET) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(text = stringResource(Res.string.preserve_favorites))
                        Switch(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            enabled = enabled,
                            checked = state.nodeDbResetPreserveFavorites,
                            onCheckedChange = onPreserveFavoritesChange,
                        )
                    }
                }
            },
            onDismiss = onDismiss,
            onConfirm = onConfirm,
        )
    }
}
