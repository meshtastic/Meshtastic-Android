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
package org.meshtastic.core.ui.input

/** What one edit to the keyboard sink typed: [deleted] backspaces, then [inserted] text. */
internal data class SinkEdit(val deleted: Int, val inserted: String)

/** One changed range of the keyboard sink: [replacedLength] characters of the old text gave way to [inserted]. */
internal class SinkChange(val replacedLength: Int, val inserted: String)

/**
 * The input a set of sink changes amounts to. A change that only removes text is a backspace; one that inserts text
 * types exactly what it inserted, so an IME replacing the sentinel, or a paste over it, sends no stray backspace and
 * keeps a leading space.
 */
internal fun sinkEdit(changes: List<SinkChange>): SinkEdit {
    var deleted = 0
    val inserted = StringBuilder()
    for (change in changes) {
        if (change.inserted.isEmpty()) deleted += change.replacedLength else inserted.append(change.inserted)
    }
    return SinkEdit(deleted, inserted.toString())
}
