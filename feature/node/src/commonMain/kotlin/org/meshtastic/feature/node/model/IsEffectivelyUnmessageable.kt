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
package org.meshtastic.feature.node.model

import org.meshtastic.core.model.Node
import org.meshtastic.core.model.isUnmessageableRole

val Node.isEffectivelyUnmessageable: Boolean
    get() = user.is_unmessagable ?: user.role.isUnmessageableRole()

/**
 * Whether a direct message to this node can actually be delivered.
 *
 * Two independent signals. [isEffectivelyUnmessageable] is the node saying it does not accept messages. The public key
 * is what the sending radio needs: a direct message takes the PKC path, and a radio holding no key for the destination
 * refuses with `PKI_SEND_FAIL_PUBLIC_KEY` rather than falling back to the channel key. A node whose packets we have
 * heard but whose NodeInfo has not arrived has no key on file, which is common rather than exceptional.
 */
val Node.canDirectMessage: Boolean
    get() = !isEffectivelyUnmessageable && hasPKC

/**
 * Whether to offer the direct-message action for this node.
 *
 * [canDirectMessage] with an escape hatch: a node we can no longer send to may still have a thread worth reading, so an
 * existing conversation keeps the action visible.
 */
fun Node.showsDirectMessageAction(hasConversation: Boolean): Boolean = canDirectMessage || hasConversation
