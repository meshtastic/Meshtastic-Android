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

import app.cash.turbine.test
import com.juul.kable.Advertisement
import dev.mokkery.MockMode
import dev.mokkery.mock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class KableBleConnectionTest {

    @Test
    fun `scan emits ble device for discovered advertisement`() = runTest {
        val advertisement: Advertisement = mock(MockMode.autofill)
        val scanner =
            TestKableBleScanner(
                scanResults =
                flowOf(
                    KableScanResult(
                        identifier = "AA:BB:CC:DD:EE:FF",
                        name = "Meshtastic",
                        advertisement = advertisement,
                    ),
                ),
            )

        val result = scanner.scan(timeout = 1.seconds).first()

        val device = assertIs<MeshtasticBleDevice>(result)
        assertEquals("AA:BB:CC:DD:EE:FF", device.address)
        assertEquals("Meshtastic", device.name)
        assertSame(advertisement, device.advertisement)
    }

    @Test
    fun `scan reserves one platform start before collecting advertisements`() = runTest {
        val reservationEvent = "scan-start-reserved"
        val collectionEvent = "advertisements-collected"
        val events = mutableListOf<String>()
        val scanner =
            TestKableBleScanner(
                scanResults = flow { events += collectionEvent },
                scanStartLimiter = BleScanStartLimiter { events += reservationEvent },
            )

        scanner.scan(timeout = 1.seconds).toList()

        assertEquals(1, events.count { it == reservationEvent })
        assertEquals(listOf(reservationEvent, collectionEvent), events)
    }

    @Test
    fun `expired timeout does not reserve a platform start`() = runTest {
        var reservations = 0
        val scanner =
            TestKableBleScanner(scanResults = emptyFlow(), scanStartLimiter = BleScanStartLimiter { reservations += 1 })

        scanner.scan(timeout = 0.seconds).toList()

        assertEquals(0, reservations)
    }

    @Test
    fun `timeout terminates scan`() = runTest {
        var cancelled = false
        val scanner =
            TestKableBleScanner(
                scanResults =
                flow {
                    try {
                        awaitCancellation()
                    } finally {
                        cancelled = true
                    }
                },
            )
        val collected = mutableListOf<BleDevice>()

        val job = backgroundScope.launch { scanner.scan(timeout = 1.seconds).toList(collected) }

        advanceTimeBy(1.seconds.inWholeMilliseconds + 1)
        advanceUntilIdle()
        job.join()

        assertTrue(job.isCompleted)
        assertTrue(cancelled)
        assertTrue(collected.isEmpty())
    }

    @Test
    fun `service uuid filter is applied`() = runTest {
        val serviceUuid = Uuid.parse("12345678-1234-1234-1234-1234567890ab")
        val scanner = TestKableBleScanner(scanResults = emptyFlow())

        scanner.scan(timeout = 1.seconds, serviceUuid = serviceUuid).toList()

        assertEquals(KableScanFilter.ServiceUuid(serviceUuid), scanner.lastFilter)
    }

    @Test
    fun `address filter is used natively only where the platform supports it`() {
        val address = "AA:BB:CC:DD:EE:FF"

        assertEquals(
            KableScanFilter.Address(address),
            resolveKableScanFilter(serviceUuid = null, address = address, supportsAddressFilter = true),
        )
        // Without native support the scan must stay unfiltered rather than carry a filter that matches nothing.
        assertEquals(
            KableScanFilter.None,
            resolveKableScanFilter(serviceUuid = null, address = address, supportsAddressFilter = false),
        )
    }

    @Test
    fun `service uuid wins over address when the platform cannot filter by address`() {
        val serviceUuid = Uuid.parse("12345678-1234-1234-1234-1234567890ab")
        val address = "AA:BB:CC:DD:EE:FF"

        assertEquals(
            KableScanFilter.Address(address),
            resolveKableScanFilter(serviceUuid = serviceUuid, address = address, supportsAddressFilter = true),
        )
        // Kable's btleplug backend matches address filters against a hardcoded null, so an address filter here would
        // silently yield no advertisements at all and BLE connect could never find the device.
        assertEquals(
            KableScanFilter.ServiceUuid(serviceUuid),
            resolveKableScanFilter(serviceUuid = serviceUuid, address = address, supportsAddressFilter = false),
        )
    }

    @Test
    fun `scan narrows to the requested address regardless of the native filter`() = runTest {
        val wanted = "AA:BB:CC:DD:EE:FF"
        val scanner =
            TestKableBleScanner(
                scanResults =
                flowOf(
                    KableScanResult(identifier = "11:22:33:44:55:66", name = "Other", advertisement = null),
                    KableScanResult(identifier = wanted, name = "Meshtastic", advertisement = null),
                    KableScanResult(identifier = "77:88:99:AA:BB:CC", name = "Another", advertisement = null),
                ),
            )

        scanner.scan(timeout = 1.seconds, address = wanted).test {
            assertEquals(wanted, awaitItem().address)
            awaitComplete()
        }
    }

    @Test
    fun `scan matches the requested address case-insensitively`() = runTest {
        // A second, non-matching advertisement keeps this honest: a scan that ignored the address entirely would
        // emit both, so the assertion proves the filter ran *and* that it matched across case.
        val scanner =
            TestKableBleScanner(
                scanResults =
                flowOf(
                    KableScanResult(identifier = "11:22:33:44:55:66", name = "Other", advertisement = null),
                    KableScanResult(identifier = "aa:bb:cc:dd:ee:ff", name = "Meshtastic", advertisement = null),
                ),
            )

        scanner.scan(timeout = 1.seconds, address = "AA:BB:CC:DD:EE:FF").test {
            assertEquals("aa:bb:cc:dd:ee:ff", awaitItem().address)
            awaitComplete()
        }
    }

    @Test
    fun `scan without an address emits every advertisement`() = runTest {
        val scanner =
            TestKableBleScanner(
                scanResults =
                flowOf(
                    KableScanResult(identifier = "11:22:33:44:55:66", name = "One", advertisement = null),
                    KableScanResult(identifier = "77:88:99:AA:BB:CC", name = "Two", advertisement = null),
                ),
            )

        scanner.scan(timeout = 1.seconds).test {
            assertEquals("11:22:33:44:55:66", awaitItem().address)
            assertEquals("77:88:99:AA:BB:CC", awaitItem().address)
            awaitComplete()
        }
    }

    @Test
    fun `scan wraps Android scanner registration failure`() = runTest {
        val scanner =
            TestKableBleScanner(
                scanResults = flow { throw IllegalStateException("Failed to start scan as app cannot be registered") },
            )

        val failure = assertFailsWith<BleScanStartException> { scanner.scan(timeout = 1.seconds).toList() }

        assertEquals(BleScanStartFailureReason.ApplicationRegistrationFailed, failure.reason)
    }

    @Test
    fun `scan wraps nested scanner registration failure`() = runTest {
        val innerCause = IllegalStateException("Failed to start scan as app cannot be registered")
        val scanner =
            TestKableBleScanner(scanResults = flow { throw IllegalStateException("Outer scanner failure", innerCause) })

        val failure = assertFailsWith<BleScanStartException> { scanner.scan(timeout = 1.seconds).toList() }

        assertEquals(BleScanStartFailureReason.ApplicationRegistrationFailed, failure.reason)
    }

    @Test
    fun `scan wraps Android scanning too frequently failure`() = runTest {
        val message = "Failed. App is scanning too frequently"
        val scanner = TestKableBleScanner(scanResults = flow { throw IllegalStateException(message) })

        val failure = assertFailsWith<BleScanStartException> { scanner.scan(timeout = 1.seconds).toList() }

        assertEquals(BleScanStartFailureReason.ScanningTooFrequently, failure.reason)
    }

    @Test
    fun `scan wraps missing scan permission failure`() = runTest {
        val message = "Missing required android.permission.ACCESS_COARSE_LOCATION for scanning"
        val scanner = TestKableBleScanner(scanResults = flow { throw IllegalStateException(message) })

        val failure = assertFailsWith<BleScanStartException> { scanner.scan(timeout = 1.seconds).toList() }

        assertEquals(BleScanStartFailureReason.MissingScanPermission, failure.reason)
    }

    @Test
    fun `scan preserves unrelated illegal state failure`() = runTest {
        val scanner =
            TestKableBleScanner(scanResults = flow { throw IllegalStateException("Unexpected scanner state") })

        val failure = assertFailsWith<IllegalStateException> { scanner.scan(timeout = 1.seconds).toList() }

        assertEquals("Unexpected scanner state", failure.message)
    }

    private class TestKableBleScanner(
        private val scanResults: Flow<KableScanResult>,
        private val scanStartLimiter: BleScanStartLimiter = NoOpBleScanStartLimiter,
    ) : KableBleScanner(BleLoggingConfig.Release) {
        var lastFilter: KableScanFilter? = null
            private set

        override suspend fun reserveScanStart() = scanStartLimiter.reserveStart()

        override fun advertisements(filter: KableScanFilter): Flow<KableScanResult> {
            lastFilter = filter
            return scanResults
        }
    }
}
