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
package org.meshtastic.feature.messaging.component

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.Message
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.Reaction
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.a11y_message_from
import org.meshtastic.core.resources.action_show_message_status
import org.meshtastic.core.resources.filter_message_label
import org.meshtastic.core.resources.message_translated_label
import org.meshtastic.core.resources.reply
import org.meshtastic.core.resources.security_signed_verified
import org.meshtastic.core.ui.component.AutoLinkText
import org.meshtastic.core.ui.component.HighlightedText
import org.meshtastic.core.ui.component.NODE_TINT_EMPHASIZED
import org.meshtastic.core.ui.component.NODE_TINT_MUTED
import org.meshtastic.core.ui.component.NODE_TINT_NORMAL
import org.meshtastic.core.ui.component.NodeChip
import org.meshtastic.core.ui.component.Rssi
import org.meshtastic.core.ui.component.Snr
import org.meshtastic.core.ui.component.TransportIcon
import org.meshtastic.core.ui.component.nodeBorderStroke
import org.meshtastic.core.ui.component.nodeTintedContainer
import org.meshtastic.core.ui.emoji.EmojiPickerDialog
import org.meshtastic.core.ui.icon.FormatQuote
import org.meshtastic.core.ui.icon.HopCount
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.ShieldCheck
import org.meshtastic.core.ui.theme.MessageItemColors
import org.meshtastic.core.ui.theme.StatusColors.StatusGreen
import org.meshtastic.core.ui.util.createClipEntry

internal const val MESSAGE_STATUS_LABEL_TEST_TAG = "message_status_label"

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun MessageItem(
    modifier: Modifier = Modifier,
    node: Node,
    ourNode: Node,
    message: Message,
    selected: Boolean,
    inSelectionMode: Boolean = false,
    onReply: () -> Unit = {},
    sendReaction: (String) -> Unit = {},
    onShowReactions: () -> Unit = {},
    showUserName: Boolean = true,
    emojis: List<Reaction> = emptyList(),
    quickEmojis: List<String> = listOf("👍", "👎", "😂", "🔥", "❤️", "😮"),
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    onDoubleClick: () -> Unit = {},
    onSelect: () -> Unit = {},
    onDelete: () -> Unit = {},
    onClickChip: (Node) -> Unit = {},
    resolveMention: (String) -> Node? = { null },
    onNavigateToOriginalMessage: (Int) -> Unit = {},
    onStatusClick: () -> Unit = {},
    hasSamePrev: Boolean = false,
    hasSameNext: Boolean = false,
    searchQuery: String = "",
    translationAvailable: Boolean = false,
    isDirectMessage: Boolean = false,
    onTranslate: () -> Unit = {},
    onToggleTranslation: () -> Unit = {},
) = Column(
    modifier =
    modifier
        .fillMaxWidth()
        // Wider gap between sender groups, tight spacing within a group, so runs read as one unit.
        .padding(
            top =
            if (hasSamePrev) {
                2.dp
            } else {
                10.dp
            },
        ),
) {
    var activeSheet by remember { mutableStateOf<ActiveSheet?>(null) }
    val clipboardManager = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isLocal = node.num == ourNode.num
    val statusString = message.getStatusStringRes(isDirectMessage)
    val isDirectImplicitAck = message.status == MessageStatus.DELIVERED && isDirectMessage
    // While searching, always show the original text — FTS matches and highlights apply to it, not the translation.
    val showsTranslation = message.showTranslated && message.translatedText != null && searchQuery.isEmpty()
    val bodyText = message.displayedText(searching = searchQuery.isNotEmpty())
    if (activeSheet != null) {
        ModalBottomSheet(onDismissRequest = { activeSheet = null }, sheetState = sheetState) {
            when (activeSheet) {
                ActiveSheet.Actions -> {
                    MessageActionsContent(
                        quickEmojis = quickEmojis,
                        onReply = {
                            activeSheet = null
                            onReply()
                        },
                        onReact = { emoji ->
                            activeSheet = null
                            sendReaction(emoji)
                        },
                        onMoreReactions = { activeSheet = ActiveSheet.Emoji },
                        onCopy = {
                            activeSheet = null
                            coroutineScope.launch {
                                clipboardManager.setClipEntry(createClipEntry(bodyText, "message"))
                            }
                        },
                        onSelect = {
                            activeSheet = null
                            onSelect()
                        },
                        onDelete = {
                            activeSheet = null
                            onDelete()
                        },
                        statusString = statusString,
                        status =
                        if (isLocal) {
                            message.status
                        } else {
                            null
                        },
                        xeddsaSigned = message.xeddsaSigned,
                        onStatus = onStatusClick,
                        translationRowState = translationRowStateFor(message, translationAvailable),
                        onTranslate = {
                            activeSheet = null
                            onTranslate()
                        },
                        onToggleTranslation = {
                            activeSheet = null
                            onToggleTranslation()
                        },
                    )
                }

                ActiveSheet.Emoji -> {
                    EmojiPickerDialog(
                        onDismiss = { activeSheet = null },
                        onConfirm = { emoji ->
                            activeSheet = null
                            sendReaction(emoji)
                        },
                    )
                }

                null -> {}
            }
        }
    }

    val containsBel = message.text.contains('\u0007')

    val nodeColor = Color(if (message.fromLocal) ourNode.colors.second else node.colors.second)
    // Match the node card: a faint node wash over the neutral surface + a node outline (more AA than a saturated
    // fill).
    val tintFraction =
        when {
            inSelectionMode && selected -> NODE_TINT_EMPHASIZED
            message.filtered || (inSelectionMode && !selected) -> NODE_TINT_MUTED
            else -> NODE_TINT_NORMAL
        }
    val containerColor = nodeTintedContainer(nodeColor, fraction = tintFraction)
    val cardBorder = nodeBorderStroke(nodeColor, active = selected)
    val contentColor = MaterialTheme.colorScheme.onSurface
    val metadataStyle = MaterialTheme.typography.labelSmall
    val messageShape =
        getMessageBubbleShape(
            cornerRadius = 18.dp,
            isSender = message.fromLocal,
            hasSamePrev = hasSamePrev,
            hasSameNext = hasSameNext,
        )
    val messageModifier =
        Modifier.padding(horizontal = 12.dp)
            .then(
                if (containsBel) {
                    Modifier.border(2.dp, color = MessageItemColors.Red, shape = messageShape)
                } else {
                    Modifier
                },
            )
    val senderName = if (message.fromLocal) ourNode.user.long_name else node.user.long_name
    val messageA11yText = stringResource(Res.string.a11y_message_from, senderName, bodyText)
    // Timestamp lives in the group header (Google Chat pattern) rather than inside every bubble; grouping is
    // time-windowed upstream, so the header time is always close to every message in the run.
    if (showUserName) {
        if (message.fromLocal) {
            Text(
                text = message.time,
                modifier = Modifier.align(Alignment.End).padding(end = 12.dp, bottom = 2.dp),
                style = metadataStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                NodeChip(node = node, onClick = onClickChip, modifier = Modifier.height(28.dp))
                Text(
                    text = node.user.long_name,
                    modifier = Modifier.weight(1f, fill = false),
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(text = message.time, style = metadataStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    Surface(
        modifier =
        Modifier.align(if (message.fromLocal) Alignment.End else Alignment.Start)
            // Reserve space on the opposite side and cap the width so bubbles never span the
            // whole screen (keeps sender sides scannable on phones and wide layouts alike).
            .padding(
                start = if (!message.fromLocal) 0.dp else 36.dp,
                end = if (message.fromLocal) 0.dp else 36.dp,
            )
            .widthIn(max = 480.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    onLongClick()
                    if (!inSelectionMode) {
                        activeSheet = ActiveSheet.Actions
                    }
                },
                onDoubleClick = onDoubleClick,
            )
            .then(messageModifier)
            .semantics(mergeDescendants = true) {
                contentDescription = messageA11yText
                role = Role.Button
            },
        color = containerColor,
        contentColor = contentColor,
        shape = messageShape,
        border = cardBorder,
    ) {
        Column(modifier = Modifier.width(IntrinsicSize.Max)) {
            OriginalMessageSnippet(
                modifier = Modifier.fillMaxWidth(),
                message = message,
                ourNode = ourNode,
                onNavigateToOriginalMessage = onNavigateToOriginalMessage,
            )

            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                if (searchQuery.isNotEmpty()) {
                    HighlightedText(
                        text = message.text,
                        query = searchQuery,
                        style = MaterialTheme.typography.bodyLarge,
                        color = contentColor,
                    )
                } else {
                    val mentionDisplayName =
                        remember(resolveMention) {
                            { id: String ->
                                resolveMention(id)?.let { it.user.long_name.ifEmpty { it.user.short_name } }
                            }
                        }
                    AutoLinkText(
                        text = bodyText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = contentColor,
                        mentionName = mentionDisplayName,
                        onMentionClick = { id -> resolveMention(id)?.let(onClickChip) },
                    )
                }

                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (!message.fromLocal) {
                        // All mesh diagnostics (signature, signal or hops, transport) grouped in one compact run.
                        DiagnosticsRow {
                            // XEdDSA is only set on verified broadcasts, never DMs — so this never shows on a DM.
                            if (message.xeddsaSigned) {
                                Icon(
                                    imageVector = MeshtasticIcons.ShieldCheck,
                                    contentDescription = stringResource(Res.string.security_signed_verified),
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.StatusGreen,
                                )
                            }
                            if (message.hopsAway == 0 && !message.viaMqtt) {
                                Snr(message.snr)
                                Rssi(message.rssi)
                            } else {
                                Icon(
                                    imageVector = MeshtasticIcons.HopCount,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = if (message.hopsAway >= 0) message.hopsAway.toString() else "?",
                                    style = metadataStyle,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TransportIcon(
                                transport = message.transportMechanism,
                                viaMqtt = message.viaMqtt,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (containsBel) {
                        Text(text = "\uD83D\uDD14")
                    }
                    if (message.filtered) {
                        Text(
                            text = stringResource(Res.string.filter_message_label),
                            style = metadataStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (showsTranslation) {
                        Text(
                            text = stringResource(Res.string.message_translated_label),
                            style = metadataStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (message.fromLocal) {
                        MessageStatusLabel(
                            status = message.status ?: MessageStatus.UNKNOWN,
                            text = stringResource(statusString.second),
                            metadataStyle = metadataStyle,
                            isWarning = isDirectImplicitAck,
                            onStatusClick = onStatusClick,
                        )
                    }
                }
            }
        }
    }

    ReactionRow(
        modifier =
        Modifier.align(if (message.fromLocal) Alignment.End else Alignment.Start)
            .padding(
                top = 2.dp,
                start = if (!message.fromLocal) 12.dp else 48.dp,
                end = if (message.fromLocal) 12.dp else 48.dp,
            ),
        reactions = if (message.fromLocal) emojis.reversed() else emojis,
        myId = ourNode.user.id,
        onSendReaction = sendReaction,
        onShowReactions = onShowReactions,
    )
}

private enum class ActiveSheet {
    Actions,
    Emoji,
}

/** Row grouping a received message's mesh diagnostics (signature, signal or hops, transport). */
@Composable
private fun DiagnosticsRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}

@Composable
private fun MessageStatusLabel(
    status: MessageStatus,
    text: String,
    metadataStyle: TextStyle,
    isWarning: Boolean,
    onStatusClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusColor = messageStatusColor(status, isWarning = isWarning)
    Row(
        modifier =
        modifier
            .fillMaxWidth()
            .testTag(MESSAGE_STATUS_LABEL_TEST_TAG)
            .clickable(
                onClickLabel = stringResource(Res.string.action_show_message_status),
                role = Role.Button,
                onClick = onStatusClick,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        MessageStatusIcon(
            status = status,
            modifier = Modifier.size(14.dp),
            tint = statusColor,
            includeContentDescription = false,
        )
        Text(
            text = text,
            modifier = Modifier.weight(1f, fill = false),
            style = metadataStyle,
            color = statusColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun translationRowStateFor(message: Message, translationAvailable: Boolean): TranslationRowState? = when {
    // Toggling a persisted translation is just a DB flag flip — offer it even when
    // the translation engine is no longer available for the current locale.
    message.translatedText != null ->
        if (message.showTranslated) TranslationRowState.ShowOriginal else TranslationRowState.ShowTranslation

    !translationAvailable || message.text.isBlank() -> null

    else -> TranslationRowState.Translate
}

@Composable
private fun OriginalMessageSnippet(
    message: Message,
    ourNode: Node,
    onNavigateToOriginalMessage: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val originalMessage = message.originalMessage
    if (originalMessage != null && originalMessage.packetId != 0) {
        val originalMessageNode = if (originalMessage.fromLocal) ourNode else originalMessage.node
        // Same node-tinted treatment as the bubble (keeps onSurface text AA), but at the emphasized tint so the quoted
        // header still reads as distinct from the bubble body below it.
        val replyContainerColor =
            nodeTintedContainer(Color(originalMessageNode.colors.second), fraction = NODE_TINT_EMPHASIZED)
        val replyContentColor = MaterialTheme.colorScheme.onSurface
        // Rectangle shape — the outer message bubble's Surface clips to its
        // rounded corners, so the reply header inherits the correct top radii
        // automatically and stays square on the bottom where body text follows.
        Surface(
            modifier = modifier.fillMaxWidth().clickable { onNavigateToOriginalMessage(originalMessage.packetId) },
            contentColor = replyContentColor,
            color = replyContainerColor,
            shape = RectangleShape,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    MeshtasticIcons.FormatQuote,
                    contentDescription = stringResource(Res.string.reply),
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = originalMessageNode.user.short_name,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = originalMessage.text,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
