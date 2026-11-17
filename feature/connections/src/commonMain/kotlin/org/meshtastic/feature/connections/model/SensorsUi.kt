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
package org.meshtastic.feature.connections.model

import org.meshtastic.core.common.util.formatString
import org.meshtastic.node.sensors.I2cBusState
import org.meshtastic.node.sensors.I2cSensorState
import org.meshtastic.node.sensors.SensorKind
import org.meshtastic.node.sensors.SensorUnread
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** The host's I2C sensor bus as the node card's sensors section shows it. */
sealed interface SensorsUi {
    data object Scanning : SensorsUi

    data class BusFailed(val reason: String) : SensorsUi

    data object NoneFound : SensorsUi

    data class Found(val sensors: List<SensorRowUi>) : SensorsUi
}

/**
 * One device that answered the scan. [name] is its driver's name for it, else the scan's, and null for a device nothing
 * names; [address] is its I2C address as `0x48`; [feeds] is the telemetry its readings go into.
 */
data class SensorRowUi(val name: String?, val address: String, val feeds: List<SensorKind>, val state: SensorRowState)

sealed interface SensorRowState {
    /** Set up, and no read has run yet. */
    data object Waiting : SensorRowState

    /** [sinceGoodRead] is whole minutes as the bus last reported, null under a minute. */
    data class Reading(val sinceGoodRead: Duration?) : SensorRowState

    /** [sinceGoodRead] is as [Reading]'s, and null too for a sensor that has never read well. */
    data class Failing(val error: String, val sinceGoodRead: Duration?) : SensorRowState

    data class Unread(val reason: SensorUnread) : SensorRowState
}

fun I2cBusState.toSensorsUi(): SensorsUi = when (this) {
    I2cBusState.Scanning -> SensorsUi.Scanning

    is I2cBusState.Failed -> SensorsUi.BusFailed(reason)

    is I2cBusState.Scanned ->
        if (sensors.isEmpty()) SensorsUi.NoneFound else SensorsUi.Found(sensors.map { it.toRow() })
}

private fun I2cSensorState.toRow(): SensorRowUi {
    val error = lastError
    val unread = unread
    return SensorRowUi(
        name = sensorName ?: name,
        address = formatString("0x%02x", address),
        feeds = kinds.sorted(),
        state =
        when {
            unread != null -> SensorRowState.Unread(unread)
            error != null -> SensorRowState.Failing(error, sinceGoodRead())
            reads.values.all { it.isPending } -> SensorRowState.Waiting
            else -> SensorRowState.Reading(sinceGoodRead())
        },
    )
}

/** The age of the newest good read of any kind, in whole minutes so a fresh state reads the same as the last. */
private fun I2cSensorState.sinceGoodRead(): Duration? =
    reads.values.mapNotNull { it.sinceLastSuccess }.minOrNull()?.inWholeMinutes?.takeIf { it > 0 }?.minutes
