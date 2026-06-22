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

import kotlinx.coroutines.flow.StateFlow

/**
 * Platform-abstracted view of currently-connected serial devices.
 *
 * Each key is the `rest` portion of an `InterfaceId.SERIAL` address (i.e. the address with its leading `'s'` prefix
 * stripped) — exactly the keys that [UsbRepository.serialDevices] emits on Android.
 *
 * Consumed by transport-recovery code (notably `SharedRadioInterfaceService.initStateListeners`) to detect when a
 * previously-selected serial device has been unplugged and replugged, so the transport can be brought back without
 * manual re-selection.
 *
 * Platforms without hot-plug observation (e.g. JVM/Desktop jSerialComm) return a perpetually-empty set.
 */
interface SerialDevicePresence {
    /**
     * The set of currently-connected serial device keys (the `rest` part of an `s$rest` SERIAL address).
     *
     * Implementations MUST emit a fresh set whenever the connected-device set changes. Equal sets MUST be deduped by
     * the underlying [StateFlow] semantics so collectors do not observe redundant emissions.
     */
    val deviceKeys: StateFlow<Set<String>>
}
