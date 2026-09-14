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

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.koin.core.annotation.Single

/**
 * A long-running operation that owns the radio and must not be interrupted halfway.
 *
 * [suppressesTransport] is per-operation rather than blanket because the operations genuinely differ: a USB factory
 * erase needs the transport to stay off the port, while a discovery scan needs the transport to keep running.
 */
enum class RadioOperation(val suppressesTransport: Boolean) {
    /**
     * A USB firmware-maintenance sequence (factory erase).
     *
     * A factory erase leaves the device enumerating as a bare CDC port with no Meshtastic protocol on it. The serial
     * transport will happily bind to that — its device lookup falls back to whatever probed device it can find, not
     * just the saved address — and the environmental-recovery listeners re-enter transport startup whenever Bluetooth
     * or the network flips. Both would claim the port the maintenance flow needs.
     */
    FirmwareMaintenance(suppressesTransport = true),

    /**
     * An OTA/DFU flash.
     *
     * Does not suppress the transport: the flow already frees it by deselecting the device
     * (`radioController.setDeviceAddress("n")`), and widening suppression to this operation would change reconnection
     * behaviour well beyond keeping the process alive.
     */
    FirmwareUpdate(suppressesTransport = false),

    /** A Local Mesh Discovery preset sweep. Needs the transport, so it must never suppress it. */
    DiscoveryScan(suppressesTransport = false),
}

/**
 * Tracks which [RadioOperation]s are in flight, so the rest of the app can keep out of their way.
 *
 * Three consumers, which is why one lock replaced the single-purpose `FirmwareMaintenanceLock` it grew out of:
 * `SharedRadioInterfaceService` reads [suppressesTransport] to stay off the device, `MeshService` reads [isActive] to
 * refuse to stop itself and to hold a wake lock, and the service notification reads [active] to say what is running.
 *
 * Lives in `:core:common` because the parties sit in modules that cannot see each other: the flows that take an
 * operation are in `:feature:firmware` and `:feature:discovery`, and the code that must respect it is in
 * `:core:service`.
 *
 * Process-local by design. A process death drops every operation, which is correct — nothing is in flight any more —
 * but it means this narrows the window in which an interrupted flash can strand a device rather than closing it.
 */
@Single
class RadioOperationLock {
    private val _active = MutableStateFlow<Set<RadioOperation>>(emptySet())

    /** The operations currently held. Emits on every acquire and release. */
    val active: StateFlow<Set<RadioOperation>> = _active.asStateFlow()

    /** True while any operation is in flight. */
    val isActive: Boolean
        get() = _active.value.isNotEmpty()

    /** True while an operation that needs the transport off the device is in flight. */
    val suppressesTransport: Boolean
        get() = _active.value.any(RadioOperation::suppressesTransport)

    /** Takes [operation]. Idempotent — re-taking an already-held operation is a no-op. */
    fun acquire(operation: RadioOperation) {
        _active.update { it + operation }
    }

    /** Releases [operation]. Idempotent, and safe to call for an operation that was never taken. */
    fun release(operation: RadioOperation) {
        _active.update { it - operation }
    }

    /**
     * Runs [block] with [operation] held, releasing it even on failure or cancellation.
     *
     * Preferred over [acquire]/[release] wherever the work fits in one block. The USB maintenance flow cannot use it —
     * it spans several passes and suspend boundaries — so the explicit pair stays public.
     *
     * **Do not nest the same [operation].** Holders are a set, not a counter, so an inner block's release drops the
     * operation while the outer one is still running. Two holds of the same operation must be sequential, which is how
     * the firmware flow uses it: the flash releases before the post-flash verification takes it again, with no suspend
     * point in between for anything to observe the gap. Different operations may overlap freely.
     */
    suspend fun <T> withOperation(operation: RadioOperation, block: suspend () -> T): T {
        acquire(operation)
        return try {
            block()
        } finally {
            release(operation)
        }
    }
}
