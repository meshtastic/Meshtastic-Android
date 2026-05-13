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
package org.meshtastic.feature.messaging.ui.sharing

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
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
import org.meshtastic.core.model.Contact
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.share
import org.meshtastic.core.resources.share_to
import org.meshtastic.core.ui.component.MainAppBar
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Send
import org.meshtastic.feature.messaging.ui.contact.ContactItem
import org.meshtastic.feature.messaging.ui.contact.ContactsViewModel

@Composable
fun ShareScreen(viewModel: ContactsViewModel, onConfirm: (String) -> Unit, onNavigateUp: () -> Unit) {
    val contactList by viewModel.contactList.collectAsStateWithLifecycle()

    ShareScreen(contacts = contactList, onConfirm = onConfirm, onNavigateUp = onNavigateUp)
}

@Composable
fun ShareScreen(contacts: List<Contact>, onConfirm: (String) -> Unit, onNavigateUp: () -> Unit) {
    var selectedContact by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            MainAppBar(
                title = stringResource(Res.string.share_to),
                ourNode = null,
                showNodeChip = false,
                canNavigateUp = true,
                onNavigateUp = onNavigateUp,
                actions = {},
                onClickChip = {},
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                itemsIndexed(contacts, key = { index, contact -> "${contact.contactKey}#$index" }) { _, contact ->
                    val selected = contact.contactKey == selectedContact
                    ContactItem(
                        contact = contact,
                        selected = selected,
                        onClick = { selectedContact = contact.contactKey },
                    )
                }
            }

            Button(
                onClick = { onConfirm(selectedContact) },
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                enabled = selectedContact.isNotEmpty(),
            ) {
                Icon(imageVector = MeshtasticIcons.Send, contentDescription = stringResource(Res.string.share))
            }
        }
    }
}

// Preview kept out of commonMain to avoid platform tooling dependencies.
