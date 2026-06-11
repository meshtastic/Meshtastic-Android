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
package org.meshtastic.feature.settings.appfunctions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.app_functions_get_channel_info
import org.meshtastic.core.resources.app_functions_get_device_status
import org.meshtastic.core.resources.app_functions_get_mesh_metrics
import org.meshtastic.core.resources.app_functions_get_mesh_status
import org.meshtastic.core.resources.app_functions_get_node_details
import org.meshtastic.core.resources.app_functions_get_node_list
import org.meshtastic.core.resources.app_functions_get_recent_messages
import org.meshtastic.core.resources.app_functions_get_unread_summary
import org.meshtastic.core.resources.app_functions_master_summary
import org.meshtastic.core.resources.app_functions_master_toggle
import org.meshtastic.core.resources.app_functions_read_section
import org.meshtastic.core.resources.app_functions_send_message
import org.meshtastic.core.resources.app_functions_settings
import org.meshtastic.core.resources.app_functions_write_section
import org.meshtastic.core.ui.component.MainAppBar
import org.meshtastic.core.ui.component.SwitchListItem
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.SettingsRemote
import org.meshtastic.core.ui.theme.AppTheme

@Composable
fun AppFunctionsSettingsScreen(
    viewModel: AppFunctionsSettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val masterEnabled by viewModel.masterEnabled.collectAsStateWithLifecycle()
    val sendMessage by viewModel.sendMessageEnabled.collectAsStateWithLifecycle()
    val getMeshStatus by viewModel.getMeshStatusEnabled.collectAsStateWithLifecycle()
    val getNodeList by viewModel.getNodeListEnabled.collectAsStateWithLifecycle()
    val getChannelInfo by viewModel.getChannelInfoEnabled.collectAsStateWithLifecycle()
    val getDeviceStatus by viewModel.getDeviceStatusEnabled.collectAsStateWithLifecycle()
    val getNodeDetails by viewModel.getNodeDetailsEnabled.collectAsStateWithLifecycle()
    val getMeshMetrics by viewModel.getMeshMetricsEnabled.collectAsStateWithLifecycle()
    val getRecentMessages by viewModel.getRecentMessagesEnabled.collectAsStateWithLifecycle()
    val getUnreadSummary by viewModel.getUnreadSummaryEnabled.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            MainAppBar(
                title = stringResource(Res.string.app_functions_settings),
                canNavigateUp = true,
                onNavigateUp = onBack,
                ourNode = null,
                showNodeChip = false,
                actions = {},
                onClickChip = {},
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            MasterToggleSection(
                masterEnabled = masterEnabled,
                onToggle = { viewModel.setMasterEnabled(!masterEnabled) },
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            WriteFunctionsSection(
                masterEnabled = masterEnabled,
                sendMessage = sendMessage,
                onToggleSendMessage = { viewModel.setSendMessageEnabled(!sendMessage) },
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            ReadFunctionsSection(
                masterEnabled = masterEnabled,
                getMeshStatus = getMeshStatus,
                onToggleMeshStatus = { viewModel.setGetMeshStatusEnabled(!getMeshStatus) },
                getNodeList = getNodeList,
                onToggleNodeList = { viewModel.setGetNodeListEnabled(!getNodeList) },
                getChannelInfo = getChannelInfo,
                onToggleChannelInfo = { viewModel.setGetChannelInfoEnabled(!getChannelInfo) },
                getDeviceStatus = getDeviceStatus,
                onToggleDeviceStatus = { viewModel.setGetDeviceStatusEnabled(!getDeviceStatus) },
                getNodeDetails = getNodeDetails,
                onToggleNodeDetails = { viewModel.setGetNodeDetailsEnabled(!getNodeDetails) },
                getMeshMetrics = getMeshMetrics,
                onToggleMeshMetrics = { viewModel.setGetMeshMetricsEnabled(!getMeshMetrics) },
                getRecentMessages = getRecentMessages,
                onToggleRecentMessages = { viewModel.setGetRecentMessagesEnabled(!getRecentMessages) },
                getUnreadSummary = getUnreadSummary,
                onToggleUnreadSummary = { viewModel.setGetUnreadSummaryEnabled(!getUnreadSummary) },
            )
        }
    }
}

@Composable
private fun MasterToggleSection(masterEnabled: Boolean, onToggle: () -> Unit) {
    SwitchListItem(
        text = stringResource(Res.string.app_functions_master_toggle),
        checked = masterEnabled,
        leadingIcon = MeshtasticIcons.SettingsRemote,
        onClick = onToggle,
    )
    Text(
        text = stringResource(Res.string.app_functions_master_summary),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 56.dp, end = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun WriteFunctionsSection(masterEnabled: Boolean, sendMessage: Boolean, onToggleSendMessage: () -> Unit) {
    Text(
        text = stringResource(Res.string.app_functions_write_section),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
    )
    SwitchListItem(
        text = stringResource(Res.string.app_functions_send_message),
        checked = sendMessage,
        enabled = masterEnabled,
        onClick = onToggleSendMessage,
    )
}

@Suppress("LongParameterList")
@Composable
private fun ReadFunctionsSection(
    masterEnabled: Boolean,
    getMeshStatus: Boolean,
    onToggleMeshStatus: () -> Unit,
    getNodeList: Boolean,
    onToggleNodeList: () -> Unit,
    getChannelInfo: Boolean,
    onToggleChannelInfo: () -> Unit,
    getDeviceStatus: Boolean,
    onToggleDeviceStatus: () -> Unit,
    getNodeDetails: Boolean,
    onToggleNodeDetails: () -> Unit,
    getMeshMetrics: Boolean,
    onToggleMeshMetrics: () -> Unit,
    getRecentMessages: Boolean,
    onToggleRecentMessages: () -> Unit,
    getUnreadSummary: Boolean,
    onToggleUnreadSummary: () -> Unit,
) {
    Text(
        text = stringResource(Res.string.app_functions_read_section),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
    )
    SwitchListItem(
        text = stringResource(Res.string.app_functions_get_mesh_status),
        checked = getMeshStatus,
        enabled = masterEnabled,
        onClick = onToggleMeshStatus,
    )
    SwitchListItem(
        text = stringResource(Res.string.app_functions_get_node_list),
        checked = getNodeList,
        enabled = masterEnabled,
        onClick = onToggleNodeList,
    )
    SwitchListItem(
        text = stringResource(Res.string.app_functions_get_channel_info),
        checked = getChannelInfo,
        enabled = masterEnabled,
        onClick = onToggleChannelInfo,
    )
    SwitchListItem(
        text = stringResource(Res.string.app_functions_get_device_status),
        checked = getDeviceStatus,
        enabled = masterEnabled,
        onClick = onToggleDeviceStatus,
    )
    SwitchListItem(
        text = stringResource(Res.string.app_functions_get_node_details),
        checked = getNodeDetails,
        enabled = masterEnabled,
        onClick = onToggleNodeDetails,
    )
    SwitchListItem(
        text = stringResource(Res.string.app_functions_get_mesh_metrics),
        checked = getMeshMetrics,
        enabled = masterEnabled,
        onClick = onToggleMeshMetrics,
    )
    SwitchListItem(
        text = stringResource(Res.string.app_functions_get_recent_messages),
        checked = getRecentMessages,
        enabled = masterEnabled,
        onClick = onToggleRecentMessages,
    )
    SwitchListItem(
        text = stringResource(Res.string.app_functions_get_unread_summary),
        checked = getUnreadSummary,
        enabled = masterEnabled,
        onClick = onToggleUnreadSummary,
    )
}

@PreviewLightDark
@Suppress("PreviewPublic") // public so :screenshot-tests can reference it
@Composable
fun PreviewAppFunctionsSettings() {
    AppTheme {
        Surface {
            Column {
                MasterToggleSection(masterEnabled = true, onToggle = {})
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                WriteFunctionsSection(masterEnabled = true, sendMessage = false, onToggleSendMessage = {})
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                ReadFunctionsSection(
                    masterEnabled = true,
                    getMeshStatus = true,
                    onToggleMeshStatus = {},
                    getNodeList = true,
                    onToggleNodeList = {},
                    getChannelInfo = true,
                    onToggleChannelInfo = {},
                    getDeviceStatus = true,
                    onToggleDeviceStatus = {},
                    getNodeDetails = true,
                    onToggleNodeDetails = {},
                    getMeshMetrics = true,
                    onToggleMeshMetrics = {},
                    getRecentMessages = false,
                    onToggleRecentMessages = {},
                    getUnreadSummary = true,
                    onToggleUnreadSummary = {},
                )
            }
        }
    }
}
