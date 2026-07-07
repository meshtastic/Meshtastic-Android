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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
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

internal fun resolveKableScanFilter(serviceUuid: Uuid?, address: String?): KableScanFilter = when {
    address != null -> KableScanFilter.Address(address)
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
    internal open fun advertisements(filter: KableScanFilter): Flow<KableScanResult> {
        val scanner = Scanner {
            logging { applyConfig(loggingConfig) }
            when (filter) {
                KableScanFilter.None -> Unit
                is KableScanFilter.Address -> filters { match { address = filter.value } }
                is KableScanFilter.ServiceUuid -> filters { match { services = listOf(filter.value) } }
            }
        }
        return scanner.advertisements.map(Advertisement::toScanResult)
    }

    override fun scan(timeout: Duration, serviceUuid: Uuid?, address: String?): Flow<BleDevice> {
        val filter = resolveKableScanFilter(serviceUuid = serviceUuid, address = address)

        // Kable's Scanner doesn't enforce timeout internally, it runs until the Flow is cancelled.
        // By wrapping it in a channelFlow with a timeout, we enforce the BleScanner contract cleanly.
        return channelFlow {
            withTimeoutOrNull(timeout) {
                try {
                    advertisements(filter).collect { advertisement ->
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
                } catch (ex: IllegalStateException) {
                    throw ex.asBleScanStartExceptionOrNull() ?: ex
                }
            }
        }
    }
}

private fun Throwable.asBleScanStartExceptionOrNull(): BleScanStartException? {
    var current: Throwable? = this
    var depth = 0
    while (current != null && depth < MAX_SCAN_START_FAILURE_CAUSE_DEPTH) {
        if (current.isApplicationRegistrationFailure()) {
            return BleScanStartException(BleScanStartFailureReason.ApplicationRegistrationFailed, this)
        }
        current = current.cause
        depth++
    }
    return null
}

// Kable exposes Android scan-start registration failure as an IllegalStateException message,
// so keep this matcher narrow and local to the scanner adapter.
private fun Throwable.isApplicationRegistrationFailure(): Boolean = this is IllegalStateException &&
    message?.let { failureMessage ->
        failureMessage.contains("app cannot be registered", ignoreCase = true) ||
            failureMessage.contains("SCAN_FAILED_APPLICATION_REGISTRATION_FAILED", ignoreCase = true)
    } == true
