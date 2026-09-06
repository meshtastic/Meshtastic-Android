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

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.meshtastic.core.common.util.CommonUri
import org.meshtastic.core.model.DeviceHardware
import org.meshtastic.core.model.MaintenanceUf2Manifest
import org.meshtastic.core.model.SoftDeviceVariant
import org.meshtastic.core.repository.MaintenanceUf2Repository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Covers the one decision [UsbPassWriter] makes after the image is chosen: whether to open a CDC port and assert DTR.
 *
 * That decision used to be fixed before the drive was read ("nRF52 factory erase ⇒ unblock"). It now follows the
 * resolved image, because the same request writes the SoftDevice erase sketch (which waits for a host) on one
 * bootloader and the bootloader-driven erase block (after which the only port is the bootloader's own) on another.
 *
 * Abstract because it builds [CommonUri]; the concrete `jvmTest` subclass supplies the runner.
 */
abstract class CommonUsbPassWriterTest {

    private val manifest =
        Json { ignoreUnknownKeys = true }
            .decodeFromString<MaintenanceUf2Manifest>(
                """
                {
                  "manifestVersion": 1,
                  "otafixReleaseTag": "0.9.2-OTAFIX2.3-BP1.5",
                  "otafixBase": "https://example.invalid/otafix",
                  "erase": {
                    "nrf52": {
                      "6.1.1": { "fileName": "nrf_erase2.uf2", "sha256": "00", "expectedFirstTargetAddress": 155648 }
                    },
                    "nrf52Bootloader": {
                      "fileName": "meshtastic_factory_erase.uf2",
                      "sha256": "00",
                      "expectedFamilyId": 1296388936
                    },
                    "rp2040": { "fileName": "pico_erase.uf2", "sha256": "00" }
                  },
                  "otafixByBoardId": {
                    "WisBlock-RAK4631-Board": { "otafixBoardSlug": "wiscore_rak4631_board", "sha256": "00" }
                  },
                  "otafixSupportedTargets": ["rak4631"]
                }
                """
                    .trimIndent(),
            )

    private val treeUri = CommonUri.parse("content://com.android.externalstorage.documents/tree/1234-5678%3A")

    private val rak =
        DeviceHardware(
            hwModelSlug = "RAK4631",
            platformioTarget = "rak4631",
            architecture = "nrf52840",
            softDeviceVariant = SoftDeviceVariant.S140_6_1_1,
        )

    private val sketchInfo = "UF2 Bootloader 0.4.3\r\nBoard-ID: WisBlock-RAK4631-Board\r\nSoftDevice: S140 6.1.1\r\n"

    private val bootloaderEraseInfo = sketchInfo + "Factory-Erase: UF2 family 0x4D455348\r\n"

    /** A volume that accepts every write: the copy lands and the device detaches, so only the CDC decision varies. */
    private class WritableVolume(private val info: String) : NoopFirmwareFileHandler() {
        override suspend fun isRemovableDestination(destinationUri: CommonUri): Boolean = true

        override suspend fun readSiblingText(treeUri: CommonUri, fileName: String): String? = info

        override suspend fun createDocumentInTree(treeUri: CommonUri, fileName: String, mimeType: String): CommonUri =
            CommonUri.parse("content://com.android.externalstorage.documents/document/1234-5678%3A$fileName")

        override suspend fun copyToUri(source: FirmwareArtifact, destinationUri: CommonUri): Long =
            UF2_BLOCK_BYTES.toLong()
    }

    private class FixedManifest(private val manifest: MaintenanceUf2Manifest) : MaintenanceUf2Repository {
        override suspend fun getSnapshot(): MaintenanceUf2Manifest = manifest

        override suspend fun reconcile() = Unit
    }

    private inner class Harness(info: String) {
        val unblockCalls = mutableListOf<Pair<Long, Long>>()
        var unblockResult = true
        val written = mutableListOf<String>()

        val writer =
            UsbPassWriter(
                fileHandler = WritableVolume(info),
                maintenanceUf2Repository = FixedManifest(manifest),
                retrieveMaintenanceUf2 = { asset, _ ->
                    written += asset.fileName
                    FirmwareArtifact(uri = CommonUri.parse("file:///tmp/${asset.fileName}"), fileName = asset.fileName)
                },
                awaitDeviceDetach = { true },
                unblockCdc = { wait, hold ->
                    unblockCalls += wait to hold
                    unblockResult
                },
            )
    }

    private fun harness(info: String) = Harness(info)

    private val erasePass = UsbFileSavePass.FromVolume(UsbFileSaveStep.FactoryErase, UsbMaintenanceRequest.FactoryErase)

    @Test
    fun `the softdevice erase sketch is unblocked over cdc after it is written`() = runTest {
        val h = harness(sketchInfo)

        val result = h.writer.write(erasePass, treeUri, rak) {}

        assertEquals(UsbPassResult.Written, result)
        assertEquals(listOf("nrf_erase2.uf2"), h.written)
        assertEquals(1, h.unblockCalls.size, "The sketch blocks on while(!Serial) until DTR is asserted")
    }

    @Test
    fun `the bootloader erase image never has its cdc port opened`() = runTest {
        // After the bootloader consumes the block the only CDC port present is the bootloader's own; opening it would
        // latch onto the wrong port and hold the flow for the whole unblock timeout for nothing.
        val h = harness(bootloaderEraseInfo)

        val result = h.writer.write(erasePass, treeUri, rak) {}

        assertEquals(UsbPassResult.Written, result)
        assertEquals(listOf("meshtastic_factory_erase.uf2"), h.written)
        assertTrue(h.unblockCalls.isEmpty(), "No CDC unblock after the bootloader-driven erase image")
    }

    @Test
    fun `a failed cdc unblock is only reported for the sketch`() = runTest {
        val sketch = harness(sketchInfo).apply { unblockResult = false }
        assertEquals(UsbPassResult.CdcUnblockFailed, sketch.writer.write(erasePass, treeUri, rak) {})

        val bootloader = harness(bootloaderEraseInfo).apply { unblockResult = false }
        assertEquals(UsbPassResult.Written, bootloader.writer.write(erasePass, treeUri, rak) {})
        assertTrue(bootloader.unblockCalls.isEmpty())
    }

    @Test
    fun `a bootloader self-update never has its cdc port opened`() = runTest {
        val h = harness(sketchInfo)
        val pass =
            UsbFileSavePass.FromVolume(UsbFileSaveStep.BootloaderUpgrade, UsbMaintenanceRequest.BootloaderUpgrade)

        assertEquals(UsbPassResult.Written, h.writer.write(pass, treeUri, rak) {})
        assertTrue(h.unblockCalls.isEmpty())
    }
}
