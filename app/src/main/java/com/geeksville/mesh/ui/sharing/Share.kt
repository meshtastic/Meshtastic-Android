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

package com.geeksville.mesh.ui.sharing

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.model.Contact
import com.geeksville.mesh.ui.contact.ContactItem
import com.geeksville.mesh.ui.contact.ContactsViewModel
import org.meshtastic.core.ui.component.MainAppBar
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.core.strings.R as Res

@Composable
fun ShareScreen(viewModel: ContactsViewModel = hiltViewModel(), onConfirm: (String) -> Unit, onNavigateUp: () -> Unit) {
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
                items(contacts, key = { it.contactKey }) { contact ->
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
                Icon(
                    imageVector = Icons.AutoMirrored.Default.Send,
                    contentDescription = stringResource(Res.string.share),
                )
            }
        }
    }
}

@PreviewScreenSizes
@Composable
private fun ShareScreenPreview() {
    AppTheme {
        ShareScreen(
            contacts =
            listOf(
                Contact(
                    contactKey = "0^all",
                    shortName = stringResource(Res.string.some_username),
                    longName = stringResource(Res.string.unknown_username),
                    lastMessageTime = "3 minutes ago",
                    lastMessageText = stringResource(Res.string.sample_message),
                    unreadCount = 2,
                    messageCount = 10,
                    isMuted = true,
                    isUnmessageable = false,
                ),
            ),
            onConfirm = {},
            onNavigateUp = {},
        )
    }
}
