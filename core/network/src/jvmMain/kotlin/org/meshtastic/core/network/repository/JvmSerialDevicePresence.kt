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
package org.meshtastic.core.network.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.core.annotation.Single
import org.meshtastic.core.network.availableSerialPorts
import java.io.File

/**
 * JVM/Desktop implementation of [SerialDevicePresence].
 *
 * Desktop serial publishes no hot-plug device set, so [deviceKeys] stays empty. [portExists] takes the port a desktop
 * selection stores and answers through [serialPortExists].
 */
@Single
class JvmSerialDevicePresence : SerialDevicePresence {
    override val deviceKeys: StateFlow<Set<String>> = MutableStateFlow<Set<String>>(emptySet()).asStateFlow()

    override fun portExists(key: String): Boolean = serialPortExists(key) { availableSerialPorts().map { it.name } }
}

/**
 * Whether the port a stored serial address names exists. A path (a Linux `/dev/serial/by-id` link) exists while it
 * resolves, and a system port name (`ttyUSB0`, `cu.usbmodem1101`, `tty.usbmodem1101`) while its node in [devDir] does,
 * which is where serial open looks for it whether or not the port listing shows it. A Windows `COMn` name is not a
 * file, so it exists while [listedNames] has it.
 */
internal fun serialPortExists(
    key: String,
    isWindows: Boolean = System.getProperty("os.name", "").lowercase().startsWith("windows"),
    devDir: File = File("/dev"),
    listedNames: () -> List<String>,
): Boolean = when {
    key.startsWith("/") -> File(key).exists()
    isWindows -> key in listedNames()
    else -> File(devDir, key).exists()
}
