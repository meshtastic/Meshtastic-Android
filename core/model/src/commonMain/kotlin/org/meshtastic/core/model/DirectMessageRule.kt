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

val Node.isEffectivelyUnmessageable: Boolean
    get() = user.is_unmessagable ?: user.role.isUnmessageableRole()

/** Whether this, our own node, is in licensed (ham) mode. False when our node is not known yet. */
val Node?.isLicensedLocal: Boolean
    get() = this?.user?.is_licensed == true

/**
 * Whether our radio can address this node at all. An unlicensed radio takes the PKC path and refuses with
 * `PKI_SEND_FAIL_PUBLIC_KEY` when it holds no key for the destination, while a licensed radio never uses PKC and sends
 * on the channel, so it needs no key. A node whose NodeInfo has not arrived has no key on file, which is common rather
 * than exceptional.
 */
fun Node.hasDirectMessagePath(localIsLicensed: Boolean): Boolean = hasPKC || localIsLicensed

/**
 * Whether a direct message to this node can actually be delivered by our radio: the node accepts messages
 * ([isEffectivelyUnmessageable] is false) and our radio has a [hasDirectMessagePath] to it.
 */
fun Node.canDirectMessage(localIsLicensed: Boolean): Boolean =
    !isEffectivelyUnmessageable && hasDirectMessagePath(localIsLicensed)

/**
 * Whether to offer the direct-message action for this node.
 *
 * [canDirectMessage] with an escape hatch: a node we can no longer send to may still have a thread worth reading, so an
 * existing conversation keeps the action visible.
 */
fun Node.showsDirectMessageAction(hasConversation: Boolean, localIsLicensed: Boolean): Boolean =
    canDirectMessage(localIsLicensed) || hasConversation
