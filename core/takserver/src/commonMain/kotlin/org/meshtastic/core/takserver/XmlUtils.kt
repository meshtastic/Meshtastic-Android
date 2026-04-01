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
package org.meshtastic.core.takserver

/** Escapes XML special characters in attribute values and text content. */
internal fun String.xmlEscaped(): String =
    replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")

/**
 * Extracts the sender UID from a GeoChat-format UID string ("GeoChat.<senderUid>.<chatroom>.<messageId>"). Returns the
 * original string unchanged for non-GeoChat UIDs.
 */
internal fun String.geoChatSenderUid(): String = if (startsWith("GeoChat.")) split(".").getOrElse(1) { "" } else this

/**
 * Extracts the message ID from a GeoChat-format UID string ("GeoChat.<senderUid>.<chatroom>.<messageId>"). Returns the
 * original string unchanged for non-GeoChat UIDs.
 */
internal fun String.geoChatMessageId(): String = if (startsWith("GeoChat.")) split(".").lastOrNull() ?: this else this
