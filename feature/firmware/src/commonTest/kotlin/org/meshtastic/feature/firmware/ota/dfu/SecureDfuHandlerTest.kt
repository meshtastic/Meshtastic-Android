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
import org.meshtastic.core.testing.FakeBleDevice
import org.meshtastic.core.testing.FakeRadioController
import org.meshtastic.feature.firmware.BootloaderDfuZip
import org.meshtastic.feature.firmware.FirmwareArtifact
import org.meshtastic.feature.firmware.FirmwareFileHandler
import org.meshtastic.feature.firmware.FirmwareRetriever
import org.meshtastic.feature.firmware.FirmwareUpdateState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
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

    private fun startWith(failure: Exception): List<FirmwareUpdateState> {
        val fileHandler: FirmwareFileHandler = mock(MockMode.autofill)
        everySuspend { fileHandler.extractZipEntries(any()) } throws failure
        val handler =
            SecureDfuHandler(
                firmwareRetriever = FirmwareRetriever(fileHandler),
                firmwareFileHandler = fileHandler,
                radioController = FakeRadioController(),
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
    fun `a zip over the size limit wrote nothing`() {
        val error = startWith(IllegalArgumentException("entry too large")).last()
        assertTrue(assertIs<FirmwareUpdateState.Error>(error).nothingWritten)
    }

    @Test
    fun `a corrupt archive wrote nothing`() {
        val error = startWith(Exception("invalid entry")).last()
        assertTrue(assertIs<FirmwareUpdateState.Error>(error).nothingWritten)
    }

    @Test
    fun `a bootloader that speaks Secure DFU refuses the package with nothing written`() {
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
        every { scanner.scan(any(), any(), any()) } calls
            {
                if (it.args[1] == SecureDfuUuids.SERVICE) flowOf(FakeBleDevice(target)) else emptyFlow()
            }
        val handler =
            SecureDfuHandler(
                firmwareRetriever = retriever,
                firmwareFileHandler = fileHandler,
                radioController = FakeRadioController(),
                bleScanner = scanner,
                bleConnectionFactory = mock<BleConnectionFactory>(MockMode.autofill),
                dispatchers = dispatchers,
            )
        val states = mutableListOf<FirmwareUpdateState>()

        runBlocking { handler.upgradeBootloader(release, hardware, target, asset, states::add) }

        val error = assertIs<FirmwareUpdateState.Error>(states.last())
        assertTrue(error.nothingWritten)
        assertEquals(DfuException.BootloaderNotLegacy().toErrorState(), error)
    }
}
