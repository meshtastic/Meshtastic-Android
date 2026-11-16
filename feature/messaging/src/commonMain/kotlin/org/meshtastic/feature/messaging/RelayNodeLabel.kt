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
package org.meshtastic.feature.messaging

import org.meshtastic.core.model.Node
import org.meshtastic.core.model.effectiveLongName

private const val RELAY_BYTE_MASK = 0xFF
private const val HEX_RADIX = 16

/**
 * Names the relaying node as `Name (0x5A)`, or `0x5A` alone when no known node ends in that byte. The byte is always
 * shown because it is the only part of the header that is certain; the name is a best guess. Null when no relay was
 * recorded (0 or absent).
 */
internal fun relayNodeLabel(relayNode: Int?, nodes: List<Node>, ourNodeNum: Int?): String? {
    val relay = relayNode?.and(RELAY_BYTE_MASK)?.takeIf { it != 0 } ?: return null
    val hex = "0x" + relay.toString(HEX_RADIX).uppercase().padStart(2, '0')
    val name = Node.getRelayNode(relay, nodes, ourNodeNum)?.effectiveLongName?.takeIf { it.isNotBlank() }
    return if (name != null) "$name ($hex)" else hex
}
