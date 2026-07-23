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
package org.meshtastic.core.ble

import kotlin.time.Duration

/** Known reasons a BLE discovery scan failed before Android registered the scanner. */
enum class BleScanStartFailureReason(val androidCode: String, val description: String) {
    ApplicationRegistrationFailed(
        androidCode = "SCAN_FAILED_APPLICATION_REGISTRATION_FAILED(2)",
        description = "Android could not register the app for BLE scanning",
    ),
    MissingScanPermission(
        androidCode = "MISSING_SCAN_PERMISSION",
        description = "A runtime permission required for BLE scanning is not granted",
    ),
    ScanningTooFrequently(
        androidCode = "SCAN_FAILED_SCANNING_TOO_FREQUENTLY(6)",
        description = "Android rejected a BLE scan because the app reached its scan-start quota",
    ),
}

/** A discovery scan-start failure. No advertisements can be delivered until a future scan starts successfully. */
class BleScanStartException(val reason: BleScanStartFailureReason, cause: Throwable, val retryAfter: Duration? = null) :
    IllegalStateException("BLE scan could not start: ${reason.androidCode}", cause)
