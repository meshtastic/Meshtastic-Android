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
package com.geeksville.mesh.ui.contact

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.geeksville.mesh.model.Contact
import com.geeksville.mesh.model.UIViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.database.entity.ContactSettings
import org.meshtastic.core.model.util.formatMuteRemainingTime
import org.meshtastic.core.model.util.getChannel
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.cancel
import org.meshtastic.core.strings.channel_invalid
import org.meshtastic.core.strings.close_selection
import org.meshtastic.core.strings.conversations
import org.meshtastic.core.strings.currently
import org.meshtastic.core.strings.delete
import org.meshtastic.core.strings.delete_messages
import org.meshtastic.core.strings.delete_selection
import org.meshtastic.core.strings.mute_1_week
import org.meshtastic.core.strings.mute_8_hours
import org.meshtastic.core.strings.mute_always
import org.meshtastic.core.strings.mute_notifications
import org.meshtastic.core.strings.mute_status_always
import org.meshtastic.core.strings.mute_status_muted_for_days
import org.meshtastic.core.strings.mute_status_muted_for_hours
import org.meshtastic.core.strings.mute_status_unmuted
import org.meshtastic.core.strings.okay
import org.meshtastic.core.strings.select_all
import org.meshtastic.core.strings.unmute
import org.meshtastic.core.ui.component.AddContactFAB
import org.meshtastic.core.ui.component.MainAppBar
import org.meshtastic.core.ui.component.ScrollToTopEvent
import org.meshtastic.core.ui.component.smartScrollToTop
import org.meshtastic.core.ui.icon.Close
import org.meshtastic.core.ui.icon.Delete
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.SelectAll
import org.meshtastic.core.ui.icon.VolumeMuteTwoTone
import org.meshtastic.core.ui.icon.VolumeUpTwoTone
import org.meshtastic.core.ui.qr.ScannedQrCodeDialog
import org.meshtastic.core.ui.util.showToast
import org.meshtastic.proto.ChannelSet
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalPermissionsApi::class)
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun ContactsScreen(
    onNavigateToShare: () -> Unit,
    viewModel: ContactsViewModel = hiltViewModel(),
    uIViewModel: UIViewModel = hiltViewModel(),
    onClickNodeChip: (Int) -> Unit = {},
    onNavigateToMessages: (String) -> Unit = {},
    onNavigateToNodeDetails: (Int) -> Unit = {},
    scrollToTopEvents: Flow<ScrollToTopEvent>? = null,
    activeContactKey: String? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val ourNode by viewModel.ourNodeInfo.collectAsStateWithLifecycle()
    var showMuteDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // State for managing selected contacts
    val selectedContactKeys = remember { mutableStateListOf<String>() }
    val isSelectionModeActive by remember { derivedStateOf { selectedContactKeys.isNotEmpty() } }

    // State for contacts list
    val pagedContacts = viewModel.contactListPaged.collectAsLazyPagingItems()

    // Create channel placeholders (always show broadcast contacts, even when empty)
    val channels by viewModel.channels.collectAsStateWithLifecycle()
    val channelPlaceholders =
        remember(channels.settings.size) {
            (0 until channels.settings.size).map { ch ->
                Contact(
                    contactKey = "$ch^all",
                    shortName = "$ch",
                    longName = channels.getChannel(ch)?.name ?: "Channel $ch",
                    lastMessageTime = "",
                    lastMessageText = "",
                    unreadCount = 0,
                    messageCount = 0,
                    isMuted = false,
                    isUnmessageable = false,
                    nodeColors = null,
                )
            }
        }

    val contactsListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(scrollToTopEvents) {
        scrollToTopEvents?.collectLatest { event ->
            if (event is ScrollToTopEvent.ConversationsTabPressed) {
                contactsListState.smartScrollToTop(coroutineScope)
            }
        }
    }

    // Derived state for selected contacts and count
    val selectedContacts =
        remember(pagedContacts.itemCount, selectedContactKeys) {
            (0 until pagedContacts.itemCount)
                .mapNotNull { pagedContacts[it] }
                .filter { it.contactKey in selectedContactKeys }
        }
    // Get message count directly from repository for selected contacts
    var selectedCount by remember { mutableStateOf(0) }
    LaunchedEffect(selectedContactKeys.size, selectedContactKeys.joinToString(",")) {
        selectedCount = viewModel.getTotalMessageCount(selectedContactKeys.toList())
    }
    val isAllMuted = remember(selectedContacts) { selectedContacts.all { it.isMuted } }

    val sharedContactRequested by uIViewModel.sharedContactRequested.collectAsStateWithLifecycle()
    val requestChannelSet by uIViewModel.requestChannelSet.collectAsStateWithLifecycle()
    requestChannelSet?.let { ScannedQrCodeDialog(it, onDismiss = { uIViewModel.clearRequestChannelUrl() }) }

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
            val node = viewModel.getNode(nodeKey)
            onNavigateToNodeDetails(node.num)
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
                title = stringResource(Res.string.conversations),
                ourNode = ourNode,
                showNodeChip = ourNode != null && connectionState.isConnected(),
                canNavigateUp = false,
                onNavigateUp = {},
                actions = {},
                onClickChip = { onClickNodeChip(it.num) },
            )
        },
        floatingActionButton = {
            if (connectionState.isConnected()) {
                AddContactFAB(
                    sharedContact = sharedContactRequested,
                    onResult = { uri ->
                        uIViewModel.handleScannedUri(uri) {
                            scope.launch { context.showToast(Res.string.channel_invalid) }
                        }
                    },
                    onShareChannels = onNavigateToShare,
                    onDismissSharedContact = { uIViewModel.clearSharedContactRequested() },
                    isContactContext = false,
                )
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
                        selectedContactKeys.addAll(
                            (0 until pagedContacts.itemCount).mapNotNull { pagedContacts[it]?.contactKey },
                        )
                    },
                    isAllMuted = isAllMuted, // Pass the derived state
                )
            }

            ContactListViewPaged(
                contacts = pagedContacts,
                channelPlaceholders = channelPlaceholders,
                selectedList = selectedContactKeys,
                activeContactKey = activeContactKey,
                onClick = onContactClick,
                onLongClick = onContactLongClick,
                onNodeChipClick = onNodeChipClick,
                listState = contactsListState,
                channels = channels,
            )
        }
    }
    DeleteConfirmationDialog(
        showDialog = showDeleteDialog,
        selectedCount = selectedCount,
        onDismiss = { showDeleteDialog = false },
        onConfirm = {
            showDeleteDialog = false
            viewModel.deleteContacts(selectedContactKeys.toList())
            selectedContactKeys.clear()
        },
    )

    // Get contact settings for the dialog
    val contactSettings by viewModel.getContactSettings().collectAsStateWithLifecycle(initialValue = emptyMap())

    MuteNotificationsDialog(
        showDialog = showMuteDialog,
        selectedContactKeys = selectedContactKeys.toList(),
        contactSettings = contactSettings,
        onDismiss = { showMuteDialog = false },
        onConfirm = { muteUntil ->
            showMuteDialog = false
            viewModel.setMuteUntil(selectedContactKeys.toList(), muteUntil)
            selectedContactKeys.clear()
        },
    )
}

@Suppress("LongMethod")
@Composable
private fun MuteNotificationsDialog(
    showDialog: Boolean,
    selectedContactKeys: List<String>,
    contactSettings: Map<String, ContactSettings>,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit, // Lambda to handle the confirmed mute duration
) {
    if (showDialog) {
        // Options for mute duration
        val muteOptions = remember {
            listOf(
                Res.string.unmute to 0L,
                Res.string.mute_8_hours to TimeUnit.HOURS.toMillis(8),
                Res.string.mute_1_week to TimeUnit.DAYS.toMillis(7),
                Res.string.mute_always to Long.MAX_VALUE,
            )
        }

        // State to hold the selected mute duration index
        var selectedOptionIndex by remember { mutableStateOf(2) } // Default to "Always"

        AlertDialog(
            onDismissRequest = onDismiss, // Dismiss the dialog when clicked outside
            title = { Text(text = stringResource(Res.string.mute_notifications)) },
            text = {
                Column {
                    // Show current mute status
                    selectedContactKeys.forEach { contactKey ->
                        contactSettings[contactKey]?.let { settings ->
                            val now = System.currentTimeMillis()
                            val statusText =
                                when {
                                    settings.muteUntil > 0 && settings.muteUntil != Long.MAX_VALUE -> {
                                        val remaining = settings.muteUntil - now
                                        if (remaining > 0) {
                                            val (days, hours) = formatMuteRemainingTime(remaining)
                                            if (days >= 1) {
                                                stringResource(Res.string.mute_status_muted_for_days, days, hours)
                                            } else {
                                                stringResource(Res.string.mute_status_muted_for_hours, hours)
                                            }
                                        } else {
                                            stringResource(Res.string.mute_status_unmuted)
                                        }
                                    }
                                    settings.muteUntil == Long.MAX_VALUE ->
                                        stringResource(Res.string.mute_status_always)
                                    else -> stringResource(Res.string.mute_status_unmuted)
                                }
                            Text(
                                text = stringResource(Res.string.currently) + " " + statusText,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }
                    }

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
                    Text(stringResource(Res.string.okay))
                }
            },
            dismissButton = {
                Button(
                    onClick = onDismiss, // Dismiss the dialog on cancel
                ) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun DeleteConfirmationDialog(
    showDialog: Boolean,
    selectedCount: Int, // Number of items to be deleted
    onDismiss: () -> Unit,
    onConfirm: () -> Unit, // Lambda to handle the delete action
) {
    if (showDialog) {
        val deleteMessage =
            pluralStringResource(
                Res.plurals.delete_messages,
                selectedCount,
                selectedCount, // Pass the count as a format argument
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
                    Text(stringResource(Res.string.delete))
                }
            },
            dismissButton = { Button(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
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
private fun SelectionToolbar(
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
                Icon(MeshtasticIcons.Close, contentDescription = stringResource(Res.string.close_selection))
            }
        },
        actions = {
            IconButton(onClick = onMuteSelected) {
                Icon(
                    imageVector =
                    if (isAllMuted) {
                        MeshtasticIcons.VolumeUpTwoTone
                    } else {
                        MeshtasticIcons.VolumeMuteTwoTone
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
                Icon(MeshtasticIcons.Delete, contentDescription = stringResource(Res.string.delete_selection))
            }
            IconButton(onClick = onSelectAll) {
                Icon(MeshtasticIcons.SelectAll, contentDescription = stringResource(Res.string.select_all))
            }
        },
    )
}

@Composable
private fun ContactListViewPaged(
    contacts: LazyPagingItems<Contact>,
    channelPlaceholders: List<Contact>,
    selectedList: List<String>,
    activeContactKey: String?,
    onClick: (Contact) -> Unit,
    onLongClick: (Contact) -> Unit,
    onNodeChipClick: (Contact) -> Unit,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    channels: ChannelSet? = null,
) {
    val haptics = LocalHapticFeedback.current

    if (contacts.loadState.refresh is LoadState.Loading && contacts.itemCount == 0) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val visiblePlaceholders = rememberVisiblePlaceholders(contacts, channelPlaceholders)

    ContactListContentInternal(
        contacts = contacts,
        visiblePlaceholders = visiblePlaceholders,
        selectedList = selectedList,
        activeContactKey = activeContactKey,
        onClick = onClick,
        onLongClick = onLongClick,
        onNodeChipClick = onNodeChipClick,
        listState = listState,
        modifier = modifier,
        channels = channels,
        haptics = haptics,
    )
}

@Composable
private fun ContactListContentInternal(
    contacts: LazyPagingItems<Contact>,
    visiblePlaceholders: List<Contact>,
    selectedList: List<String>,
    activeContactKey: String?,
    onClick: (Contact) -> Unit,
    onLongClick: (Contact) -> Unit,
    onNodeChipClick: (Contact) -> Unit,
    listState: LazyListState,
    channels: ChannelSet?,
    haptics: HapticFeedback,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize(), state = listState) {
        contactListPagedItems(
            contacts = contacts,
            selectedList = selectedList,
            activeContactKey = activeContactKey,
            onClick = onClick,
            onLongClick = onLongClick,
            onNodeChipClick = onNodeChipClick,
            channels = channels,
            haptics = haptics,
        )

        contactListPlaceholdersItems(
            visiblePlaceholders = visiblePlaceholders,
            selectedList = selectedList,
            activeContactKey = activeContactKey,
            onClick = onClick,
            onLongClick = onLongClick,
            onNodeChipClick = onNodeChipClick,
            channels = channels,
            haptics = haptics,
        )

        contactListAppendLoadingItem(contacts = contacts)
    }
}

private fun LazyListScope.contactListPlaceholdersItems(
    visiblePlaceholders: List<Contact>,
    selectedList: List<String>,
    activeContactKey: String?,
    onClick: (Contact) -> Unit,
    onLongClick: (Contact) -> Unit,
    onNodeChipClick: (Contact) -> Unit,
    channels: ChannelSet?,
    haptics: HapticFeedback,
) {
    items(
        count = visiblePlaceholders.size,
        key = { index -> "placeholder_${visiblePlaceholders[index].contactKey}" },
    ) { index ->
        val placeholder = visiblePlaceholders[index]
        val selected by remember { derivedStateOf { selectedList.contains(placeholder.contactKey) } }
        val isActive = remember(placeholder.contactKey, activeContactKey) { placeholder.contactKey == activeContactKey }

        ContactItem(
            contact = placeholder,
            selected = selected,
            isActive = isActive,
            onClick = { onClick(placeholder) },
            onLongClick = {
                onLongClick(placeholder)
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            },
            channels = channels,
            onNodeChipClick = { onNodeChipClick(placeholder) },
        )
    }
}

private fun LazyListScope.contactListPagedItems(
    contacts: LazyPagingItems<Contact>,
    selectedList: List<String>,
    activeContactKey: String?,
    onClick: (Contact) -> Unit,
    onLongClick: (Contact) -> Unit,
    onNodeChipClick: (Contact) -> Unit,
    channels: ChannelSet?,
    haptics: HapticFeedback,
) {
    items(
        count = contacts.itemCount,
        key = { index ->
            val contact = contacts[index]
            contact?.let { "${it.contactKey}#$index" } ?: "contact_placeholder_$index"
        },
    ) { index ->
        val contact = contacts[index]
        if (contact != null) {
            val selected by remember { derivedStateOf { selectedList.contains(contact.contactKey) } }
            val isActive = remember(contact.contactKey, activeContactKey) { contact.contactKey == activeContactKey }

            ContactItem(
                contact = contact,
                selected = selected,
                isActive = isActive,
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

private fun LazyListScope.contactListAppendLoadingItem(contacts: LazyPagingItems<Contact>) {
    contacts.apply {
        when {
            loadState.append is LoadState.Loading -> {
                item(key = "append_loading") {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberVisiblePlaceholders(
    contacts: LazyPagingItems<Contact>,
    channelPlaceholders: List<Contact>,
): List<Contact> {
    val contactKeys by
        remember(contacts.itemCount) {
            derivedStateOf { (0 until contacts.itemCount).mapNotNull { contacts[it]?.contactKey }.toSet() }
        }
    return remember(channelPlaceholders, contactKeys) {
        channelPlaceholders.filter { placeholder -> !contactKeys.contains(placeholder.contactKey) }
    }
}
