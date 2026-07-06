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
package org.meshtastic.core.model

/**
 * The @mention wire token: `@` followed by a node's hex user id (`@!ffccee11`). This is the single source of truth for
 * the cross-platform wire format (see meshtastic/design#21) so the compose input, message rendering, and notification
 * paths all detect it identically. The 8-hex run plus the negative lookahead reject a partial match against a longer
 * id.
 */
val MENTION_TOKEN_REGEX = Regex("""@(![0-9a-fA-F]{8})(?![0-9a-fA-F])""")

/**
 * True if [text] @mentions the node whose hex user id is [userId] (e.g. `!ffccee11`), honoring the token boundary — a
 * trailing hex digit (`@!ffccee11a`) is a different, longer id and is not a match.
 */
fun textMentionsNode(text: String?, userId: String): Boolean {
    if (text.isNullOrEmpty() || userId.length <= 1) return false
    // Derive from the shared token regex so the format/boundary rule lives in exactly one place.
    return MENTION_TOKEN_REGEX.findAll(text).any { it.groupValues[1].equals(userId, ignoreCase = true) }
}
