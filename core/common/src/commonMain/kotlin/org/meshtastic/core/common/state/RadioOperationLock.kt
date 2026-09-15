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
     * (`radioController.setDeviceAddress("n")`), and the post-flash verification deliberately reconnects the radio, so
     * suppression would block the very step it is meant to protect.
     */
    FirmwareUpdate(suppressesTransport = false),

    /** A Local Mesh Discovery preset sweep. Needs the transport, so it must never suppress it. */
    DiscoveryScan(suppressesTransport = false),
}

/**
 * One holder's claim on a [RadioOperation]. Releasing a lease affects only that holder.
 *
 * Identity matters: two concurrent claims on the same operation produce two distinct leases, so a late release from an
 * abandoned holder cannot retire a live one.
 */
class OperationLease internal constructor(internal val id: Long, val operation: RadioOperation)

/**
 * Tracks which [RadioOperation]s are in flight, so the rest of the app can keep out of their way.
 *
 * Three consumers, which is why one lock replaced the single-purpose `FirmwareMaintenanceLock` it grew out of:
 * `SharedRadioInterfaceService` reads [suppressesTransport] to stay off the device, `MeshService` reads [holders] to
 * refuse to stop itself and to hold a wake lock, and the service notification reads it to say what is running.
 *
 * Holders are tracked as individual leases rather than a set of operation types. The set was wrong: a caller that
 * cancels a job and immediately starts a replacement — which `FirmwareUpdateViewModel` does, without joining — lets the
 * cancelled job's `finally` run *after* the replacement has acquired, and a shared entry would be retired out from
 * under the live holder. Two holders of the same operation are now independent.
 *
 * Lives in `:core:common` because the parties sit in modules that cannot see each other: the flows that take an
 * operation are in `:feature:firmware` and `:feature:discovery`, and the code that must respect it is in
 * `:core:service`.
 *
 * Process-local by design. A process death drops every lease, which is correct — nothing is in flight any more — but it
 * means this narrows the window in which an interrupted flash can strand a device rather than closing it.
 */
@Single
class RadioOperationLock {
    private val nextLeaseId = atomic(0L)
    private val _holders = MutableStateFlow<Map<Long, RadioOperation>>(emptyMap())

    /** Every live lease, keyed by lease id. Emits on every acquire and release. */
    val holders: StateFlow<Map<Long, RadioOperation>> = _holders.asStateFlow()

    /** The distinct operations currently in flight. */
    val activeOperations: Set<RadioOperation>
        get() = _holders.value.values.toSet()

    /** True while any operation is in flight. */
    val isActive: Boolean
        get() = _holders.value.isNotEmpty()

    /** True while an operation that needs the transport off the device is in flight. */
    val suppressesTransport: Boolean
        get() = _holders.value.values.any(RadioOperation::suppressesTransport)

    /** Takes [operation] and returns the lease that holds it. Each call is a distinct holder. */
    fun acquire(operation: RadioOperation): OperationLease {
        val lease = OperationLease(nextLeaseId.incrementAndGet(), operation)
        _holders.update { it + (lease.id to operation) }
        return lease
    }

    /**
     * Releases [lease]. Idempotent, and null-safe so a caller can release a hold it may never have taken.
     *
     * Releasing a stale lease is harmless: its id is already gone, and other holders of the same operation keep theirs.
     */
    fun release(lease: OperationLease?) {
        val id = lease?.id ?: return
        _holders.update { it - id }
    }

    /** Runs [block] with [operation] held, releasing that exact lease even on failure or cancellation. */
    suspend fun <T> withOperation(operation: RadioOperation, block: suspend () -> T): T {
        val lease = acquire(operation)
        return try {
            block()
        } finally {
            release(lease)
        }
    }
}
