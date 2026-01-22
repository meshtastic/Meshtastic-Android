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
    private var scope: CoroutineScope? = null

    fun start(coroutineScope: CoroutineScope) {
        scope = coroutineScope
        nodeManagementActions.start(coroutineScope)
        nodeRequestActions.start(coroutineScope)
    }

    fun handleNodeMenuAction(action: NodeMenuAction) {
        when (action) {
            is NodeMenuAction.Remove -> nodeManagementActions.removeNode(action.node.num)
            is NodeMenuAction.Ignore -> nodeManagementActions.ignoreNode(action.node)
            is NodeMenuAction.Mute -> nodeManagementActions.muteNode(action.node)
            is NodeMenuAction.Favorite -> nodeManagementActions.favoriteNode(action.node)
            is NodeMenuAction.RequestUserInfo ->
                nodeRequestActions.requestUserInfo(action.node.num, action.node.user.longName)
            is NodeMenuAction.RequestNeighborInfo ->
                nodeRequestActions.requestNeighborInfo(action.node.num, action.node.user.longName)
            is NodeMenuAction.RequestPosition ->
                nodeRequestActions.requestPosition(action.node.num, action.node.user.longName)
            is NodeMenuAction.RequestTelemetry ->
                nodeRequestActions.requestTelemetry(action.node.num, action.node.user.longName, action.type)
            is NodeMenuAction.TraceRoute ->
                nodeRequestActions.requestTraceroute(action.node.num, action.node.user.longName)
            else -> {}
        }
    }

    fun setNodeNotes(nodeNum: Int, notes: String) {
        nodeManagementActions.setNodeNotes(nodeNum, notes)
    }

    fun requestPosition(destNum: Int, longName: String, position: Position) {
        nodeRequestActions.requestPosition(destNum, longName, position)
    }

    fun requestUserInfo(destNum: Int, longName: String) {
        nodeRequestActions.requestUserInfo(destNum, longName)
    }

    fun requestNeighborInfo(destNum: Int, longName: String) {
        nodeRequestActions.requestNeighborInfo(destNum, longName)
    }

    fun requestTelemetry(destNum: Int, longName: String, type: TelemetryType) {
        nodeRequestActions.requestTelemetry(destNum, longName, type)
    }

    fun requestTraceroute(destNum: Int, longName: String) {
        nodeRequestActions.requestTraceroute(destNum, longName)
    }
}
