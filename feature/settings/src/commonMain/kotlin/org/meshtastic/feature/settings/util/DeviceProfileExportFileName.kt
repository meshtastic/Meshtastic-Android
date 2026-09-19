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
package org.meshtastic.feature.settings.util

private const val FALLBACK_NODE_NAME = "node"

/** Upper bound on the name segment so the whole file name stays well inside filesystem limits. */
private const val MAX_NAME_LENGTH = 48

private const val SEPARATOR = '_'

/**
 * Letters and digits of any script are kept, in any Unicode plane, so a node called `Küche`, `東京` or `𐐀` keeps its
 * name. Only characters a storage provider would actually choke on — separators, the Windows reserved set, whitespace,
 * control characters, emoji — are replaced.
 */
private fun Char.isFileNameSafe() = isLetterOrDigit() || this == SEPARATOR || this == '-'

/**
 * Reduces a free-text node name to something safe to hand to a file picker as a suggested name.
 *
 * A run of unsafe characters collapses to one separator rather than one per character, so `Roof // Node` becomes
 * `Roof_Node`. Returns `null` when nothing usable survives (an all-emoji name, for example) so the caller can fall back
 * to another name.
 */
internal fun sanitizeExportNameSegment(name: String?): String? {
    if (name == null) return null
    val sanitized = StringBuilder(name.length)
    var index = 0
    // Steps whole code points: a supplementary-plane letter is two UTF-16 units, and testing either half alone
    // reports a lone surrogate rather than the letter it belongs to.
    while (index < name.length) {
        val high = name[index]
        val paired = high.isHighSurrogate() && index + 1 < name.length && name[index + 1].isLowSurrogate()
        val width = if (paired) 2 else 1
        val safe = if (paired) isSupplementaryNameChar(codePointAt(name, index)) else high.isFileNameSafe()
        when {
            safe -> sanitized.append(name, index, index + width)

            // Collapse a run of unsafe characters, and never open the name with a separator.
            sanitized.isNotEmpty() && sanitized.last() != SEPARATOR -> sanitized.append(SEPARATOR)
        }
        index += width
    }
    return sanitized.toString().take(MAX_NAME_LENGTH).trimEnd(SEPARATOR).takeIf { it.isNotEmpty() }
}

private const val SURROGATE_SHIFT = 10
private const val SURROGATE_OFFSET = 0x10000
private const val HIGH_SURROGATE_BASE = 0xD800
private const val LOW_SURROGATE_BASE = 0xDC00

/**
 * Whether a supplementary-plane code point belongs in a name.
 *
 * `Char.isLetterOrDigit()` only classifies one UTF-16 unit, and the stdlib has no code-point form in `commonMain`.
 * Ranges beat a platform lookup here: the pictographic blocks are what a name has to shed, and everything else above
 * the basic plane is a script — Deseret, Linear B, the CJK extensions — that a node may legitimately be named in.
 */
private fun isSupplementaryNameChar(codePoint: Int): Boolean = PICTOGRAPHIC_RANGES.none { codePoint in it }

/** Musical notation, emoji and pictographs, and the legacy-computing symbols. */
private val PICTOGRAPHIC_RANGES = listOf(0x1D000..0x1D1FF, 0x1F000..0x1FAFF, 0x1FB00..0x1FBFF)

/** The code point of the surrogate pair starting at [index]. */
private fun codePointAt(text: String, index: Int): Int = SURROGATE_OFFSET +
    ((text[index].code - HIGH_SURROGATE_BASE) shl SURROGATE_SHIFT) +
    (text[index + 1].code - LOW_SURROGATE_BASE)

/**
 * Builds the suggested file name for a device profile ("node config") export.
 *
 * Prefers the long name, which since firmware 2.8 is short enough to be practical and is the field that actually
 * distinguishes a person's nodes from each other — short names are routinely identical across them (see #7082). Falls
 * back to the short name, then to a generic placeholder, when the preferred name is absent (the export dialog can
 * exclude it) or has no file-name-safe characters.
 */
internal fun deviceProfileExportFileName(longName: String?, shortName: String?, dateStamp: String): String {
    val nodeName = sanitizeExportNameSegment(longName) ?: sanitizeExportNameSegment(shortName) ?: FALLBACK_NODE_NAME
    return "Meshtastic_${nodeName}_${dateStamp}_nodeConfig.cfg"
}
