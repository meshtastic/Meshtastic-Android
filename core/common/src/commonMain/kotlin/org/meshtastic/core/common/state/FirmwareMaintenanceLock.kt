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
package org.meshtastic.core.common.state

import kotlinx.atomicfu.atomic
import org.koin.core.annotation.Single

/**
 * Marks a USB firmware-maintenance sequence as in flight, so the radio transport does not fight it for the device.
 *
 * A factory erase leaves the device enumerating as a bare CDC port with no Meshtastic protocol on it. The serial
 * transport will happily bind to that — its device lookup falls back to whatever probed device it can find, not just
 * the saved address — and the environmental-recovery listeners re-enter transport startup whenever Bluetooth or the
 * network flips. Both would claim the port the maintenance flow needs.
 *
 * Lives in `:core:common` because the two parties sit in modules that cannot see each other: the flow that takes the
 * lock is in `:feature:firmware`, and the code that must respect it is in `:core:service`.
 *
 * The consequence of ignoring the lock is bounded rather than destructive — a transport that claims the port asserts
 * DTR itself, which happens to unblock the erase — but it makes the flow's own signal unreliable, and a mesh handshake
 * against erase firmware is noise nobody needs to debug.
 */
@Single
class FirmwareMaintenanceLock {
    private val active = atomic(false)

    /** True while a maintenance sequence holds the lock. */
    val isActive: Boolean
        get() = active.value

    /** Takes the lock. Idempotent — re-taking an already-held lock is a no-op. */
    fun acquire() {
        active.value = true
    }

    /** Releases the lock. Must run even on failure, so callers use `try`/`finally`. */
    fun release() {
        active.value = false
    }
}
