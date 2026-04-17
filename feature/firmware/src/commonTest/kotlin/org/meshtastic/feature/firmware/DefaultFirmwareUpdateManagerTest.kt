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
package org.meshtastic.feature.firmware

import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.meshtastic.core.ble.BleConnectionFactory
import org.meshtastic.core.ble.BleScanner
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.DeviceHardware
import org.meshtastic.core.model.RadioController
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.RadioPrefs
import org.meshtastic.feature.firmware.ota.Esp32OtaUpdateHandler
import org.meshtastic.feature.firmware.ota.dfu.SecureDfuHandler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/**
 * Tests for [DefaultFirmwareUpdateManager] routing logic. Verifies that `getHandler()` selects the correct handler
 * based on connection type (BLE/Serial/TCP) and device architecture (ESP32 vs nRF52), and that `getTarget()` returns
 * the correct address.
 *
 * Handler instances are constructed with mocked interface dependencies; only the routing logic (`getHandler` /
 * `getTarget`) is exercised — no handler methods are called.
 */
class DefaultFirmwareUpdateManagerTest {

    // ── Test fixtures ───────────────────────────────────────────────────────

    private val esp32Hardware =
        DeviceHardware(hwModelSlug = "HELTEC_V3", platformioTarget = "heltec-v3", architecture = "esp32-s3")

    private val nrf52Hardware =
        DeviceHardware(hwModelSlug = "RAK4631", platformioTarget = "rak4631", architecture = "nrf52840")

    // Real handler instances — their internal deps are mocked interfaces but never invoked by these tests.
    private val fileHandler: FirmwareFileHandler = mock(MockMode.autofill)
    private val radioController: RadioController = mock(MockMode.autofill)
    private val nodeRepository: NodeRepository = mock(MockMode.autofill)
    private val bleScanner: BleScanner = mock(MockMode.autofill)
    private val bleConnectionFactory: BleConnectionFactory = mock(MockMode.autofill)
    private val firmwareRetriever = FirmwareRetriever(fileHandler)
    private val dispatchers =
        CoroutineDispatchers(
            io = Dispatchers.Unconfined,
            main = Dispatchers.Unconfined,
            default = Dispatchers.Unconfined,
        )

    private val secureDfuHandler =
        SecureDfuHandler(
            firmwareRetriever = firmwareRetriever,
            firmwareFileHandler = fileHandler,
            radioController = radioController,
            bleScanner = bleScanner,
            bleConnectionFactory = bleConnectionFactory,
            dispatchers = dispatchers,
        )

    private val usbUpdateHandler =
        UsbUpdateHandler(
            firmwareRetriever = firmwareRetriever,
            radioController = radioController,
            nodeRepository = nodeRepository,
        )

    private val esp32OtaHandler =
        Esp32OtaUpdateHandler(
            firmwareRetriever = firmwareRetriever,
            firmwareFileHandler = fileHandler,
            radioController = radioController,
            nodeRepository = nodeRepository,
            bleScanner = bleScanner,
            bleConnectionFactory = bleConnectionFactory,
            dispatchers = dispatchers,
        )

    private fun createManager(address: String?): DefaultFirmwareUpdateManager {
        val radioPrefs: RadioPrefs = mock(MockMode.autofill) { every { devAddr } returns MutableStateFlow(address) }
        return DefaultFirmwareUpdateManager(
            radioPrefs = radioPrefs,
            secureDfuHandler = secureDfuHandler,
            usbUpdateHandler = usbUpdateHandler,
            esp32OtaUpdateHandler = esp32OtaHandler,
        )
    }

    // ── getHandler: BLE connection ──────────────────────────────────────────

    @Test
    fun `BLE + ESP32 routes to OTA handler`() {
        val manager = createManager("xAA:BB:CC:DD:EE:FF")
        val handler = manager.getHandler(esp32Hardware)
        assertIs<Esp32OtaUpdateHandler>(handler)
    }

    @Test
    fun `BLE + nRF52 routes to Secure DFU handler`() {
        val manager = createManager("xAA:BB:CC:DD:EE:FF")
        val handler = manager.getHandler(nrf52Hardware)
        assertIs<SecureDfuHandler>(handler)
    }

    // ── getHandler: Serial/USB connection ───────────────────────────────────

    @Test
    fun `Serial + nRF52 routes to USB handler`() {
        val manager = createManager("s/dev/ttyUSB0")
        val handler = manager.getHandler(nrf52Hardware)
        assertIs<UsbUpdateHandler>(handler)
    }

    @Test
    fun `Serial + ESP32 throws error`() {
        val manager = createManager("s/dev/ttyUSB0")
        assertFailsWith<IllegalStateException> { manager.getHandler(esp32Hardware) }
    }

    // ── getHandler: TCP/WiFi connection ─────────────────────────────────────

    @Test
    fun `TCP + ESP32 routes to OTA handler`() {
        val manager = createManager("t192.168.1.100")
        val handler = manager.getHandler(esp32Hardware)
        assertIs<Esp32OtaUpdateHandler>(handler)
    }

    @Test
    fun `TCP + nRF52 throws error`() {
        val manager = createManager("t192.168.1.100")
        assertFailsWith<IllegalStateException> { manager.getHandler(nrf52Hardware) }
    }

    // ── getHandler: Unknown / null connection ───────────────────────────────

    @Test
    fun `Unknown connection type throws error`() {
        val manager = createManager("z_unknown")
        assertFailsWith<IllegalStateException> { manager.getHandler(esp32Hardware) }
    }

    @Test
    fun `Null address throws error`() {
        val manager = createManager(null)
        assertFailsWith<IllegalStateException> { manager.getHandler(esp32Hardware) }
    }

    // ── getTarget ───────────────────────────────────────────────────────────

    @Test
    fun `Serial target is empty string`() {
        val manager = createManager("s/dev/ttyUSB0")
        assertEquals("", manager.getTarget("anything"))
    }

    @Test
    fun `BLE target is the passed address`() {
        val manager = createManager("xAA:BB:CC:DD:EE:FF")
        assertEquals("AA:BB:CC:DD:EE:FF", manager.getTarget("AA:BB:CC:DD:EE:FF"))
    }

    @Test
    fun `TCP target is the passed address`() {
        val manager = createManager("t192.168.1.100")
        assertEquals("192.168.1.100", manager.getTarget("192.168.1.100"))
    }

    @Test
    fun `Unknown connection target is empty string`() {
        val manager = createManager("z_unknown")
        assertEquals("", manager.getTarget("something"))
    }
}
