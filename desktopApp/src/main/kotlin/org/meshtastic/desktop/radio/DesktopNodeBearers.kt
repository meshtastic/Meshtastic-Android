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
package org.meshtastic.desktop.radio

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.meshtastic.core.database.desktopDataDir
import org.meshtastic.core.network.radio.NodeBearers
import org.meshtastic.core.network.radio.NodeSensors
import org.meshtastic.node.BytesStore
import org.meshtastic.node.MeshLogger
import org.meshtastic.node.desktop.JvmBearerFactory
import org.meshtastic.node.desktop.parseExtraLoraRadios
import org.meshtastic.node.host.BearerFactory
import org.meshtastic.node.host.HostState
import org.meshtastic.node.sensors.I2cSensorSources
import org.meshtastic.node.sensors.linuxI2cBus
import org.meshtastic.node.store.file.AtomicFileStore
import java.io.File

/**
 * The desktop half of the in-process node. Each data directory is its own node, so two app instances on separate
 * `MESHTASTIC_DATA_DIR`s are two nodes. The window is always considered visible and the host always in front.
 *
 * Registered in [org.meshtastic.desktop.di.DesktopRuntimeModule]; no `@Single` here, to avoid double registration with
 * the desktop component scan.
 */
class DesktopNodeBearers : NodeBearers {
    private val nodeDir: File
        get() = File(desktopDataDir(), NODE_DIR)

    override val hostSignals: StateFlow<HostState> = MutableStateFlow(HostState())

    override fun factory(signals: StateFlow<HostState>): BearerFactory = JvmBearerFactory(
        signals,
        extraLora =
        parseExtraLoraRadios(System.getenv(LORA_EXTRA_VAR), System.getenv(LORA_BOARD_VAR), LORA_EXTRA_VAR),
    )

    override fun storesDir(): String = nodeDir.path

    override fun hostBearerStore(): BytesStore = AtomicFileStore(File(nodeDir, HOST_BEARERS_FILE).path)

    /** The sensors on the I2C bus `MESH_I2C_DEVICE` names, such as `/dev/i2c-1` on a Raspberry Pi; none when unset. */
    override fun sensors(scope: CoroutineScope, logger: MeshLogger): NodeSensors? =
        i2cSensors(System.getenv(I2C_DEVICE_VAR), scope, logger)

    private companion object {
        const val NODE_DIR = "node"
        const val HOST_BEARERS_FILE = "host-bearers.bin"
        const val LORA_EXTRA_VAR = "MESH_LORA_EXTRA"
        const val LORA_BOARD_VAR = "MESH_LORA_BOARD"
        const val I2C_DEVICE_VAR = "MESH_I2C_DEVICE"
    }
}

/**
 * The sensors on the I2C bus at [path] as the node's telemetry sources, scanned off the caller's thread so a bus that
 * is slow or missing never holds the node; null for no path. A bus that will not open is logged once and reads nothing.
 */
internal fun i2cSensors(
    path: String?,
    scope: CoroutineScope,
    logger: MeshLogger,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
): NodeSensors? {
    val device = path?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val bus = I2cSensorSources(scope, dispatcher, logger = logger) { linuxI2cBus(device) }
    return NodeSensors(bus.environment, bus.airQuality, bus.powerMetrics, bus.bus)
}
