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
package org.meshtastic.core.service

import org.meshtastic.core.model.Position
import org.meshtastic.core.repository.CommandSender
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.RequestController
import org.meshtastic.core.repository.UiPrefs
import org.meshtastic.proto.AdminMessage

/**
 * [RequestController] implementation: position, traceroute, telemetry, user info, and metadata "pull" queries.
 *
 * Focused collaborator of [DirectRadioControllerImpl]. Mirrors the SDK's `TelemetryApi`/`RoutingApi` surface.
 */
internal class RequestControllerImpl(
    private val commandSender: CommandSender,
    private val nodeManager: NodeManager,
    private val uiPrefs: UiPrefs,
) : RequestController {

    private val myNodeNum: Int
        get() = nodeManager.myNodeNum.value ?: 0

    override suspend fun refreshMetadata(destNum: Int) {
        commandSender.sendAdmin(destNum, wantResponse = true) { AdminMessage(get_device_metadata_request = true) }
    }

    override suspend fun requestPosition(destNum: Int, currentPosition: Position) {
        if (destNum == nodeManager.myNodeNum.value) return
        val provideLocation = uiPrefs.shouldProvideNodeLocation(myNodeNum).value
        // Position(0.0, 0.0, 0) is the protocol-level "no position" sentinel.
        val resolvedPosition =
            if (provideLocation) {
                currentPosition.takeIf { it.isValid() }
                    ?: nodeManager.nodeDBbyNodeNum[myNodeNum]?.position?.let { Position(it) }?.takeIf { it.isValid() }
                    ?: Position(0.0, 0.0, 0)
            } else {
                Position(0.0, 0.0, 0)
            }
        commandSender.requestPosition(destNum, resolvedPosition)
    }

    override suspend fun requestUserInfo(destNum: Int) {
        if (destNum != nodeManager.myNodeNum.value) {
            commandSender.requestUserInfo(destNum)
        }
    }

    override suspend fun requestTraceroute(requestId: Int, destNum: Int) {
        commandSender.requestTraceroute(requestId, destNum)
    }

    override suspend fun requestTelemetry(requestId: Int, destNum: Int, typeValue: Int) {
        commandSender.requestTelemetry(requestId, destNum, typeValue)
    }

    override suspend fun requestNeighborInfo(requestId: Int, destNum: Int) {
        commandSender.requestNeighborInfo(requestId, destNum)
    }
}
