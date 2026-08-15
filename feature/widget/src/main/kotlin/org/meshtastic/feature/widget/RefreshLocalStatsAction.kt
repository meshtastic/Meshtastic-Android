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
package org.meshtastic.feature.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import co.touchlab.kermit.Logger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.meshtastic.core.model.TelemetryType
import org.meshtastic.core.repository.CommandSender
import org.meshtastic.core.repository.LocalNodeUnavailableException
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.PacketQueueRejectedException

internal suspend fun runTelemetryRequestBestEffort(type: TelemetryType, request: suspend () -> Unit) {
    try {
        request()
    } catch (e: PacketQueueRejectedException) {
        Logger.w(e) { "RefreshLocalStatsAction: $type request rejected by packet queue" }
    } catch (e: LocalNodeUnavailableException) {
        Logger.w(e) { "RefreshLocalStatsAction: $type request skipped because the local node became unavailable" }
    }
}

internal suspend fun requestLocalStatsRefresh(
    myNodeNum: Int?,
    request: suspend (nodeNum: Int, type: TelemetryType) -> Unit,
) {
    if (myNodeNum == null) return
    listOf(TelemetryType.LOCAL_STATS, TelemetryType.DEVICE).forEach { type ->
        runTelemetryRequestBestEffort(type) { request(myNodeNum, type) }
    }
}

class RefreshLocalStatsAction :
    ActionCallback,
    KoinComponent {

    private val commandSender: CommandSender by inject()
    private val nodeManager: NodeManager by inject()

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val myNodeNum = nodeManager.myNodeNum.value
        if (myNodeNum == null) {
            Logger.w { "RefreshLocalStatsAction: myNodeNum is null, skipping telemetry request" }
            return
        }

        requestLocalStatsRefresh(myNodeNum) { nodeNum, type ->
            commandSender.requestTelemetry(commandSender.generatePacketId(), nodeNum, type.ordinal)
        }
    }
}
