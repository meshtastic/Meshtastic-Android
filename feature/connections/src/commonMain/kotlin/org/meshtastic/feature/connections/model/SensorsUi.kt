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

import org.meshtastic.feature.connections.model.host.SensorKind
import org.meshtastic.feature.connections.model.host.SensorUnread
import kotlin.time.Duration

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
