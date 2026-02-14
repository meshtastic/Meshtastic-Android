/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.testing.TestLifecycleOwner
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import no.nordicsemi.kotlin.ble.client.android.CentralManager
import no.nordicsemi.kotlin.ble.client.android.Peripheral
import no.nordicsemi.kotlin.ble.client.android.mock.mock
import no.nordicsemi.kotlin.ble.client.mock.AddressType
import no.nordicsemi.kotlin.ble.client.mock.PeripheralSpec
import no.nordicsemi.kotlin.ble.client.mock.PeripheralSpecEventHandler
import no.nordicsemi.kotlin.ble.client.mock.Proximity
import no.nordicsemi.kotlin.ble.core.LegacyAdvertisingSetParameters
import no.nordicsemi.kotlin.ble.environment.android.mock.MockAndroidEnvironment
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.meshtastic.core.ble.MeshtasticBleConstants.SERVICE_UUID
import org.meshtastic.core.di.CoroutineDispatchers
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class BluetoothRepositoryTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val dispatchers = CoroutineDispatchers(main = testDispatcher, default = testDispatcher, io = testDispatcher)

    private lateinit var mockEnvironment: MockAndroidEnvironment
    private lateinit var lifecycleOwner: TestLifecycleOwner

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockEnvironment =
            MockAndroidEnvironment.Api31(
                isBluetoothEnabled = true,
                isBluetoothScanPermissionGranted = true,
                isBluetoothConnectPermissionGranted = true,
            )
        lifecycleOwner =
            TestLifecycleOwner(initialState = Lifecycle.State.RESUMED, coroutineDispatcher = testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state reflects environment`() = runTest(testDispatcher) {
        val centralManager = CentralManager.mock(mockEnvironment, backgroundScope)
        val bleScanner = BleScanner(centralManager)
        val repository =
            BluetoothRepository(dispatchers, lifecycleOwner.lifecycle, centralManager, mockEnvironment, bleScanner)

        runCurrent()
        val state = repository.state.value
        assertTrue(state.enabled)
        assertTrue(state.hasPermissions)
    }

    @Test
    fun `state updates when bluetooth is disabled`() = runTest(testDispatcher) {
        val centralManager = CentralManager.mock(mockEnvironment, backgroundScope)
        val bleScanner = BleScanner(centralManager)
        val repository =
            BluetoothRepository(dispatchers, lifecycleOwner.lifecycle, centralManager, mockEnvironment, bleScanner)

        mockEnvironment.simulatePowerOff()
        runCurrent()

        val state = repository.state.value
        assertFalse(state.enabled)
    }

    @Test
    fun `scan finds matching devices`() = runTest(testDispatcher) {
        val address = "C0:00:00:00:00:01"
        val peripheral = mockk<Peripheral>()
        every { peripheral.address } returns address

        val centralManager = CentralManager.mock(mockEnvironment, backgroundScope)
        val bleScanner = mockk<BleScanner>()
        coEvery { bleScanner.scan(any(), any()) } returns
            flow {
                emit(peripheral)
                delay(5000) // Prevent immediate completion
            }

        val repository =
            BluetoothRepository(dispatchers, lifecycleOwner.lifecycle, centralManager, mockEnvironment, bleScanner)

        repository.startScan()

        // Verify scanning started
        assertTrue(repository.isScanning.value)

        // Give it time to scan (virtual)
        runCurrent()

        val scannedDevices = repository.scannedDevices.value
        assertFalse("Scanned devices list should not be empty", scannedDevices.isEmpty())
        assertEquals(address, scannedDevices.first().address)

        // Verify BleScanner was called with correct timeout and filter
        verify { bleScanner.scan(5.seconds, any()) }

        repository.stopScan()
        assertFalse(repository.isScanning.value)
    }

    @Test
    fun `scan ignores non-matching devices`() = runTest(testDispatcher) {
        // Since logic is delegated to BleScanner, we test that repository correctly handles BleScanner result
        // Here we simulate BleScanner not finding anything (e.g. filter worked)

        val centralManager = CentralManager.mock(mockEnvironment, backgroundScope)
        val bleScanner = mockk<BleScanner>()
        coEvery { bleScanner.scan(any(), any()) } returns flowOf()

        val repository =
            BluetoothRepository(dispatchers, lifecycleOwner.lifecycle, centralManager, mockEnvironment, bleScanner)

        repository.startScan()
        runCurrent()

        val scannedDevices = repository.scannedDevices.value
        assertTrue("Should be empty", scannedDevices.isEmpty())
    }

    @Test
    fun `bonded devices are correctly identified`() = runTest(testDispatcher) {
        val address = "C0:00:00:00:00:03"
        val peripheral =
            PeripheralSpec.simulatePeripheral(
                identifier = address,
                addressType = AddressType.RANDOM_STATIC,
                proximity = Proximity.IMMEDIATE,
            ) {
                advertising(parameters = LegacyAdvertisingSetParameters(connectable = true)) {
                    CompleteLocalName("Meshtastic_5678")
                }
                connectable(
                    name = "Meshtastic_5678",
                    isBonded = true,
                    eventHandler = object : PeripheralSpecEventHandler {},
                ) {
                    Service(uuid = SERVICE_UUID) {}
                }
            }

        val centralManager = CentralManager.mock(mockEnvironment, backgroundScope)
        centralManager.simulatePeripherals(listOf(peripheral))

        val bleScanner = BleScanner(centralManager)
        val repository =
            BluetoothRepository(dispatchers, lifecycleOwner.lifecycle, centralManager, mockEnvironment, bleScanner)
        repository.refreshState()
        runCurrent()

        val state = repository.state.value
        assertEquals("Should find 1 bonded device", 1, state.bondedDevices.size)
        assertEquals(address, state.bondedDevices.first().address)
    }
}
