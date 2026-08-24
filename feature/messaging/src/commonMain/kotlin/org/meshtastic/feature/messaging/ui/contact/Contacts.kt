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
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package org.meshtastic.feature.messaging.ui.contact

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.common.util.CommonUri
import org.meshtastic.core.common.util.NumberFormatter
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.Contact
import org.meshtastic.core.model.ContactKey
import org.meshtastic.core.model.ContactSettings
import org.meshtastic.core.model.util.TimeConstants
import org.meshtastic.core.model.util.formatMuteRemainingTime
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.are_you_sure
import org.meshtastic.core.resources.cancel
import org.meshtastic.core.resources.channel_invalid
import org.meshtastic.core.resources.channels
import org.meshtastic.core.resources.close_selection
import org.meshtastic.core.resources.collapsed
import org.meshtastic.core.resources.conversations
import org.meshtastic.core.resources.currently
import org.meshtastic.core.resources.delete
import org.meshtastic.core.resources.delete_messages
import org.meshtastic.core.resources.delete_selection
import org.meshtastic.core.resources.direct_messages
import org.meshtastic.core.resources.expanded
import org.meshtastic.core.resources.mark_as_read
import org.meshtastic.core.resources.mute_1_week
import org.meshtastic.core.resources.mute_8_hours
import org.meshtastic.core.resources.mute_always
import org.meshtastic.core.resources.mute_notifications
import org.meshtastic.core.resources.mute_selected
import org.meshtastic.core.resources.mute_status_always
import org.meshtastic.core.resources.mute_status_muted_for_days
import org.meshtastic.core.resources.mute_status_muted_for_hours
import org.meshtastic.core.resources.mute_status_unmuted
import org.meshtastic.core.resources.okay
import org.meshtastic.core.resources.select_all
import org.meshtastic.core.resources.unmute
import org.meshtastic.core.resources.unmute_selected
import org.meshtastic.core.ui.component.MainAppBar
import org.meshtastic.core.ui.component.MeshtasticDialog
import org.meshtastic.core.ui.component.MeshtasticImportFAB
import org.meshtastic.core.ui.component.MeshtasticTextDialog
import org.meshtastic.core.ui.component.ScrollToTopEvent
import org.meshtastic.core.ui.component.smartScrollToTop
import org.meshtastic.core.ui.icon.Close
import org.meshtastic.core.ui.icon.Delete
import org.meshtastic.core.ui.icon.ExpandLess
import org.meshtastic.core.ui.icon.ExpandMore
import org.meshtastic.core.ui.icon.MarkChatRead
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.SelectAll
import org.meshtastic.core.ui.icon.VolumeMute
import org.meshtastic.core.ui.icon.VolumeUp
import org.meshtastic.core.ui.util.parseDeepLinkOrInvalid
import org.meshtastic.core.ui.util.rememberShowToastResource
import org.meshtastic.proto.ChannelSet
import kotlin.time.Duration.Companion.days

@Suppress("LongMethod", "CyclomaticComplexMethod", "LongParameterList")
@Composable
fun ContactsScreen(
    onNavigateToShare: () -> Unit,
    onHandleDeepLink: (CommonUri, onInvalid: () -> Unit) -> Unit,
    viewModel: ContactsViewModel,
    onClickNodeChip: (Int) -> Unit,
    onNavigateToMessages: (String) -> Unit,
    onNavigateToNodeDetails: (Int) -> Unit,
    scrollToTopEvents: Flow<ScrollToTopEvent>?,
    activeContactKey: String?,
) {
    val showToast = rememberShowToastResource()
    val scope = rememberCoroutineScope()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val ourNode by viewModel.ourNodeInfo.collectAsStateWithLifecycle()
    var showMuteDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }

    // State for managing selected contacts
    val selectedContactKeys = remember { mutableStateListOf<String>() }
    val isSelectionModeActive by remember { derivedStateOf { selectedContactKeys.isNotEmpty() } }

    // State for contacts list. Channel placeholders (empty broadcast channels) are already merged in by the VM.
    val contacts by viewModel.contactList.collectAsStateWithLifecycle()
    val channels by viewModel.channels.collectAsStateWithLifecycle()

    val contactsListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(scrollToTopEvents) {
        scrollToTopEvents?.collectLatest { event ->
            if (event == ScrollToTopEvent.ConversationsTabPressed) {
                contactsListState.smartScrollToTop(coroutineScope)
            }
        }
    }

    // Derived state for selected contacts and count
    val selectedContacts =
        remember(contacts, selectedContactKeys) { contacts.filter { it.contactKey in selectedContactKeys } }
    // Get message count directly from repository for selected contacts
    var selectedCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(selectedContactKeys.size, selectedContactKeys.joinToString(",")) {
        selectedCount = viewModel.getTotalMessageCount(selectedContactKeys.toList())
    }
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
            val nodeKey = ContactKey(contact.contactKey).addressString
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
                showNodeChip = ourNode != null && connectionState is ConnectionState.Connected,
                canNavigateUp = false,
                onNavigateUp = {},
                actions = {
                    val unreadCountTotal by viewModel.unreadCountTotal.collectAsStateWithLifecycle(0)
                    if (unreadCountTotal > 0) {
                        IconButton(onClick = { viewModel.markAllAsRead() }) {
                            Icon(
                                MeshtasticIcons.MarkChatRead,
                                contentDescription = stringResource(Res.string.mark_as_read),
                            )
                        }
                    }
                },
                onClickChip = { onClickNodeChip(it.num) },
            )
        },
        floatingActionButton = {
            if (connectionState is ConnectionState.Connected) {
                MeshtasticImportFAB(
                    onImport = { uriString ->
                        val onInvalid: () -> Unit = { scope.launch { showToast(Res.string.channel_invalid) } }
                        parseDeepLinkOrInvalid(uriString, onHandleDeepLink, onInvalid)
                    },
                    onShareChannels = onNavigateToShare,
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
                        selectedContactKeys.addAll(contacts.map { it.contactKey })
                    },
                    isAllMuted = isAllMuted, // Pass the derived state
                )
            }

            val collapsedSections by viewModel.collapsedSections.collectAsStateWithLifecycle()

            ContactListView(
                contacts = contacts,
                selectedList = selectedContactKeys,
                activeContactKey = activeContactKey,
                onClick = onContactClick,
                onLongClick = onContactLongClick,
                onNodeChipClick = onNodeChipClick,
                listState = contactsListState,
                channels = channels,
                collapsedSections = collapsedSections,
                onToggleSectionCollapse = viewModel::toggleSectionCollapse,
            )
        }
    }

    if (showDeleteDialog) {
        DeleteConfirmationDialog(
            selectedCount = selectedCount,
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                showDeleteDialog = false
                viewModel.deleteContacts(selectedContactKeys.toList())
                selectedContactKeys.clear()
            },
        )
    }

    // Get contact settings for the dialog
    val contactSettings by viewModel.getContactSettings().collectAsStateWithLifecycle(initialValue = emptyMap())

    if (showMuteDialog) {
        MuteNotificationsDialog(
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
}

@Suppress("LongMethod")
@Composable
private fun MuteNotificationsDialog(
    selectedContactKeys: List<String>,
    contactSettings: Map<String, ContactSettings>,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit, // Lambda to handle the confirmed mute duration
) {
    // Options for mute duration
    val muteOptions = remember {
        listOf(
            Res.string.unmute to 0L,
            Res.string.mute_8_hours to TimeConstants.EIGHT_HOURS.inWholeMilliseconds,
            Res.string.mute_1_week to 7.days.inWholeMilliseconds,
            Res.string.mute_always to Long.MAX_VALUE,
        )
    }

    // State to hold the selected mute duration index
    var selectedOptionIndex by remember { mutableIntStateOf(2) } // Default to "Always"

    MeshtasticDialog(
        onDismiss = onDismiss, // Dismiss the dialog when clicked outside
        titleRes = Res.string.mute_notifications,
        confirmTextRes = Res.string.okay,
        onConfirm = {
            val selectedMuteDuration = muteOptions[selectedOptionIndex].second
            onConfirm(selectedMuteDuration)
            onDismiss() // Dismiss the dialog after confirming
        },
        dismissTextRes = Res.string.cancel,
        text = {
            Column {
                // Show current mute status
                selectedContactKeys.forEach { contactKey ->
                    contactSettings[contactKey]?.let { settings ->
                        val now = nowMillis
                        val statusText =
                            when {
                                settings.muteUntil > 0 && settings.muteUntil != Long.MAX_VALUE -> {
                                    val remaining = settings.muteUntil - now
                                    if (remaining > 0) {
                                        val (days, hours) = formatMuteRemainingTime(remaining)
                                        val hoursFormatted = NumberFormatter.format(hours, 1)
                                        if (days >= 1) {
                                            stringResource(Res.string.mute_status_muted_for_days, days, hoursFormatted)
                                        } else {
                                            stringResource(Res.string.mute_status_muted_for_hours, hoursFormatted)
                                        }
                                    } else {
                                        stringResource(Res.string.mute_status_unmuted)
                                    }
                                }

                                settings.muteUntil == Long.MAX_VALUE -> stringResource(Res.string.mute_status_always)

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
    )
}

@Composable
private fun DeleteConfirmationDialog(
    selectedCount: Int, // Number of items to be deleted
    onDismiss: () -> Unit,
    onConfirm: () -> Unit, // Lambda to handle the delete action
) {
    val deleteMessage =
        pluralStringResource(
            Res.plurals.delete_messages,
            selectedCount,
            selectedCount, // Pass the count as a format argument
        )

    MeshtasticTextDialog(
        titleRes = Res.string.are_you_sure,
        message = deleteMessage,
        confirmTextRes = Res.string.delete,
        onConfirm = {
            onConfirm()
            onDismiss() // Dismiss the dialog after confirming
        },
        onDismiss = onDismiss,
    )
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
                        MeshtasticIcons.VolumeUp
                    } else {
                        MeshtasticIcons.VolumeMute
                    },
                    contentDescription =
                    stringResource(
                        if (isAllMuted) {
                            Res.string.unmute_selected
                        } else {
                            Res.string.mute_selected
                        },
                    ),
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
private fun ContactListView(
    contacts: List<Contact>,
    selectedList: List<String>,
    activeContactKey: String?,
    onClick: (Contact) -> Unit,
    onLongClick: (Contact) -> Unit,
    onNodeChipClick: (Contact) -> Unit,
    listState: LazyListState,
    collapsedSections: Set<String>,
    onToggleSectionCollapse: (String) -> Unit,
    modifier: Modifier = Modifier,
    channels: ChannelSet? = null,
) {
    val haptic = LocalHapticFeedback.current
    val (channelContacts, dmContacts) =
        remember(contacts) {
            val (channelPart, dmPart) = contacts.partition { it.section() == ContactSection.CHANNELS }
            // Channels keep a fixed slot order (channel index); DMs stay in the query's recency order.
            channelPart.sortedBy { ContactKey(it.contactKey).channel } to dmPart
        }
    val channelsTitle = stringResource(Res.string.channels)
    val dmTitle = stringResource(Res.string.direct_messages)

    LazyColumn(state = listState, modifier = modifier.fillMaxSize()) {
        contactSection(
            section = ContactSection.CHANNELS,
            title = channelsTitle,
            sectionContacts = channelContacts,
            collapsed = ContactSection.CHANNELS.key in collapsedSections,
            onToggleCollapse = onToggleSectionCollapse,
            selectedList = selectedList,
            activeContactKey = activeContactKey,
            onClick = onClick,
            onLongClick = onLongClick,
            onNodeChipClick = onNodeChipClick,
            channels = channels,
            haptic = haptic,
        )

        contactSection(
            section = ContactSection.DIRECT_MESSAGES,
            title = dmTitle,
            sectionContacts = dmContacts,
            collapsed = ContactSection.DIRECT_MESSAGES.key in collapsedSections,
            onToggleCollapse = onToggleSectionCollapse,
            selectedList = selectedList,
            activeContactKey = activeContactKey,
            onClick = onClick,
            onLongClick = onLongClick,
            onNodeChipClick = onNodeChipClick,
            channels = channels,
            haptic = haptic,
        )
    }
}

private fun LazyListScope.contactSection(
    section: ContactSection,
    title: String,
    sectionContacts: List<Contact>,
    collapsed: Boolean,
    onToggleCollapse: (String) -> Unit,
    selectedList: List<String>,
    activeContactKey: String?,
    onClick: (Contact) -> Unit,
    onLongClick: (Contact) -> Unit,
    onNodeChipClick: (Contact) -> Unit,
    channels: ChannelSet?,
    haptic: HapticFeedback,
) {
    if (sectionContacts.isEmpty()) return

    stickyHeader(key = "${section.key}_header") {
        ContactSectionHeader(
            title = title,
            count = sectionContacts.size,
            collapsed = collapsed,
            onClick = { onToggleCollapse(section.key) },
        )
    }

    if (!collapsed) {
        items(count = sectionContacts.size, key = { index -> sectionContacts[index].contactKey }) { index ->
            val contact = sectionContacts[index]
            ContactItem(
                contact = contact,
                selected = selectedList.contains(contact.contactKey),
                isActive = contact.contactKey == activeContactKey,
                onClick = { onClick(contact) },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick(contact)
                },
                onNodeChipClick = { onNodeChipClick(contact) },
                channels = channels,
            )
        }
    }
}

@Composable
private fun ContactSectionHeader(title: String, count: Int, collapsed: Boolean, onClick: () -> Unit) {
    val expandStateDescription = stringResource(if (collapsed) Res.string.collapsed else Res.string.expanded)
    Row(
        modifier =
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { stateDescription = expandStateDescription }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$title ($count)",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        Icon(
            imageVector = if (collapsed) MeshtasticIcons.ExpandMore else MeshtasticIcons.ExpandLess,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}
