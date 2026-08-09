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

import org.meshtastic.core.common.log.ExpectedCondition
import kotlin.time.Duration

/**
 * Known reasons a BLE discovery scan failed before Android registered the scanner.
 *
 * Every reason here is an environment state — a permission not granted, a radio switched off, an OS quota — so
 * [BleScanStartException] is an [ExpectedCondition] and is never reported as a crash.
 *
 * @property androidCode the platform-level code or constant this maps to, used in log lines.
 * @property description a human-readable explanation for log lines.
 * @property label the stable, low-cardinality [ExpectedCondition.expectedConditionLabel] for rate tracking.
 */
enum class BleScanStartFailureReason(val androidCode: String, val description: String, val label: String) {
    ApplicationRegistrationFailed(
        androidCode = "SCAN_FAILED_APPLICATION_REGISTRATION_FAILED(2)",
        description = "Android could not register the app for BLE scanning",
        label = "ble-scan-registration-failed",
    ),
    MissingScanPermission(
        androidCode = "MISSING_SCAN_PERMISSION",
        description = "A runtime permission required for BLE scanning is not granted",
        label = "ble-scan-missing-permission",
    ),
    ScanningTooFrequently(
        androidCode = "SCAN_FAILED_SCANNING_TOO_FREQUENTLY(6)",
        description = "Android rejected a BLE scan because the app reached its scan-start quota",
        label = "ble-scan-too-frequently",
    ),
    BluetoothDisabled(
        androidCode = "BLUETOOTH_DISABLED",
        description = "Bluetooth is switched off",
        label = "ble-scan-bluetooth-disabled",
    ),
    LocationServicesDisabled(
        androidCode = "LOCATION_SERVICES_DISABLED",
        description = "Location services are off, and this Android version requires them for BLE scanning",
        label = "ble-scan-location-services-disabled",
    ),
}

/**
 * A discovery scan-start failure. No advertisements can be delivered until a future scan starts successfully.
 *
 * This is an [ExpectedCondition]: every [BleScanStartFailureReason] describes the environment refusing the scan, not a
 * defect in the app, so callers should surface it to the user and log it at warn level rather than reporting it.
 */
class BleScanStartException(val reason: BleScanStartFailureReason, cause: Throwable, val retryAfter: Duration? = null) :
    IllegalStateException("BLE scan could not start: ${reason.androidCode}", cause),
    ExpectedCondition {
    override val expectedConditionLabel: String = reason.label
}
