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
package org.meshtastic.feature.node.model

import org.meshtastic.core.database.model.Node
import org.meshtastic.core.navigation.Route
import org.meshtastic.core.service.ServiceAction
import org.meshtastic.feature.node.component.NodeMenuAction
import org.meshtastic.proto.Config

sealed interface NodeDetailAction {
    data class Navigate(val route: Route) : NodeDetailAction

    data class TriggerServiceAction(val action: ServiceAction) : NodeDetailAction

    data class HandleNodeMenuAction(val action: NodeMenuAction) : NodeDetailAction

    data object ShareContact : NodeDetailAction

    // Opens the compass sheet scoped to a target node and the user’s preferred units.
    data class OpenCompass(val node: Node, val displayUnits: Config.DisplayConfig.DisplayUnits) : NodeDetailAction
}
