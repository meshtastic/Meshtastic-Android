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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.effectiveLongName
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.add
import org.meshtastic.core.resources.add_favorite
import org.meshtastic.core.resources.input_shared_contact_url
import org.meshtastic.core.resources.live_update_search_nodes
import org.meshtastic.core.resources.remote_contacts
import org.meshtastic.core.resources.remote_contacts_add_contact
import org.meshtastic.core.resources.remote_contacts_explainer
import org.meshtastic.core.resources.remote_contacts_failed
import org.meshtastic.core.resources.remote_contacts_invalid_url
import org.meshtastic.core.resources.remote_contacts_sent
import org.meshtastic.core.resources.remove_favorite
import org.meshtastic.core.resources.shared_contact_no_public_key
import org.meshtastic.core.ui.component.ListItem
import org.meshtastic.core.ui.icon.Favorite
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.NotFavorite
import org.meshtastic.core.ui.icon.PersonAdd
import org.meshtastic.core.ui.icon.Search
import org.meshtastic.feature.settings.component.RadioAdminAppBar
import org.meshtastic.feature.settings.radio.RemoteContactsEvent
import org.meshtastic.feature.settings.radio.RemoteContactsViewModel

@Composable
fun RemoteContactsScreen(viewModel: RemoteContactsViewModel, onBack: () -> Unit) {
    val destNode by viewModel.destNode.collectAsStateWithLifecycle()
    val candidates by viewModel.candidates.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val name = destNode?.effectiveLongName ?: ""
            val message =
                when (event) {
                    RemoteContactsEvent.Acknowledged -> getString(Res.string.remote_contacts_sent, name)
                    RemoteContactsEvent.NotAcknowledged -> getString(Res.string.remote_contacts_failed, name)
                    RemoteContactsEvent.InvalidUrl -> getString(Res.string.remote_contacts_invalid_url)
                    RemoteContactsEvent.NoPublicKey -> getString(Res.string.shared_contact_no_public_key)
                }
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        topBar = {
            RadioAdminAppBar(
                title = stringResource(Res.string.remote_contacts),
                isLocal = false,
                destNode = destNode,
                onNavigateUp = onBack,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text = stringResource(Res.string.remote_contacts_explainer),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
                ContactUrlRow(enabled = !busy, onAdd = viewModel::addContactFromUrl)
                OutlinedTextField(
                    value = query,
                    onValueChange = viewModel::setQuery,
                    label = { Text(stringResource(Res.string.live_update_search_nodes)) },
                    leadingIcon = { Icon(MeshtasticIcons.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items(candidates, key = { it.num }) { node ->
                CandidateRow(
                    node = node,
                    enabled = !busy,
                    onAddContact = { viewModel.addContact(node) },
                    onSetFavorite = { viewModel.setFavorite(node, it) },
                )
            }
        }
    }
}

@Composable
private fun ContactUrlRow(enabled: Boolean, onAdd: (String) -> Unit) {
    var url by rememberSaveable { mutableStateOf("") }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text(stringResource(Res.string.input_shared_contact_url)) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        TextButton(enabled = enabled && url.isNotBlank(), onClick = { onAdd(url) }) {
            Text(stringResource(Res.string.add))
        }
    }
}

@Composable
private fun CandidateRow(
    node: Node,
    enabled: Boolean,
    onAddContact: () -> Unit,
    onSetFavorite: (Boolean) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        ListItem(
            text = node.effectiveLongName,
            supportingText = node.user.id,
            enabled = enabled,
            trailingIcon = null,
            onClick = { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ActionItem(MeshtasticIcons.PersonAdd, stringResource(Res.string.remote_contacts_add_contact)) {
                expanded = false
                onAddContact()
            }
            ActionItem(MeshtasticIcons.Favorite, stringResource(Res.string.add_favorite)) {
                expanded = false
                onSetFavorite(true)
            }
            ActionItem(MeshtasticIcons.NotFavorite, stringResource(Res.string.remove_favorite)) {
                expanded = false
                onSetFavorite(false)
            }
        }
    }
}

@Composable
private fun ActionItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    DropdownMenuItem(
        leadingIcon = { Icon(imageVector = icon, contentDescription = null) },
        text = { Text(label) },
        onClick = onClick,
    )
}
