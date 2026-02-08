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
package org.meshtastic.feature.node.component

import org.meshtastic.core.database.model.Node
import org.meshtastic.core.model.TelemetryType

sealed class NodeMenuAction {
    data class Remove(val node: Node) : NodeMenuAction()

    data class Ignore(val node: Node) : NodeMenuAction()

    data class Mute(val node: Node) : NodeMenuAction()

    data class Favorite(val node: Node) : NodeMenuAction()

    data class DirectMessage(val node: Node) : NodeMenuAction()

    data class RequestUserInfo(val node: Node) : NodeMenuAction()

    data class RequestNeighborInfo(val node: Node) : NodeMenuAction()

    data class RequestPosition(val node: Node) : NodeMenuAction()

    data class RequestTelemetry(val node: Node, val type: TelemetryType) : NodeMenuAction()

    data class TraceRoute(val node: Node) : NodeMenuAction()

    data class MoreDetails(val node: Node) : NodeMenuAction()

    data class Share(val node: Node) : NodeMenuAction()

    data class Reboot(val node: Node) : NodeMenuAction()

    data class Shutdown(val node: Node) : NodeMenuAction()
}
