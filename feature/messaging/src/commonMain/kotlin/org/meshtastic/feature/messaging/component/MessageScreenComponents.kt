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
@file:Suppress("TooManyFunctions")

package org.meshtastic.feature.messaging.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.database.entity.QuickChatAction
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.Message
import org.meshtastic.core.model.Node
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.alert_bell_text
import org.meshtastic.core.resources.cancel_reply
import org.meshtastic.core.resources.clear_selection
import org.meshtastic.core.resources.copy
import org.meshtastic.core.resources.delete
import org.meshtastic.core.resources.delete_messages
import org.meshtastic.core.resources.delete_messages_title
import org.meshtastic.core.resources.filter_disable_for_contact
import org.meshtastic.core.resources.filter_enable_for_contact
import org.meshtastic.core.resources.filter_hide_count
import org.meshtastic.core.resources.filter_settings
import org.meshtastic.core.resources.filter_show_count
import org.meshtastic.core.resources.navigate_back
import org.meshtastic.core.resources.new_messages_below
import org.meshtastic.core.resources.overflow_menu
import org.meshtastic.core.resources.quick_chat
import org.meshtastic.core.resources.quick_chat_hide
import org.meshtastic.core.resources.quick_chat_show
import org.meshtastic.core.resources.reply
import org.meshtastic.core.resources.replying_to
import org.meshtastic.core.resources.scroll_to_bottom
import org.meshtastic.core.resources.select_all
import org.meshtastic.core.resources.unknown
import org.meshtastic.core.ui.component.MeshtasticTextDialog
import org.meshtastic.core.ui.component.NodeKeyStatusIcon
import org.meshtastic.core.ui.component.SecurityIcon
import org.meshtastic.core.ui.icon.ArrowBack
import org.meshtastic.core.ui.icon.ArrowDownward
import org.meshtastic.core.ui.icon.ChatBubbleOutline
import org.meshtastic.core.ui.icon.Close
import org.meshtastic.core.ui.icon.Copy
import org.meshtastic.core.ui.icon.Delete
import org.meshtastic.core.ui.icon.FilterList
import org.meshtastic.core.ui.icon.FilterListOff
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.More
import org.meshtastic.core.ui.icon.Muted
import org.meshtastic.core.ui.icon.Reply
import org.meshtastic.core.ui.icon.SelectAll
import org.meshtastic.core.ui.icon.Settings
import org.meshtastic.core.ui.icon.Unmuted
import org.meshtastic.core.ui.icon.Visibility
import org.meshtastic.core.ui.icon.VisibilityOff
import org.meshtastic.feature.messaging.DeliveryInfo
import org.meshtastic.proto.ChannelSet

// region ── ScrollToBottomFab ──

/**
 * A FloatingActionButton that scrolls the message list to the bottom (most recent messages).
 *
 * @param coroutineScope The coroutine scope for launching the scroll animation.
 * @param listState The [LazyListState] of the message list.
 * @param unreadCount The number of unread messages to display as a badge.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoxScope.ScrollToBottomFab(coroutineScope: CoroutineScope, listState: LazyListState, unreadCount: Int) {
    FloatingActionButton(
        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        onClick = { coroutineScope.launch { listState.animateScrollToItem(0) } },
    ) {
        if (unreadCount > 0) {
            BadgedBox(badge = { Badge { Text(unreadCount.toString()) } }) {
                Icon(
                    imageVector = MeshtasticIcons.ArrowDownward,
                    contentDescription = stringResource(Res.string.scroll_to_bottom),
                )
            }
        } else {
            Icon(
                imageVector = MeshtasticIcons.ArrowDownward,
                contentDescription = stringResource(Res.string.scroll_to_bottom),
            )
        }
    }
}

// endregion

// region ── ReplySnippet ──

/**
 * Displays a snippet of the message being replied to.
 *
 * @param originalMessage The message being replied to, or null if not replying.
 * @param onClearReply Callback to clear the reply state.
 * @param ourNode The current user's node information, to display "You" if replying to self.
 */
@Composable
fun ReplySnippet(originalMessage: Message?, onClearReply: () -> Unit, ourNode: Node?) {
    AnimatedVisibility(visible = originalMessage != null) {
        originalMessage?.let { message ->
            val isFromLocalUser = message.fromLocal
            val replyingToNodeUser = if (isFromLocalUser) ourNode?.user else message.node.user
            val unknownUserText = stringResource(Res.string.unknown)

            Row(
                modifier =
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = MeshtasticIcons.Reply,
                    contentDescription = stringResource(Res.string.reply),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(Res.string.replying_to, replyingToNodeUser?.short_name ?: unknownUserText),
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    modifier = Modifier.weight(1f),
                    text = message.text.ellipsize(SNIPPET_CHARACTER_LIMIT),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(onClick = onClearReply) {
                    Icon(MeshtasticIcons.Close, contentDescription = stringResource(Res.string.cancel_reply))
                }
            }
        }
    }
}

// endregion

// region ── DeleteMessageDialog ──

/**
 * A dialog confirming the deletion of messages.
 *
 * @param count The number of messages to be deleted.
 * @param onConfirm Callback invoked when the user confirms the deletion.
 * @param onDismiss Callback invoked when the dialog is dismissed.
 */
@Composable
fun DeleteMessageDialog(count: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val deleteMessagesString = pluralStringResource(Res.plurals.delete_messages, count, count)

    MeshtasticTextDialog(
        titleRes = Res.string.delete_messages_title,
        message = deleteMessagesString,
        confirmTextRes = Res.string.delete,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

// endregion

// region ── ActionModeTopBar & MessageMenuAction ──

/** Actions available in the message selection mode's top bar. */
sealed class MessageMenuAction {
    data object ClipboardCopy : MessageMenuAction()

    data object Delete : MessageMenuAction()

    data object Dismiss : MessageMenuAction()

    data object SelectAll : MessageMenuAction()
}

/**
 * The top app bar displayed when in message selection mode.
 *
 * @param selectedCount The number of currently selected messages.
 * @param onAction Callback for when a menu action is triggered.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionModeTopBar(selectedCount: Int, onAction: (MessageMenuAction) -> Unit) = TopAppBar(
    title = { Text(text = selectedCount.toString()) },
    navigationIcon = {
        IconButton(onClick = { onAction(MessageMenuAction.Dismiss) }) {
            Icon(
                imageVector = MeshtasticIcons.ArrowBack,
                contentDescription = stringResource(Res.string.clear_selection),
            )
        }
    },
    actions = {
        IconButton(onClick = { onAction(MessageMenuAction.ClipboardCopy) }) {
            Icon(imageVector = MeshtasticIcons.Copy, contentDescription = stringResource(Res.string.copy))
        }
        IconButton(onClick = { onAction(MessageMenuAction.Delete) }) {
            Icon(imageVector = MeshtasticIcons.Delete, contentDescription = stringResource(Res.string.delete))
        }
        IconButton(onClick = { onAction(MessageMenuAction.SelectAll) }) {
            Icon(
                imageVector = MeshtasticIcons.SelectAll,
                contentDescription = stringResource(Res.string.select_all),
            )
        }
    },
)

// endregion

// region ── MessageTopBar ──

/**
 * The default top app bar for the message screen.
 *
 * @param title The title to display (contact or channel name).
 * @param channelIndex The index of the current channel, if applicable.
 * @param mismatchKey True if there's a key mismatch for the current PKC.
 * @param onNavigateBack Callback for the navigation icon.
 * @param channels The set of all channels, used for the [SecurityIcon].
 * @param channelIndexParam The specific channel index for the [SecurityIcon].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageTopBar(
    title: String,
    channelIndex: Int?,
    mismatchKey: Boolean,
    onNavigateBack: () -> Unit,
    channels: ChannelSet?,
    channelIndexParam: Int?,
    showQuickChat: Boolean,
    onToggleQuickChat: () -> Unit,
    onNavigateToQuickChatOptions: () -> Unit = {},
    filteringDisabled: Boolean = false,
    onToggleFilteringDisabled: () -> Unit = {},
    filteredCount: Int = 0,
    showFiltered: Boolean = false,
    onToggleShowFiltered: () -> Unit = {},
    onNavigateToFilterSettings: () -> Unit = {},
) = TopAppBar(
    title = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.width(10.dp))

            if (channels != null && channelIndexParam != null) {
                SecurityIcon(channels, channelIndexParam)
            }
        }
    },
    navigationIcon = {
        IconButton(onClick = onNavigateBack) {
            Icon(
                imageVector = MeshtasticIcons.ArrowBack,
                contentDescription = stringResource(Res.string.navigate_back),
            )
        }
    },
    actions = {
        MessageTopBarActions(
            showQuickChat = showQuickChat,
            onToggleQuickChat = onToggleQuickChat,
            onNavigateToQuickChatOptions = onNavigateToQuickChatOptions,
            channelIndex = channelIndex,
            mismatchKey = mismatchKey,
            filteringDisabled = filteringDisabled,
            onToggleFilteringDisabled = onToggleFilteringDisabled,
            filteredCount = filteredCount,
            showFiltered = showFiltered,
            onToggleShowFiltered = onToggleShowFiltered,
            onNavigateToFilterSettings = onNavigateToFilterSettings,
        )
    },
)

@Composable
private fun MessageTopBarActions(
    showQuickChat: Boolean,
    onToggleQuickChat: () -> Unit,
    onNavigateToQuickChatOptions: () -> Unit,
    channelIndex: Int?,
    mismatchKey: Boolean,
    filteringDisabled: Boolean,
    onToggleFilteringDisabled: () -> Unit,
    filteredCount: Int,
    showFiltered: Boolean,
    onToggleShowFiltered: () -> Unit,
    onNavigateToFilterSettings: () -> Unit,
) {
    if (channelIndex == DataPacket.PKC_CHANNEL_INDEX) {
        NodeKeyStatusIcon(hasPKC = true, mismatchKey = mismatchKey)
    }
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, enabled = true) {
            Icon(imageVector = MeshtasticIcons.More, contentDescription = stringResource(Res.string.overflow_menu))
        }
        OverFlowMenu(
            expanded = expanded,
            onDismiss = { expanded = false },
            showQuickChat = showQuickChat,
            onToggleQuickChat = onToggleQuickChat,
            onNavigateToQuickChatOptions = onNavigateToQuickChatOptions,
            filteringDisabled = filteringDisabled,
            onToggleFilteringDisabled = onToggleFilteringDisabled,
            filteredCount = filteredCount,
            showFiltered = showFiltered,
            onToggleShowFiltered = onToggleShowFiltered,
            onNavigateToFilterSettings = onNavigateToFilterSettings,
        )
    }
}

@Composable
private fun OverFlowMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    showQuickChat: Boolean,
    onToggleQuickChat: () -> Unit,
    onNavigateToQuickChatOptions: () -> Unit,
    filteringDisabled: Boolean,
    onToggleFilteringDisabled: () -> Unit,
    filteredCount: Int,
    showFiltered: Boolean,
    onToggleShowFiltered: () -> Unit,
    onNavigateToFilterSettings: () -> Unit,
) {
    if (expanded) {
        DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
            QuickChatToggleMenuItem(showQuickChat, onDismiss, onToggleQuickChat)
            QuickChatOptionsMenuItem(onDismiss, onNavigateToQuickChatOptions)
            if (filteredCount > 0 && !filteringDisabled) {
                FilteredMessagesMenuItem(showFiltered, filteredCount, onDismiss, onToggleShowFiltered)
            }
            FilterToggleMenuItem(filteringDisabled, onDismiss, onToggleFilteringDisabled)
            FilterSettingsMenuItem(onDismiss, onNavigateToFilterSettings)
        }
    }
}

@Composable
private fun QuickChatToggleMenuItem(showQuickChat: Boolean, onDismiss: () -> Unit, onToggle: () -> Unit) {
    val title = stringResource(if (showQuickChat) Res.string.quick_chat_hide else Res.string.quick_chat_show)
    DropdownMenuItem(
        text = { Text(title) },
        onClick = {
            onDismiss()
            onToggle()
        },
        leadingIcon = {
            Icon(
                imageVector = if (showQuickChat) MeshtasticIcons.Muted else MeshtasticIcons.Unmuted,
                contentDescription = title,
            )
        },
    )
}

@Composable
private fun QuickChatOptionsMenuItem(onDismiss: () -> Unit, onNavigate: () -> Unit) {
    val title = stringResource(Res.string.quick_chat)
    DropdownMenuItem(
        text = { Text(title) },
        onClick = {
            onDismiss()
            onNavigate()
        },
        leadingIcon = { Icon(imageVector = MeshtasticIcons.ChatBubbleOutline, contentDescription = title) },
    )
}

@Composable
private fun FilteredMessagesMenuItem(showFiltered: Boolean, count: Int, onDismiss: () -> Unit, onToggle: () -> Unit) {
    val title = stringResource(if (showFiltered) Res.string.filter_hide_count else Res.string.filter_show_count, count)
    DropdownMenuItem(
        text = { Text(title) },
        onClick = {
            onDismiss()
            onToggle()
        },
        leadingIcon = {
            Icon(
                imageVector = if (showFiltered) MeshtasticIcons.VisibilityOff else MeshtasticIcons.Visibility,
                contentDescription = title,
            )
        },
    )
}

@Composable
private fun FilterToggleMenuItem(filteringDisabled: Boolean, onDismiss: () -> Unit, onToggle: () -> Unit) {
    val title =
        stringResource(
            if (filteringDisabled) Res.string.filter_enable_for_contact else Res.string.filter_disable_for_contact,
        )
    DropdownMenuItem(
        text = { Text(title) },
        onClick = {
            onDismiss()
            onToggle()
        },
        leadingIcon = {
            Icon(
                imageVector = if (filteringDisabled) MeshtasticIcons.FilterList else MeshtasticIcons.FilterListOff,
                contentDescription = title,
            )
        },
    )
}

@Composable
private fun FilterSettingsMenuItem(onDismiss: () -> Unit, onNavigate: () -> Unit) {
    val title = stringResource(Res.string.filter_settings)
    DropdownMenuItem(
        text = { Text(title) },
        onClick = {
            onDismiss()
            onNavigate()
        },
        leadingIcon = { Icon(imageVector = MeshtasticIcons.Settings, contentDescription = title) },
    )
}

// endregion

// region ── QuickChatRow ──

/**
 * A row of quick chat action buttons.
 *
 * @param enabled Whether the buttons should be enabled.
 * @param actions The list of [QuickChatAction]s to display.
 * @param onClick Callback when a quick chat button is clicked.
 */
@Composable
fun QuickChatRow(
    modifier: Modifier = Modifier,
    enabled: Boolean,
    actions: List<QuickChatAction>,
    onClick: (QuickChatAction) -> Unit,
) {
    val alertActionMessage = stringResource(Res.string.alert_bell_text)
    val alertAction =
        remember(alertActionMessage) {
            QuickChatAction(
                name = "🔔",
                message = "🔔 $alertActionMessage \u0007",
                mode = QuickChatAction.Mode.Append,
                position = -1,
            )
        }

    val allActions = remember(alertAction, actions) { listOf(alertAction) + actions }

    LazyRow(modifier = modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        items(allActions, key = { it.uuid }) { action ->
            Button(onClick = { onClick(action) }, enabled = enabled) { Text(text = action.name) }
        }
    }
}

/**
 * Handles a quick chat action, either appending its message to the current text or sending it directly.
 *
 * @param action The [QuickChatAction] to handle.
 * @param currentText The current text in the message input.
 * @param onUpdateText Lambda to call when the text needs to be updated (for Append mode).
 * @param onSendMessage Lambda to call when a message needs to be sent (for Instant mode).
 */
fun handleQuickChatAction(
    action: QuickChatAction,
    currentText: String,
    onUpdateText: (String) -> Unit,
    onSendMessage: (String) -> Unit,
) {
    when (action.mode) {
        QuickChatAction.Mode.Append -> {
            if (!currentText.contains(action.message)) {
                val newText =
                    buildString {
                        append(currentText)
                        if (currentText.isNotEmpty() && !currentText.endsWith(' ')) {
                            append(' ')
                        }
                        append(action.message)
                    }
                        .limitBytes(MESSAGE_CHARACTER_LIMIT_BYTES)
                onUpdateText(newText)
            }
        }

        QuickChatAction.Mode.Instant -> {
            onSendMessage(action.message)
        }
    }
}

// endregion

// region ── UnreadMessagesDivider ──

@Composable
fun UnreadMessagesDivider(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f))
        Text(
            text = stringResource(Res.string.new_messages_below),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        HorizontalDivider(modifier = Modifier.weight(1f))
    }
}

// endregion

// region ── MessageStatusDialog ──

@Composable
fun MessageStatusDialog(message: Message, resendOption: Boolean, onResend: () -> Unit, onDismiss: () -> Unit) {
    val (title, text) = message.getStatusStringRes()
    DeliveryInfo(
        title = title,
        resendOption = resendOption,
        text = text,
        relays = message.relays,
        onConfirm = onResend,
        onDismiss = onDismiss,
    )
}

// endregion

// region ── Utility Functions ──

/** The maximum number of characters to display in the reply snippet. */
internal const val SNIPPET_CHARACTER_LIMIT = 50

/** The maximum byte size for a message. */
const val MESSAGE_CHARACTER_LIMIT_BYTES = 200

/**
 * Ellipsizes a string if its length exceeds [maxLength].
 *
 * @param maxLength The maximum number of characters to display before adding "…".
 * @return The ellipsized string.
 * @receiver The string to ellipsize.
 */
fun String.ellipsize(maxLength: Int): String = if (length > maxLength) "${take(maxLength)}…" else this

/**
 * Truncates a string to ensure its UTF-8 byte representation does not exceed [maxBytes].
 *
 * @param maxBytes The maximum allowed byte length.
 * @return The truncated string, or the original string if it's within the byte limit.
 * @receiver The string to limit.
 */
fun String.limitBytes(maxBytes: Int): String {
    val bytes = this.encodeToByteArray()
    if (bytes.size <= maxBytes) {
        return this
    }

    var currentBytesSum = 0
    var validCharCount = 0
    for (charIndex in this.indices) {
        val charToTest = this[charIndex]
        val charBytes = charToTest.toString().encodeToByteArray().size
        if (currentBytesSum + charBytes > maxBytes) {
            break
        }
        currentBytesSum += charBytes
        validCharCount++
    }
    return this.substring(0, validCharCount)
}

// endregion
