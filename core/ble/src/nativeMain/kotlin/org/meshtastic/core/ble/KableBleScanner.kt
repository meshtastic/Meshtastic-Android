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

import com.juul.kable.Advertisement
import com.juul.kable.Scanner
import com.juul.kable.UnmetRequirementException
import com.juul.kable.UnmetRequirementReason
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.annotation.Single
import kotlin.time.Duration
import kotlin.uuid.Uuid

private const val MAX_SCAN_START_FAILURE_CAUSE_DEPTH = 10

internal sealed interface KableScanFilter {
    data object None : KableScanFilter

    data class Address(val value: String) : KableScanFilter

    data class ServiceUuid(val value: Uuid) : KableScanFilter
}

internal data class KableScanResult(val identifier: String, val name: String?, val advertisement: Advertisement?)

/**
 * Picks the native filter to hand Kable: address only where the platform honours it
 * ([supportsNativeAddressScanFilter]), otherwise the service UUID, since a filter the platform ignores matches nothing.
 * [KableBleScanner.scan] narrows to the address client-side either way.
 *
 * [supportsAddressFilter] is a parameter so both platform behaviours are reachable from commonTest.
 */
internal fun resolveKableScanFilter(
    serviceUuid: Uuid?,
    address: String?,
    supportsAddressFilter: Boolean = supportsNativeAddressScanFilter,
): KableScanFilter = when {
    address != null && supportsAddressFilter -> KableScanFilter.Address(address)
    serviceUuid != null -> KableScanFilter.ServiceUuid(serviceUuid)
    else -> KableScanFilter.None
}

// Kable's Advertisement.identifier is an expect typealias: String on Android/JVM/JS but Uuid on Apple.
// toString() looks redundant when compiling the Android/JVM view (hence the warning) but is required to
// normalize the Apple Uuid to the String this result carries, so it must stay.
@Suppress("REDUNDANT_CALL_OF_CONVERSION_METHOD")
private fun Advertisement.toScanResult(): KableScanResult =
    KableScanResult(identifier = identifier.toString(), name = name, advertisement = this)

@Single(binds = [BleScanner::class])
open class KableBleScanner(private val loggingConfig: BleLoggingConfig) : BleScanner {
    private val scanStartLimiter = createBleScanStartLimiter()

    internal open suspend fun reserveScanStart() = scanStartLimiter.reserveStart()

    internal open fun advertisements(filter: KableScanFilter): Flow<KableScanResult> {
        val scanner = Scanner {
            platformScanConfig()
            logging { applyConfig(loggingConfig) }
            when (filter) {
                KableScanFilter.None -> Unit
                is KableScanFilter.Address -> filters { match { address = filter.value } }
                is KableScanFilter.ServiceUuid -> filters { match { services = listOf(filter.value) } }
            }
        }
        return scanner.advertisements.map(Advertisement::toScanResult)
    }

    // ThrowsCount: three deliberate rethrow paths, one per exception family Kable can surface here — cancellation
    // (must propagate untouched), UnmetRequirementException (an IOException) and IllegalStateException. They have no
    // common supertype below Exception, so merging them would mean catching Exception broadly instead.
    @Suppress("ThrowsCount")
    override fun scan(timeout: Duration, serviceUuid: Uuid?, address: String?): Flow<BleDevice> {
        val nativeFilter = resolveKableScanFilter(serviceUuid = serviceUuid, address = address)

        // Kable's Scanner doesn't enforce timeout internally, it runs until the Flow is cancelled.
        // By wrapping it in a channelFlow with a timeout, we enforce the BleScanner contract cleanly.
        return channelFlow {
            withTimeoutOrNull(timeout) {
                reserveScanStart()
                try {
                    // Re-check the address even when the native filter already covers it: callers such as
                    // NymeaWifiService take the first emission without their own address check, so an unsupported
                    // native address filter must never widen the scan to other devices.
                    advertisements(nativeFilter)
                        .filter { address == null || it.identifier.equals(address, ignoreCase = true) }
                        .collect { advertisement ->
                            send(
                                MeshtasticBleDevice(
                                    address = advertisement.identifier,
                                    name = advertisement.name,
                                    advertisement = advertisement.advertisement,
                                ),
                            )
                        }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: UnmetRequirementException) {
                    // Kable models "Bluetooth is off" and "location services are off" as an IOException, so these
                    // never matched the IllegalStateException branch below and escaped raw to ViewModel-level
                    // catch-alls that log at error — the top source of Crashlytics/RUM noise. Map them to the
                    // typed, non-reported BleScanStartException instead.
                    throw ex.asBleScanStartException()
                } catch (ex: IllegalStateException) {
                    throw ex.asBleScanStartExceptionOrNull() ?: ex
                }
            }
        }
    }
}

/**
 * Maps Kable's typed [UnmetRequirementReason] onto the matching [BleScanStartFailureReason].
 *
 * Kable's reason enum is exhaustive over the preconditions it checks, so this needs no message matching — unlike the
 * [IllegalStateException] paths below, which Kable only distinguishes by message text.
 *
 * Kept `internal` and separate from the exception so it stays unit-testable: [UnmetRequirementException] has an
 * `internal` constructor in Kable and cannot be instantiated from our tests.
 */
internal fun UnmetRequirementReason.toBleScanStartFailureReason(): BleScanStartFailureReason = when (this) {
    UnmetRequirementReason.BluetoothDisabled -> BleScanStartFailureReason.BluetoothDisabled
    UnmetRequirementReason.LocationServicesDisabled -> BleScanStartFailureReason.LocationServicesDisabled
}

private fun UnmetRequirementException.asBleScanStartException(): BleScanStartException =
    BleScanStartException(reason.toBleScanStartFailureReason(), this)

private fun Throwable.asBleScanStartExceptionOrNull(): BleScanStartException? {
    var current: Throwable? = this
    var depth = 0
    while (current != null && depth < MAX_SCAN_START_FAILURE_CAUSE_DEPTH) {
        val reason = current.scanStartFailureReasonOrNull()
        if (reason != null) return BleScanStartException(reason, this)
        current = current.cause
        depth++
    }
    return null
}

private fun Throwable.scanStartFailureReasonOrNull(): BleScanStartFailureReason? = when {
    isApplicationRegistrationFailure() -> BleScanStartFailureReason.ApplicationRegistrationFailed
    isScanningTooFrequentlyFailure() -> BleScanStartFailureReason.ScanningTooFrequently
    isMissingScanPermission() -> BleScanStartFailureReason.MissingScanPermission
    else -> null
}

// Kable exposes Android scan-start registration failure as an IllegalStateException message,
// so keep this matcher narrow and local to the scanner adapter.
private fun Throwable.isApplicationRegistrationFailure(): Boolean = this is IllegalStateException &&
    message?.let { failureMessage ->
        failureMessage.contains("app cannot be registered", ignoreCase = true) ||
            failureMessage.contains("SCAN_FAILED_APPLICATION_REGISTRATION_FAILED", ignoreCase = true)
    } == true

private fun Throwable.isScanningTooFrequentlyFailure(): Boolean = this is IllegalStateException &&
    message?.let { failureMessage ->
        failureMessage.contains("scanning too frequently", ignoreCase = true) ||
            failureMessage.contains("SCAN_FAILED_SCANNING_TOO_FREQUENTLY", ignoreCase = true)
    } == true

// Kable's scan-permission check throws a plain IllegalStateException "Missing required <permission> for scanning"
// (ACCESS_COARSE/FINE_LOCATION on Android <12, BLUETOOTH_SCAN on 12+). Match the stable message anchors.
private fun Throwable.isMissingScanPermission(): Boolean = this is IllegalStateException &&
    message?.let { failureMessage ->
        failureMessage.contains("Missing required", ignoreCase = true) &&
            failureMessage.contains("for scanning", ignoreCase = true)
    } == true
