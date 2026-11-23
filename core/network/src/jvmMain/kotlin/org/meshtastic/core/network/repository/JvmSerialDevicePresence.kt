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
import org.meshtastic.core.network.availableSerialPortNames

/**
 * JVM/Desktop implementation of [SerialDevicePresence].
 *
 * Desktop serial publishes no hot-plug device set, so [deviceKeys] stays empty and [portExists] asks jSerialComm for
 * the system port name a desktop selection stores (`sttyUSB0`, `sCOM3`).
 */
@Single
class JvmSerialDevicePresence : SerialDevicePresence {
    override val deviceKeys: StateFlow<Set<String>> = MutableStateFlow<Set<String>>(emptySet()).asStateFlow()

    override fun portExists(key: String): Boolean = key in availableSerialPortNames()
}
