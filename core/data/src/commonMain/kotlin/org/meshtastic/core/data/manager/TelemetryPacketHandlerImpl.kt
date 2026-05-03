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
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.nowSeconds
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.util.decodeOrNull
import org.meshtastic.core.model.util.toOneLiner
import org.meshtastic.core.repository.MeshConnectionManager
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.Notification
import org.meshtastic.core.repository.NotificationManager
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
    @Named("ServiceScope") private val scope: CoroutineScope,
) : TelemetryPacketHandler {

    private val batteryMutex = Mutex()
    private val batteryPercentCooldowns = mutableMapOf<Int, Long>()

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    override fun handleTelemetry(packet: MeshPacket, dataPacket: DataPacket, myNodeNum: Int) {
        val payload = packet.decoded?.payload ?: return
        val t =
            (Telemetry.ADAPTER.decodeOrNull(payload, Logger) ?: return).let {
                if (it.time == 0) it.copy(time = (dataPacket.time.milliseconds.inWholeSeconds).toInt()) else it
            }
        Logger.d { "Telemetry from ${packet.from}: ${Telemetry.ADAPTER.toOneLiner(t)}" }
        val fromNum = packet.from
        val isRemote = (fromNum != myNodeNum)
        if (!isRemote) {
            connectionManager.value.updateTelemetry(t)
        }

        nodeManager.updateNode(fromNum) { node: Node ->
            val metrics = t.device_metrics
            val environment = t.environment_metrics
            val power = t.power_metrics

            var nextNode = node
            when {
                metrics != null -> {
                    nextNode = nextNode.copy(deviceMetrics = metrics)
                    if (fromNum == myNodeNum || (isRemote && node.isFavorite)) {
                        if (
                            (metrics.voltage ?: 0f) > BATTERY_PERCENT_UNSUPPORTED &&
                            (metrics.battery_level ?: 0) <= BATTERY_PERCENT_LOW_THRESHOLD
                        ) {
                            scope.launch {
                                if (shouldBatteryNotificationShow(fromNum, t, myNodeNum)) {
                                    notificationManager.dispatch(
                                        Notification(
                                            title =
                                            getStringSuspend(
                                                Res.string.low_battery_title,
                                                nextNode.user.short_name,
                                            ),
                                            message =
                                            getStringSuspend(
                                                Res.string.low_battery_message,
                                                nextNode.user.long_name,
                                                nextNode.deviceMetrics.battery_level ?: 0,
                                            ),
                                            category = Notification.Category.Battery,
                                        ),
                                    )
                                }
                            }
                        } else {
                            scope.launch {
                                batteryMutex.withLock {
                                    if (batteryPercentCooldowns.containsKey(fromNum)) {
                                        batteryPercentCooldowns.remove(fromNum)
                                    }
                                }
                                notificationManager.cancel(nextNode.num)
                            }
                        }
                    }
                }

                environment != null -> nextNode = nextNode.copy(environmentMetrics = environment)

                power != null -> nextNode = nextNode.copy(powerMetrics = power)
            }

            val telemetryTime = if (t.time != 0) t.time else nextNode.lastHeard
            val newLastHeard = maxOf(nextNode.lastHeard, telemetryTime)
            nextNode.copy(lastHeard = newLastHeard)
        }
    }

    @Suppress("ReturnCount")
    private suspend fun shouldBatteryNotificationShow(fromNum: Int, t: Telemetry, myNodeNum: Int): Boolean {
        val isRemote = (fromNum != myNodeNum)
        var shouldDisplay = false
        var forceDisplay = false
        val metrics = t.device_metrics ?: return false
        val batteryLevel = metrics.battery_level ?: 0
        when {
            batteryLevel <= BATTERY_PERCENT_CRITICAL_THRESHOLD -> {
                shouldDisplay = true
                forceDisplay = true
            }

            batteryLevel == BATTERY_PERCENT_LOW_THRESHOLD -> shouldDisplay = true

            batteryLevel.mod(BATTERY_PERCENT_LOW_DIVISOR) == 0 && !isRemote -> shouldDisplay = true

            isRemote -> shouldDisplay = true
        }
        if (shouldDisplay) {
            val now = nowSeconds
            batteryMutex.withLock {
                if (!batteryPercentCooldowns.containsKey(fromNum)) batteryPercentCooldowns[fromNum] = 0L
                if ((now - batteryPercentCooldowns[fromNum]!!) >= BATTERY_PERCENT_COOLDOWN_SECONDS || forceDisplay) {
                    batteryPercentCooldowns[fromNum] = now
                    return true
                }
            }
        }
        return false
    }

    companion object {
        private const val BATTERY_PERCENT_UNSUPPORTED = 0.0
        private const val BATTERY_PERCENT_LOW_THRESHOLD = 20
        private const val BATTERY_PERCENT_LOW_DIVISOR = 5
        private const val BATTERY_PERCENT_CRITICAL_THRESHOLD = 5
        private const val BATTERY_PERCENT_COOLDOWN_SECONDS = 1500
    }
}
