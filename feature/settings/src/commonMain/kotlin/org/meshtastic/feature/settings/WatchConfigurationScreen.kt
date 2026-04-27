/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.mirror_notifications
import org.meshtastic.core.resources.mirror_notifications_summary
import org.meshtastic.core.resources.push_to_watch
import org.meshtastic.core.resources.push_to_watch_summary
import org.meshtastic.core.resources.sync_messages
import org.meshtastic.core.resources.sync_messages_summary
import org.meshtastic.core.resources.sync_node_list
import org.meshtastic.core.resources.sync_node_list_summary
import org.meshtastic.core.resources.sync_now
import org.meshtastic.core.resources.watch_configuration
import org.meshtastic.core.resources.watch_settings
import org.meshtastic.core.ui.component.ListItem
import org.meshtastic.core.ui.component.MainAppBar
import org.meshtastic.core.ui.component.SwitchPreference
import org.meshtastic.core.ui.component.TitledCard

@Composable
fun WatchConfigurationScreen(viewModel: WatchViewModel, onBack: () -> Unit) {
    val pushToWatchEnabled by viewModel.pushToWatchEnabled.collectAsStateWithLifecycle()
    val syncNodesEnabled by viewModel.syncNodesEnabled.collectAsStateWithLifecycle()
    val syncMessagesEnabled by viewModel.syncMessagesEnabled.collectAsStateWithLifecycle()
    val mirrorNotificationsEnabled by viewModel.mirrorNotificationsEnabled.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            MainAppBar(
                title = stringResource(Res.string.watch_configuration),
                ourNode = null,
                showNodeChip = false,
                canNavigateUp = true,
                onNavigateUp = onBack,
                actions = {},
                onClickChip = {},
            )
        },
    ) { paddingValues ->
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(paddingValues).padding(16.dp)) {
            WatchSettingsCard(
                pushToWatchEnabled = pushToWatchEnabled,
                syncNodesEnabled = syncNodesEnabled,
                syncMessagesEnabled = syncMessagesEnabled,
                mirrorNotificationsEnabled = mirrorNotificationsEnabled,
                onPushToWatchToggle = viewModel::setPushToWatchEnabled,
                onSyncNodesToggle = viewModel::setSyncNodesEnabled,
                onSyncMessagesToggle = viewModel::setSyncMessagesEnabled,
                onMirrorNotificationsToggle = viewModel::setMirrorNotificationsEnabled,
                onSyncClick = viewModel::requestSync,
            )
        }
    }
}

@Composable
private fun WatchSettingsCard(
    pushToWatchEnabled: Boolean,
    syncNodesEnabled: Boolean,
    syncMessagesEnabled: Boolean,
    mirrorNotificationsEnabled: Boolean,
    onPushToWatchToggle: (Boolean) -> Unit,
    onSyncNodesToggle: (Boolean) -> Unit,
    onSyncMessagesToggle: (Boolean) -> Unit,
    onMirrorNotificationsToggle: (Boolean) -> Unit,
    onSyncClick: () -> Unit,
) {
    TitledCard(title = stringResource(Res.string.watch_settings)) {
        SwitchPreference(
            title = stringResource(Res.string.push_to_watch),
            summary = stringResource(Res.string.push_to_watch_summary),
            checked = pushToWatchEnabled,
            enabled = true,
            onCheckedChange = onPushToWatchToggle,
            containerColor = CardDefaults.cardColors().containerColor,
        )

        if (pushToWatchEnabled) {
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            SwitchPreference(
                title = stringResource(Res.string.sync_node_list),
                summary = stringResource(Res.string.sync_node_list_summary),
                checked = syncNodesEnabled,
                enabled = true,
                onCheckedChange = onSyncNodesToggle,
                containerColor = CardDefaults.cardColors().containerColor,
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            SwitchPreference(
                title = stringResource(Res.string.sync_messages),
                summary = stringResource(Res.string.sync_messages_summary),
                checked = syncMessagesEnabled,
                enabled = true,
                onCheckedChange = onSyncMessagesToggle,
                containerColor = CardDefaults.cardColors().containerColor,
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            SwitchPreference(
                title = stringResource(Res.string.mirror_notifications),
                summary = stringResource(Res.string.mirror_notifications_summary),
                checked = mirrorNotificationsEnabled,
                enabled = true,
                onCheckedChange = onMirrorNotificationsToggle,
                containerColor = CardDefaults.cardColors().containerColor,
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            ListItem(
                text = stringResource(Res.string.sync_now),
                onClick = onSyncClick
            )
        }
    }
}
