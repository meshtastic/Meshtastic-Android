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
package org.meshtastic.feature.firmware

import kotlinx.coroutines.flow.Flow

interface FirmwareUsbManager {
    fun deviceDetachFlow(): Flow<Unit>

    /**
     * Records which serial ports are present, so a port that appears *after* a UF2 write can be told apart from one
     * that was already there.
     *
     * Snapshot-then-diff rather than matching a VID/PID table: bootloader-mode ids are per-board and collide (four
     * OTAFIX boards share `239A/0029`), and identifying by serial number needs a permission grant we may not hold yet.
     */
    suspend fun serialPortKeys(): Set<String>

    /**
     * Opens the port that appeared since [excluding] was captured and holds DTR asserted, so firmware waiting on a host
     * will run.
     *
     * The nRF52 factory-erase image blocks in `while (!Serial)` before `InternalFS.format()`, and `Serial` only becomes
     * truthy once DTR is asserted — without this the erase never starts. A failure here is therefore safe: the image is
     * written but has destroyed nothing.
     *
     * @return true when a port was claimed and DTR held.
     */
    suspend fun unblockCdcPort(excluding: Set<String>, waitMillis: Long, holdMillis: Long): Boolean

    /**
     * Waits up to [waitMillis] for a serial device to be present and ensures the app holds USB permission for it,
     * prompting the user when needed.
     *
     * Post-update verification depends on this: every USB re-enumeration is a new device identity to Android, so the
     * grant earned before an update does not carry across the device's reboot — without a fresh one the transport's
     * auto-recovery fails with SecurityException and verification times out on a healthy device.
     *
     * Advisory: reconnection still rides the transport's own recovery; a denial just means verification times out as it
     * would have anyway.
     */
    suspend fun ensureSerialPermission(waitMillis: Long): Boolean
}
