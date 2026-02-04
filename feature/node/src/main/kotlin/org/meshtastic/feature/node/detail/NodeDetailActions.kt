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
package org.meshtastic.feature.node.detail

import kotlinx.coroutines.CoroutineScope
import org.meshtastic.core.model.Position
import org.meshtastic.core.model.TelemetryType
import org.meshtastic.feature.node.component.NodeMenuAction
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NodeDetailActions
@Inject
constructor(
    private val nodeManagementActions: NodeManagementActions,
    private val nodeRequestActions: NodeRequestActions,
) {
    fun handleNodeMenuAction(scope: CoroutineScope, action: NodeMenuAction) {
        when (action) {
            is NodeMenuAction.Remove -> nodeManagementActions.removeNode(scope, action.node.num)
            is NodeMenuAction.Ignore -> nodeManagementActions.ignoreNode(scope, action.node)
            is NodeMenuAction.Mute -> nodeManagementActions.muteNode(scope, action.node)
            is NodeMenuAction.Favorite -> nodeManagementActions.favoriteNode(scope, action.node)
            is NodeMenuAction.RequestUserInfo ->
                nodeRequestActions.requestUserInfo(scope, action.node.num, action.node.user.long_name ?: "")
            is NodeMenuAction.RequestNeighborInfo ->
                nodeRequestActions.requestNeighborInfo(scope, action.node.num, action.node.user.long_name ?: "")
            is NodeMenuAction.RequestPosition ->
                nodeRequestActions.requestPosition(scope, action.node.num, action.node.user.long_name ?: "")
            is NodeMenuAction.RequestTelemetry ->
                nodeRequestActions.requestTelemetry(
                    scope,
                    action.node.num,
                    action.node.user.long_name ?: "",
                    action.type,
                )
            is NodeMenuAction.TraceRoute ->
                nodeRequestActions.requestTraceroute(scope, action.node.num, action.node.user.long_name ?: "")
            else -> {}
        }
    }

    fun setNodeNotes(scope: CoroutineScope, nodeNum: Int, notes: String) {
        nodeManagementActions.setNodeNotes(scope, nodeNum, notes)
    }

    fun requestPosition(scope: CoroutineScope, destNum: Int, longName: String, position: Position) {
        nodeRequestActions.requestPosition(scope, destNum, longName, position)
    }

    fun requestUserInfo(scope: CoroutineScope, destNum: Int, longName: String) {
        nodeRequestActions.requestUserInfo(scope, destNum, longName)
    }

    fun requestNeighborInfo(scope: CoroutineScope, destNum: Int, longName: String) {
        nodeRequestActions.requestNeighborInfo(scope, destNum, longName)
    }

    fun requestTelemetry(scope: CoroutineScope, destNum: Int, longName: String, type: TelemetryType) {
        nodeRequestActions.requestTelemetry(scope, destNum, longName, type)
    }

    fun requestTraceroute(scope: CoroutineScope, destNum: Int, longName: String) {
        nodeRequestActions.requestTraceroute(scope, destNum, longName)
    }
}
