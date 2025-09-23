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

package com.geeksville.mesh.ui.contact

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.VolumeMute
import androidx.compose.material.icons.automirrored.twotone.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.AppOnlyProtos
import com.geeksville.mesh.model.Contact
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.ui.common.components.MainAppBar
import com.geeksville.mesh.ui.node.components.NodeMenuAction
import org.meshtastic.core.strings.R
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Suppress("LongMethod")
@Composable
fun ContactsScreen(
    uiViewModel: UIViewModel = hiltViewModel(),
    onClickNodeChip: (Int) -> Unit = {},
    onNavigateToMessages: (String) -> Unit = {},
    onNavigateToNodeDetails: (Int) -> Unit = {},
    onNavigateToShare: () -> Unit,
) {
    val isConnected by uiViewModel.isConnectedStateFlow.collectAsStateWithLifecycle()
    val ourNode by uiViewModel.ourNodeInfo.collectAsStateWithLifecycle()
    var showMuteDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // State for managing selected contacts
    val selectedContactKeys = remember { mutableStateListOf<String>() }
    val isSelectionModeActive by remember { derivedStateOf { selectedContactKeys.isNotEmpty() } }

    // State for contacts list
    val contacts by uiViewModel.contactList.collectAsStateWithLifecycle()

    // Derived state for selected contacts and count
    val selectedContacts =
        remember(contacts, selectedContactKeys) { contacts.filter { it.contactKey in selectedContactKeys } }
    val selectedCount = remember(selectedContacts) { selectedContacts.sumOf { it.messageCount } }
    val isAllMuted = remember(selectedContacts) { selectedContacts.all { it.isMuted } }

    // Callback functions for item interaction
    val onContactClick: (Contact) -> Unit = { contact ->
        if (isSelectionModeActive) {
            // If in selection mode, toggle selection
            if (selectedContactKeys.contains(contact.contactKey)) {
                selectedContactKeys.remove(contact.contactKey)
            } else {
                selectedContactKeys.add(contact.contactKey)
            }
        } else {
            // If not in selection mode, navigate to messages
            onNavigateToMessages(contact.contactKey)
        }
    }

    val onNodeChipClick: (Contact) -> Unit = { contact ->
        if (contact.contactKey.contains("!")) {
            // if it's a node, look up the nodeNum including the !
            val nodeKey = contact.contactKey.substring(1)
            val node = uiViewModel.getNode(nodeKey)

            if (node != null) {
                // navigate to node details.
                onNavigateToNodeDetails(node.num)
            }
        } else {
            // Channels
        }
    }

    val onContactLongClick: (Contact) -> Unit = { contact ->
        // Enter selection mode and select the item on long press
        if (!isSelectionModeActive) {
            selectedContactKeys.add(contact.contactKey)
        } else {
            // If already in selection mode, toggle selection
            if (selectedContactKeys.contains(contact.contactKey)) {
                selectedContactKeys.remove(contact.contactKey)
            } else {
                selectedContactKeys.add(contact.contactKey)
            }
        }
    }
    Scaffold(
        topBar = {
            MainAppBar(
                title = stringResource(R.string.conversations),
                ourNode = ourNode,
                isConnected = isConnected,
                showNodeChip = ourNode != null && isConnected,
                canNavigateUp = false,
                onNavigateUp = {},
                actions = {},
                onAction = { action ->
                    when (action) {
                        is NodeMenuAction.MoreDetails -> onClickNodeChip(action.node.num)
                        else -> {}
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                modifier = Modifier.animateFloatingActionButton(visible = isConnected, alignment = Alignment.BottomEnd),
                onClick = onNavigateToShare,
            ) {
                Icon(Icons.Rounded.QrCode2, contentDescription = null)
            }
        },
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            if (isSelectionModeActive) {
                // Display selection toolbar when in selection mode
                SelectionToolbar(
                    selectedCount = selectedContactKeys.size,
                    onCloseSelection = { selectedContactKeys.clear() },
                    onMuteSelected = { showMuteDialog = true },
                    onDeleteSelected = { showDeleteDialog = true },
                    onSelectAll = {
                        selectedContactKeys.clear()
                        selectedContactKeys.addAll(contacts.map { it.contactKey })
                    },
                    isAllMuted = isAllMuted, // Pass the derived state
                )
            }

            val channels by uiViewModel.channels.collectAsStateWithLifecycle()
            ContactListView(
                contacts = contacts,
                selectedList = selectedContactKeys,
                onClick = onContactClick,
                onLongClick = onContactLongClick,
                channels = channels,
                onNodeChipClick = onNodeChipClick,
            )
        }
    }
    DeleteConfirmationDialog(
        showDialog = showDeleteDialog,
        selectedCount = selectedCount,
        onDismiss = { showDeleteDialog = false },
        onConfirm = {
            showDeleteDialog = false
            uiViewModel.deleteContacts(selectedContactKeys.toList())
            selectedContactKeys.clear()
        },
    )

    MuteNotificationsDialog(
        showDialog = showMuteDialog,
        onDismiss = { showMuteDialog = false },
        onConfirm = { muteUntil ->
            showMuteDialog = false
            uiViewModel.setMuteUntil(selectedContactKeys.toList(), muteUntil)
            selectedContactKeys.clear()
        },
    )
}

@Suppress("LongMethod")
@Composable
fun MuteNotificationsDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit, // Lambda to handle the confirmed mute duration
) {
    if (showDialog) {
        // Options for mute duration
        val muteOptions = remember {
            listOf(
                R.string.unmute to 0L,
                R.string.mute_8_hours to TimeUnit.HOURS.toMillis(8),
                R.string.mute_1_week to TimeUnit.DAYS.toMillis(7),
                R.string.mute_always to Long.MAX_VALUE,
            )
        }

        // State to hold the selected mute duration index
        var selectedOptionIndex by remember { mutableStateOf(2) } // Default to "Always"

        AlertDialog(
            onDismissRequest = onDismiss, // Dismiss the dialog when clicked outside
            title = { Text(text = stringResource(R.string.mute_notifications)) },
            text = {
                Column {
                    muteOptions.forEachIndexed { index, (stringRes, _) ->
                        val isSelected = index == selectedOptionIndex
                        val text = stringResource(stringRes)
                        Row(
                            modifier =
                            Modifier.fillMaxWidth()
                                .selectable(selected = isSelected, onClick = { selectedOptionIndex = index })
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = isSelected, onClick = { selectedOptionIndex = index })
                            Text(text = text, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val selectedMuteDuration = muteOptions[selectedOptionIndex].second
                        onConfirm(selectedMuteDuration)
                        onDismiss() // Dismiss the dialog after confirming
                    },
                ) {
                    Text(stringResource(R.string.okay))
                }
            },
            dismissButton = {
                Button(
                    onClick = onDismiss, // Dismiss the dialog on cancel
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
fun DeleteConfirmationDialog(
    showDialog: Boolean,
    selectedCount: Int, // Number of items to be deleted
    onDismiss: () -> Unit,
    onConfirm: () -> Unit, // Lambda to handle the delete action
) {
    if (showDialog) {
        val deleteMessage =
            pluralStringResource(
                id = R.plurals.delete_messages,
                count = selectedCount,
                formatArgs = arrayOf(selectedCount), // Pass the count as a format argument
            )

        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                // Optional: You could add a title here if needed, e.g., "Confirm Deletion"
            },
            text = { Text(text = deleteMessage) },
            confirmButton = {
                Button(
                    onClick = {
                        onConfirm()
                        onDismiss() // Dismiss the dialog after confirming
                    },
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = { Button(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
            properties =
            DialogProperties(
                dismissOnClickOutside = true, // Allow dismissing by clicking outside
                dismissOnBackPress = true, // Allow dismissing with the back button
            ),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionToolbar(
    selectedCount: Int,
    onCloseSelection: () -> Unit,
    onMuteSelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    onSelectAll: () -> Unit,
    isAllMuted: Boolean,
) {
    TopAppBar(
        title = { Text(text = "$selectedCount") },
        navigationIcon = {
            IconButton(onClick = onCloseSelection) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close_selection))
            }
        },
        actions = {
            IconButton(onClick = onMuteSelected) {
                Icon(
                    imageVector =
                    if (isAllMuted) {
                        Icons.AutoMirrored.TwoTone.VolumeUp
                    } else {
                        Icons.AutoMirrored.TwoTone.VolumeMute
                    },
                    contentDescription =
                    if (isAllMuted) {
                        "Unmute selected"
                    } else {
                        "Mute selected"
                    },
                )
            }
            IconButton(onClick = onDeleteSelected) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_selection))
            }
            IconButton(onClick = onSelectAll) {
                Icon(Icons.Default.SelectAll, contentDescription = stringResource(R.string.select_all))
            }
        },
    )
}

@Composable
fun ContactListView(
    contacts: List<Contact>,
    selectedList: List<String>,
    onClick: (Contact) -> Unit,
    onLongClick: (Contact) -> Unit,
    channels: AppOnlyProtos.ChannelSet? = null,
    onNodeChipClick: (Contact) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(contacts, key = { it.contactKey }) { contact ->
            val selected by remember { derivedStateOf { selectedList.contains(contact.contactKey) } }

            ContactItem(
                contact = contact,
                selected = selected,
                onClick = { onClick(contact) },
                onLongClick = {
                    onLongClick(contact)
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                },
                channels = channels,
                onNodeChipClick = { onNodeChipClick(contact) },
            )
        }
    }
}
