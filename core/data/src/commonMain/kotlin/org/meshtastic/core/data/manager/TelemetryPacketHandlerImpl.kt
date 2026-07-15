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
package org.meshtastic.core.data.manager

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.clampTimestampToNow
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.util.decodeOrNull
import org.meshtastic.core.model.util.toOneLiner
import org.meshtastic.core.repository.MeshConnectionManager
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.Notification
import org.meshtastic.core.repository.NotificationManager
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioSessionContext
import org.meshtastic.core.repository.TelemetryPacketHandler
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.getStringSuspend
import org.meshtastic.core.resources.low_battery_message
import org.meshtastic.core.resources.low_battery_title
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.Telemetry
import kotlin.time.Duration.Companion.milliseconds

/**
 * Implementation of [TelemetryPacketHandler] that processes telemetry packets and manages battery-level notifications
 * with cooldown logic.
 */
@Single
class TelemetryPacketHandlerImpl(
    private val nodeManager: NodeManager,
    private val connectionManager: Lazy<MeshConnectionManager>,
    private val notificationManager: NotificationManager,
    private val radioInterfaceService: RadioInterfaceService,
    @Named("ServiceScope") private val scope: CoroutineScope,
) : TelemetryPacketHandler {

    private val batteryMutex = Mutex()
    private val notifiedNodes = mutableSetOf<Int>()

    @Suppress("LongMethod", "CyclomaticComplexMethod", "ReturnCount")
    override fun handleTelemetry(
        packet: MeshPacket,
        dataPacket: DataPacket,
        myNodeNum: Int,
        session: RadioSessionContext,
    ) {
        val payload = packet.decoded?.payload ?: return
        val telemetry =
            (Telemetry.ADAPTER.decodeOrNull(payload, Logger) ?: return).let {
                if (it.time == 0) it.copy(time = (dataPacket.time.milliseconds.inWholeSeconds).toInt()) else it
            }
        Logger.d { "Telemetry from ${packet.from}: ${Telemetry.ADAPTER.toOneLiner(telemetry)}" }
        val fromNum = packet.from
        val isRemote = fromNum != myNodeNum
        if (!isRemote) connectionManager.value.updateTelemetry(telemetry)

        val transform: (Node) -> Node = { node ->
            var nextNode = node
            telemetry.device_metrics?.let { nextNode = nextNode.copy(deviceMetrics = it) }
            telemetry.environment_metrics?.let { nextNode = nextNode.copy(environmentMetrics = it) }
            telemetry.power_metrics?.let { nextNode = nextNode.copy(powerMetrics = it) }
            telemetry.air_quality_metrics?.let { nextNode = nextNode.copy(airQualityMetrics = it) }
            val telemetryTime = if (telemetry.time != 0) telemetry.time else nextNode.lastHeard
            val newLastHeard = clampTimestampToNow(maxOf(nextNode.lastHeard, telemetryTime))
            nextNode.copy(lastHeard = newLastHeard)
        }
        nodeManager.updateNodeForSession(fromNum, session, transform = transform)

        val metrics = telemetry.device_metrics ?: return
        val updatedNode = nodeManager.nodeDBbyNodeNum[fromNum] ?: return
        if (fromNum != myNodeNum && !updatedNode.isFavorite) return

        if (
            (metrics.voltage ?: 0f) > BATTERY_PERCENT_UNSUPPORTED &&
            (metrics.battery_level ?: 0) <= BATTERY_PERCENT_LOW_THRESHOLD
        ) {
            radioInterfaceService.launchSessionWork(scope, session) {
                if (shouldBatteryNotificationShow(fromNum, telemetry, myNodeNum)) {
                    notificationManager.dispatch(
                        Notification(
                            title = getStringSuspend(Res.string.low_battery_title, updatedNode.user.short_name),
                            message =
                            getStringSuspend(
                                Res.string.low_battery_message,
                                updatedNode.user.long_name,
                                updatedNode.deviceMetrics.battery_level ?: 0,
                            ),
                            category = Notification.Category.Battery,
                        ),
                    )
                }
            }
        } else {
            radioInterfaceService.launchSessionWork(scope, session) {
                batteryMutex.withLock { notifiedNodes.remove(fromNum) }
                notificationManager.cancel(updatedNode.num)
            }
        }
    }

    @Suppress("UnusedParameter")
    private suspend fun shouldBatteryNotificationShow(fromNum: Int, t: Telemetry, myNodeNum: Int): Boolean {
        batteryMutex.withLock {
            if (fromNum in notifiedNodes) return false
            notifiedNodes.add(fromNum)
        }
        return true
    }

    companion object {
        private const val BATTERY_PERCENT_UNSUPPORTED = 0.0
        private const val BATTERY_PERCENT_LOW_THRESHOLD = 20
    }
}
