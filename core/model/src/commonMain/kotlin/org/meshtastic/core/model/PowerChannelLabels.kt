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
 * Merges a user-editable power-channel [label] into [existing] at the 0-based [channelIndex].
 *
 * Earlier channels keep their slot via blank padding, the label is trimmed, and trailing blanks are dropped so clearing
 * a label shrinks the list. Shared by the DB write path and test fakes so the rule can't drift between them.
 */
fun mergePowerChannelLabel(existing: List<String>, channelIndex: Int, label: String): List<String> {
    val labels = existing.toMutableList()
    while (labels.size <= channelIndex) labels.add("")
    labels[channelIndex] = label.trim()
    return labels.dropLastWhile { it.isBlank() }
}
