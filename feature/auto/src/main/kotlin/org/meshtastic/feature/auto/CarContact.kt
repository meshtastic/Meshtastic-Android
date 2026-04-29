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
package org.meshtastic.feature.auto

/**
 * Lightweight projection of a conversation used exclusively within [MeshtasticCarScreen].
 *
 * [isBroadcast] and [channelIndex] drive ordering (channels before DMs, channels sorted by index). [lastMessageTime]
 * drives DM ordering (most-recent first). [lastMessageText] mirrors `ContactsViewModel.contactList`'s `lastMessageText`
 * — received DMs are prefixed with the sender's short name, matching `ContactItem`'s ChatMetadata display.
 *
 * The `lastMessageRawText`, `lastMessageSenderName`, and `lastMessageFromSelf` fields carry the decomposed message data
 * needed to construct a [CarMessage] for [ConversationItem] — the structured body and sender info that the host uses
 * for TTS readout and reply attribution.
 */
internal data class CarContact(
    val contactKey: String,
    val displayName: String,
    val unreadCount: Int,
    val isBroadcast: Boolean,
    val channelIndex: Int,
    val lastMessageTime: Long?,
    val lastMessageText: String?,
    val lastMessageRawText: String?,
    val lastMessageSenderName: String?,
    val lastMessageFromSelf: Boolean,
)
