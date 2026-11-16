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
package org.meshtastic.feature.firmware.ota.dfu

import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.meshtastic.core.ble.BleConnectionFactory
import org.meshtastic.core.ble.BleScanner
import org.meshtastic.core.common.util.CommonUri
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.DeviceHardware
import org.meshtastic.core.model.FirmwareRelease
import org.meshtastic.core.model.SoftDeviceVariant
import org.meshtastic.core.testing.FakeBleConnection
import org.meshtastic.core.testing.FakeBleConnectionFactory
import org.meshtastic.core.testing.FakeBleDevice
import org.meshtastic.core.testing.FakeRadioController
import org.meshtastic.feature.firmware.BootloaderDfuZip
import org.meshtastic.feature.firmware.FirmwareArtifact
import org.meshtastic.feature.firmware.FirmwareFileHandler
import org.meshtastic.feature.firmware.FirmwareRetriever
import org.meshtastic.feature.firmware.FirmwareUpdateState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A run that is refused before any write reports `nothingWritten`, so its recovery record is retired. */
class SecureDfuHandlerTest {

    private val release = FirmwareRelease(id = "v2.8.3", title = "test")
    private val hardware = DeviceHardware(hwModelSlug = "TBEAM", platformioTarget = "tbeam", architecture = "nrf52840")
    private val dispatchers =
        CoroutineDispatchers(
            io = Dispatchers.Unconfined,
            main = Dispatchers.Unconfined,
            default = Dispatchers.Unconfined,
        )

    private fun startWith(failure: Exception, radio: FakeRadioController): List<FirmwareUpdateState> {
        val fileHandler: FirmwareFileHandler = mock(MockMode.autofill)
        everySuspend { fileHandler.extractZipEntries(any()) } throws failure
        val handler =
            SecureDfuHandler(
                firmwareRetriever = FirmwareRetriever(fileHandler),
                firmwareFileHandler = fileHandler,
                radioController = radio,
                bleScanner = mock<BleScanner>(MockMode.autofill),
                bleConnectionFactory = mock<BleConnectionFactory>(MockMode.autofill),
                dispatchers = dispatchers,
            )
        val states = mutableListOf<FirmwareUpdateState>()
        runBlocking {
            handler.startUpdate(
                release,
                hardware,
                "AA:BB:CC:DD:EE:FF",
                states::add,
                CommonUri.parse("file:///tmp/f.zip"),
            )
        }
        return states
    }

    @Test
    fun `an extraction that rejects the archive wrote nothing`() {
        val radio = FakeRadioController()
        val states = startWith(IllegalArgumentException("entry too large"), radio)
        assertTrue(states.filterIsInstance<FirmwareUpdateState.Error>().single().nothingWritten)
        assertNull(radio.lastSetDeviceAddress, "the device never left its application")
    }

    @Test
    fun `an extraction that fails on a corrupt archive wrote nothing`() {
        val radio = FakeRadioController()
        val states = startWith(Exception("invalid entry"), radio)
        assertTrue(states.filterIsInstance<FirmwareUpdateState.Error>().single().nothingWritten)
        assertNull(radio.lastSetDeviceAddress, "the device never left its application")
    }

    private fun upgradeAgainstSecureBootloader(
        resetConnects: Boolean,
        radio: FakeRadioController,
        connection: FakeBleConnection = FakeBleConnection(),
    ): List<FirmwareUpdateState> {
        val target = "AA:BB:CC:DD:EE:FF"
        val appManifest = """{"manifest":{"application":{"bin_file":"app.bin","dat_file":"app.dat"}}}"""
        val bootloaderManifest =
            """{"manifest":{"softdevice_bootloader":{"bin_file":"sd_bl.bin","dat_file":"sd_bl.dat",""" +
                """"bl_size":4,"sd_size":6}}}"""
        val appZip = FirmwareArtifact(uri = CommonUri.parse("file:///tmp/app.zip"), fileName = "app.zip")
        val bootloaderZip = FirmwareArtifact(uri = CommonUri.parse("file:///tmp/bl.zip"), fileName = "bl.zip")
        val asset =
            BootloaderDfuZip(
                url = "https://example.com/bl.zip",
                fileName = "bl.zip",
                sha256 = "00",
                version = "2.6.0",
                softDevice = SoftDeviceVariant.S140_6_1_1,
                bleModel = "model",
            )
        val fileHandler: FirmwareFileHandler = mock(MockMode.autofill)
        everySuspend { fileHandler.extractZipEntries(any()) }
            .calls {
                val name = (it.args[0] as FirmwareArtifact).fileName
                if (name == "app.zip") {
                    mapOf(
                        "manifest.json" to appManifest.encodeToByteArray(),
                        "app.bin" to ByteArray(3),
                        "app.dat" to ByteArray(2),
                    )
                } else {
                    mapOf(
                        "manifest.json" to bootloaderManifest.encodeToByteArray(),
                        "sd_bl.bin" to ByteArray(10),
                        "sd_bl.dat" to ByteArray(14),
                    )
                }
            }
        val retriever: FirmwareRetriever = mock(MockMode.autofill)
        everySuspend { retriever.retrieveOtaFirmware(any(), any(), any()) } returns appZip
        everySuspend { retriever.retrieveBootloaderDfuZip(any(), any()) } returns bootloaderZip
        // Only the Secure DFU service is advertised, so detection observes a Secure bootloader.
        val scanner: BleScanner = mock(MockMode.autofill)
        var secureScans = 0
        every { scanner.scan(any(), any(), any()) } calls
            {
                val advertised = it.args[1] == SecureDfuUuids.SERVICE && (resetConnects || secureScans++ == 0)
                if (advertised) flowOf(FakeBleDevice(target)) else emptyFlow()
            }
        val handler =
            SecureDfuHandler(
                firmwareRetriever = retriever,
                firmwareFileHandler = fileHandler,
                radioController = radio,
                bleScanner = scanner,
                bleConnectionFactory = FakeBleConnectionFactory(connection),
                dispatchers = dispatchers,
            )
        val states = mutableListOf<FirmwareUpdateState>()

        runBlocking { handler.upgradeBootloader(release, hardware, target, asset, states::add) }
        return states
    }

    @Test
    fun `a bootloader that speaks Secure DFU is reset and refuses the package with nothing written`() {
        val radio = FakeRadioController()
        val connection = FakeBleConnection()
        val states = upgradeAgainstSecureBootloader(resetConnects = true, radio = radio, connection = connection)

        assertTrue(
            connection.service.writes.any { it.data.contentEquals(byteArrayOf(DfuOpcode.ABORT)) },
            "the RESET was written to the control point",
        )
        val error = states.filterIsInstance<FirmwareUpdateState.Error>().single()
        assertTrue(error.nothingWritten)
        assertEquals(DfuException.BootloaderNotLegacy().toErrorState(), error)
        assertEquals("n", radio.lastSetDeviceAddress, "the buttonless trigger already released the app link")
    }

    @Test
    fun `a Secure DFU bootloader whose RESET write fails keeps its recovery record`() {
        val connection = FakeBleConnection().apply { service.writeException = IllegalStateException("write failed") }
        val states =
            upgradeAgainstSecureBootloader(resetConnects = true, radio = FakeRadioController(), connection = connection)

        assertFalse(states.filterIsInstance<FirmwareUpdateState.Error>().single().nothingWritten)
    }

    @Test
    fun `a Secure DFU bootloader that cannot be reset keeps its recovery record`() {
        val radio = FakeRadioController()
        val states = upgradeAgainstSecureBootloader(resetConnects = false, radio = radio)

        assertFalse(states.filterIsInstance<FirmwareUpdateState.Error>().single().nothingWritten)
    }
}
