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

package org.meshtastic.feature.messaging

import org.meshtastic.core.database.model.Node

/** Defines the various user interactions that can occur on the MessageScreen. */
internal sealed interface MessageScreenEvent {
    /** Send a new text message. */
    data class SendMessage(val text: String, val replyingToPacketId: Int? = null) : MessageScreenEvent

    /** Send an emoji reaction to a specific message. */
    data class SendReaction(val emoji: String, val messageId: Int) : MessageScreenEvent

    /** Delete one or more selected messages. */
    data class DeleteMessages(val ids: List<Long>) : MessageScreenEvent

    /** Mark messages up to a certain ID as read. */
    data class ClearUnreadCount(val lastReadMessageId: Long) : MessageScreenEvent

    /** Handle an action from a node's context menu. */
    data class NodeDetails(val node: Node) : MessageScreenEvent

    /** Set the title of the screen (typically the contact or channel name). */
    data class SetTitle(val title: String) : MessageScreenEvent

    /** Navigate to a different message thread. */
    data class NavigateToMessages(val contactKey: String) : MessageScreenEvent

    /** Navigate to the details screen for a specific node. */
    data class NavigateToNodeDetails(val nodeNum: Int) : MessageScreenEvent

    /** Navigate back to the previous screen. */
    data object NavigateBack : MessageScreenEvent

    /** Copy the given text to the clipboard. */
    data class CopyToClipboard(val text: String) : MessageScreenEvent
}
