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
package org.meshtastic.core.service

import org.meshtastic.core.common.util.isValidDeviceAddress

/** What the mesh service should do once the selected-device address has settled. */
enum class ServiceStayAliveDecision {
    /** A device is selected; run as a normal foreground service. */
    STAY_FOR_DEVICE,

    /**
     * No device is selected, but a long-running radio operation is in flight and needs this process alive.
     *
     * The firmware update flow deselects the device (`setDeviceAddress("n")`) to free the transport before it flashes.
     * That deselect is what makes this case real: without it the service would stop roughly five seconds into a flash
     * that runs for minutes, taking the foreground notification and the wake lock with it.
     */
    STAY_FOR_OPERATION,

    /** Nothing is selected and nothing is running, so the service has no reason to exist. */
    STOP,
}

/**
 * Decides whether the mesh service keeps running once the address flow has settled.
 *
 * Pure and platform-agnostic so the gate is unit-testable without standing up a `Service`, in the same shape as
 * [bootReconnectDecision].
 *
 * Deliberately not consulted by the service's other two teardown paths. A held operation must not keep alive a service
 * that failed dependency injection or could not enter the foreground — in the latter case ActivityManager has already
 * armed the pending-start watchdog, and refusing to stop would turn a recoverable refusal into a process kill.
 */
fun serviceStayAliveDecision(address: String?, hasActiveRadioOperation: Boolean): ServiceStayAliveDecision = when {
    isValidDeviceAddress(address) -> ServiceStayAliveDecision.STAY_FOR_DEVICE
    hasActiveRadioOperation -> ServiceStayAliveDecision.STAY_FOR_OPERATION
    else -> ServiceStayAliveDecision.STOP
}
