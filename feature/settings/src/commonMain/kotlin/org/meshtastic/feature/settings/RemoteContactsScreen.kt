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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.model.effectiveLongName
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.live_update_search_nodes
import org.meshtastic.core.resources.remote_contacts
import org.meshtastic.core.resources.remote_contacts_add_contact
import org.meshtastic.core.resources.remote_contacts_explainer
import org.meshtastic.core.resources.remote_contacts_failed
import org.meshtastic.core.resources.remote_contacts_favorite
import org.meshtastic.core.resources.remote_contacts_ignore
import org.meshtastic.core.resources.remote_contacts_ignore_confirm
import org.meshtastic.core.resources.remote_contacts_invalid_url
import org.meshtastic.core.resources.remote_contacts_no_public_key
import org.meshtastic.core.resources.remote_contacts_not_sent
import org.meshtastic.core.resources.remote_contacts_pick
import org.meshtastic.core.resources.remote_contacts_remove
import org.meshtastic.core.resources.remote_contacts_remove_confirm
import org.meshtastic.core.resources.remote_contacts_self
import org.meshtastic.core.resources.remote_contacts_sending
import org.meshtastic.core.resources.remote_contacts_sent
import org.meshtastic.core.resources.remote_contacts_target
import org.meshtastic.core.resources.remote_contacts_unfavorite
import org.meshtastic.core.resources.remote_contacts_unignore
import org.meshtastic.core.resources.remote_contacts_url
import org.meshtastic.core.ui.component.ListItem
import org.meshtastic.core.ui.component.NodeChip
import org.meshtastic.core.ui.icon.ChevronRight
import org.meshtastic.core.ui.icon.DoDisturb
import org.meshtastic.core.ui.icon.Favorite
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.NotFavorite
import org.meshtastic.core.ui.icon.PersonAdd
import org.meshtastic.core.ui.icon.PersonOff
import org.meshtastic.core.ui.icon.Search
import org.meshtastic.core.ui.icon.Visibility
import org.meshtastic.feature.settings.component.RadioAdminAppBar
import org.meshtastic.feature.settings.radio.RemoteContactAction
import org.meshtastic.feature.settings.radio.RemoteContactsViewModel
import org.meshtastic.feature.settings.radio.RemoteWriteResult
import org.meshtastic.feature.settings.radio.RemoteWriteStatus
import org.meshtastic.feature.settings.radio.component.WarningDialog

@Composable
fun RemoteContactsScreen(viewModel: RemoteContactsViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val destNode by viewModel.destNode.collectAsStateWithLifecycle()
    val candidates by viewModel.candidates.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val lastResult by viewModel.lastResult.collectAsStateWithLifecycle()
    val target = destNode?.effectiveLongName ?: viewModel.destNum.toNodeId()
    val enabled = lastResult?.status != RemoteWriteStatus.SENDING

    Scaffold(
        modifier = modifier,
        topBar = {
            RadioAdminAppBar(
                title = stringResource(Res.string.remote_contacts),
                isLocal = false,
                destNode = destNode,
                onNavigateUp = onBack,
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(16.dp),
        ) {
            item { TargetBanner(target = target, targetId = viewModel.destNum.toNodeId(), targetNode = destNode) }
            item {
                ContactUrlCard(
                    target = target,
                    enabled = enabled,
                    result = lastResult?.takeIf { it.nodeNum == null },
                    onAdd = viewModel::addContactFromUrl,
                )
            }
            item {
                Text(
                    text = stringResource(Res.string.remote_contacts_pick),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = viewModel::setQuery,
                    label = { Text(stringResource(Res.string.live_update_search_nodes)) },
                    leadingIcon = { Icon(MeshtasticIcons.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
            items(candidates, key = { it.num }) { node ->
                CandidateCard(
                    node = node,
                    target = target,
                    enabled = enabled,
                    result = lastResult?.takeIf { it.nodeNum == node.num },
                    onAction = { viewModel.perform(node, it) },
                )
            }
        }
    }
}

@Composable
private fun TargetBanner(target: String, targetId: String, targetNode: Node?) {
    Card(
        colors =
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                targetNode?.let { NodeChip(node = it) }
                Text(
                    text = stringResource(Res.string.remote_contacts_target, "$target ($targetId)"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = stringResource(Res.string.remote_contacts_explainer, target),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ContactUrlCard(target: String, enabled: Boolean, result: RemoteWriteResult?, onAdd: (String) -> Unit) {
    var url by rememberSaveable { mutableStateOf("") }
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text(stringResource(Res.string.remote_contacts_url, target)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            ActionButton(
                icon = MeshtasticIcons.PersonAdd,
                label = Res.string.remote_contacts_add_contact,
                enabled = enabled && url.isNotBlank(),
                onClick = { onAdd(url) },
            )
            result?.let { WriteStatusLine(it, target) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CandidateCard(
    node: Node,
    target: String,
    enabled: Boolean,
    result: RemoteWriteResult?,
    onAction: (RemoteContactAction) -> Unit,
) {
    var confirming by rememberSaveable { mutableStateOf<RemoteContactAction?>(null) }
    confirming?.let { action ->
        val ignore = action == RemoteContactAction.IGNORE
        WarningDialog(
            icon = if (ignore) MeshtasticIcons.DoDisturb else MeshtasticIcons.PersonOff,
            title =
            stringResource(if (ignore) Res.string.remote_contacts_ignore else Res.string.remote_contacts_remove),
            text = {
                val body =
                    if (ignore) Res.string.remote_contacts_ignore_confirm else Res.string.remote_contacts_remove_confirm
                Text(stringResource(body, node.effectiveLongName, target))
            },
            onDismiss = { confirming = null },
            onConfirm = {
                confirming = null
                onAction(action)
            },
        )
    }
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NodeChip(node = node)
                Column {
                    Text(text = node.effectiveLongName, style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = node.user.id,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(MeshtasticIcons.PersonAdd, Res.string.remote_contacts_add_contact, enabled) {
                    onAction(RemoteContactAction.ADD_CONTACT)
                }
                ActionButton(MeshtasticIcons.Favorite, Res.string.remote_contacts_favorite, enabled) {
                    onAction(RemoteContactAction.FAVORITE)
                }
                ActionButton(MeshtasticIcons.NotFavorite, Res.string.remote_contacts_unfavorite, enabled) {
                    onAction(RemoteContactAction.UNFAVORITE)
                }
                ActionButton(MeshtasticIcons.DoDisturb, Res.string.remote_contacts_ignore, enabled) {
                    confirming = RemoteContactAction.IGNORE
                }
                ActionButton(MeshtasticIcons.Visibility, Res.string.remote_contacts_unignore, enabled) {
                    onAction(RemoteContactAction.UNIGNORE)
                }
                ActionButton(MeshtasticIcons.PersonOff, Res.string.remote_contacts_remove, enabled) {
                    confirming = RemoteContactAction.REMOVE
                }
            }
            result?.let { WriteStatusLine(it, target) }
        }
    }
}

@Composable
private fun ActionButton(icon: ImageVector, label: StringResource, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Text(text = stringResource(label), modifier = Modifier.padding(start = ButtonDefaults.IconSpacing))
    }
}

@Composable
private fun WriteStatusLine(result: RemoteWriteResult, target: String) {
    val (text, color) =
        when (result.status) {
            RemoteWriteStatus.SENDING ->
                stringResource(Res.string.remote_contacts_sending, target) to MaterialTheme.colorScheme.onSurfaceVariant

            RemoteWriteStatus.ACKNOWLEDGED ->
                stringResource(Res.string.remote_contacts_sent, target) to MaterialTheme.colorScheme.primary

            RemoteWriteStatus.NOT_ACKNOWLEDGED ->
                stringResource(Res.string.remote_contacts_failed, target) to MaterialTheme.colorScheme.error

            RemoteWriteStatus.INVALID_URL ->
                stringResource(Res.string.remote_contacts_invalid_url) to MaterialTheme.colorScheme.error

            RemoteWriteStatus.NO_PUBLIC_KEY ->
                stringResource(Res.string.remote_contacts_no_public_key, target) to MaterialTheme.colorScheme.error

            RemoteWriteStatus.NOT_SENT ->
                stringResource(Res.string.remote_contacts_not_sent, target) to MaterialTheme.colorScheme.error

            RemoteWriteStatus.SELF_CONTACT ->
                stringResource(Res.string.remote_contacts_self, target) to MaterialTheme.colorScheme.error
        }
    Row(
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (result.status == RemoteWriteStatus.SENDING) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        }
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

/** The row on a remote node's settings page that opens [RemoteContactsScreen]. */
@Composable
internal fun RemoteContactsRow(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ListItem(
        text = stringResource(Res.string.remote_contacts),
        modifier = modifier,
        leadingIcon = MeshtasticIcons.PersonAdd,
        trailingIcon = MeshtasticIcons.ChevronRight,
        enabled = enabled,
        onClick = onClick,
    )
}

private fun Int.toNodeId() = NodeAddress.numToDefaultId(this)
