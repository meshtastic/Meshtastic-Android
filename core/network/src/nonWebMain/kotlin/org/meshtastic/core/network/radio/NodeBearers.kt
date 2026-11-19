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
package org.meshtastic.core.network.radio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import org.meshtastic.node.AirQualitySource
import org.meshtastic.node.BytesStore
import org.meshtastic.node.EnvironmentSource
import org.meshtastic.node.MeshLogger
import org.meshtastic.node.PositionSource
import org.meshtastic.node.PowerMetricsSource
import org.meshtastic.node.PowerSource
import org.meshtastic.node.host.BearerFactory
import org.meshtastic.node.host.ExtraRadioDevice
import org.meshtastic.node.host.HostState
import org.meshtastic.node.sensors.I2cBusState

/**
 * What a platform supplies for the in-process node: its bearer factory, its lifecycle signals and where the node's
 * files live. The Context never crosses into commonMain; Android and desktop each bind one of these.
 */
interface NodeBearers {
    /** Foreground, screen and power state the node's scan policy follows. */
    val hostSignals: StateFlow<HostState>

    /** The bearers this platform can build. Called once per transport start. */
    fun factory(signals: StateFlow<HostState>): BearerFactory

    /** The directory holding the node's identity, settings and node database, as an absolute path. */
    fun storesDir(): String

    /** Where the node remembers which host bearers (Wi-Fi Aware, audio, video) the user switched on. */
    fun hostBearerStore(): BytesStore

    /**
     * Where the node remembers which extra LoRa radios the user switched off, or null on a platform that builds one
     * radio.
     */
    fun extraRadioStore(): BytesStore? = null

    /** The device's position for the node's beacon, or null when the platform offers none. */
    val positionSource: PositionSource?
        get() = null

    /**
     * The sensors wired to this host, read for the node's telemetry while [scope] lives, or null when the platform
     * reads none. Called once per node start.
     */
    fun sensors(scope: CoroutineScope, logger: MeshLogger): NodeSensors? = null

    /** What the host's first LoRa radio is, as its configuration names it, or null on a host that says nothing. */
    fun primaryRadio(): PrimaryRadio? = null
}

/** The host's first LoRa radio: a USB stick or a board, by the [id] the host named it with, if any. */
data class PrimaryRadio(val device: ExtraRadioDevice, val id: String? = null)

/**
 * A host's readings as the sources a node takes; a source the host lacks is null. [power] is its battery, [position]
 * its GPS, which takes the place of [NodeBearers.positionSource] when set. [bus] is the I2C bus the other sensors are
 * read from as the node card shows it, null for a host that reads its sensors some other way.
 */
class NodeSensors(
    val environment: EnvironmentSource? = null,
    val airQuality: AirQualitySource? = null,
    val powerMetrics: PowerMetricsSource? = null,
    val bus: StateFlow<I2cBusState>? = null,
    val power: PowerSource? = null,
    val position: PositionSource? = null,
    /** Whether [position]'s GPS can run, and what the user has to change when it cannot; null with no GPS source. */
    val gps: StateFlow<NodeGpsState>? = null,
)

/** Whether the node's GPS can be asked for fixes, as node-android's `GpsAccess` reports it. */
enum class NodeGpsState {
    READY,
    NO_PERMISSION,
    APPROXIMATE_ONLY,
    LOCATION_OFF,
    NO_GPS,
}
