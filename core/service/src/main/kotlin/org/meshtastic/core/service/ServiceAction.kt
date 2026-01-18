/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package org.meshtastic.core.service

import org.meshtastic.core.database.model.Node
import org.meshtastic.proto.SharedContact

sealed class ServiceAction {
    data class GetDeviceMetadata(val destNum: Int) : ServiceAction()

    data class Favorite(val node: Node) : ServiceAction()

    data class Ignore(val node: Node) : ServiceAction()

    data class Mute(val node: Node) : ServiceAction()

    data class Reaction(val emoji: String, val replyId: Int, val contactKey: String) : ServiceAction()

    data class ImportContact(val contact: SharedContact) : ServiceAction()

    data class SendContact(val contact: SharedContact) : ServiceAction()
}
