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
import org.meshtastic.core.common.util.CommonUri
import org.meshtastic.core.model.DeviceHardware
import org.meshtastic.core.model.SoftDeviceVariant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Covers volume vetting and image choice — the two decisions that gate every destructive write.
 *
 * Abstract because it builds [CommonUri], whose Android `actual` needs Robolectric; the concrete `jvmTest` subclass
 * supplies the runner. Without that subclass these tests would silently not run at all.
 */
abstract class CommonMaintenanceVolumeTest {

    private val treeUri = CommonUri.parse("content://com.android.externalstorage.documents/tree/1234-5678%3A")

    private fun nrf(variant: SoftDeviceVariant?) = DeviceHardware(
        hwModelSlug = "RAK4631",
        platformioTarget = "rak4631",
        architecture = "nrf52840",
        softDeviceVariant = variant,
    )

    private fun rp2040() = DeviceHardware(hwModelSlug = "RPI_PICO", platformioTarget = "pico", architecture = "rp2040")

    private val rakInfo =
        "UF2 Bootloader 0.4.3\r\nModel: WisBlock RAK4631 Board\r\n" +
            "Board-ID: WisBlock-RAK4631-Board\r\nDate: May 20 2023\r\nSoftDevice: S140 6.1.1\r\n"

    /** RP2040 BOOTSEL volumes expose an INFO_UF2.TXT too, with no SoftDevice line. */
    private val picoInfo = "UF2 Bootloader v3.0\r\nModel: Raspberry Pi RP2\r\nBoard-ID: RPI-RP2\r\n"

    private class FakeVolume(private val removable: Boolean = true, private val info: String? = null) :
        NoopFirmwareFileHandler() {
        var readCount = 0
            private set

        override suspend fun isRemovableDestination(destinationUri: CommonUri): Boolean = removable

        override suspend fun readSiblingText(treeUri: CommonUri, fileName: String): String? {
            readCount++
            return info.takeIf { fileName.equals(INFO_UF2_FILE_NAME, ignoreCase = true) }
        }
    }

    // ── Volume vetting ───────────────────────────────────────────────────────

    @Test
    fun `a removable volume exposing INFO_UF2 is accepted with its reported identity`() = runTest {
        val result = inspectMaintenanceVolume(treeUri, FakeVolume(info = rakInfo))

        val accepted = assertIs<VolumeInspection.Accepted>(result)
        assertEquals("WisBlock-RAK4631-Board", accepted.volume.boardId)
        assertEquals(SoftDeviceVariant.S140_6_1_1, accepted.volume.softDevice)
    }

    @Test
    fun `an rp2040 bootsel volume is accepted with no softdevice`() = runTest {
        val accepted =
            assertIs<VolumeInspection.Accepted>(inspectMaintenanceVolume(treeUri, FakeVolume(info = picoInfo)))

        assertEquals("RPI-RP2", accepted.volume.boardId)
        assertEquals(null, accepted.volume.softDevice, "RP2040 has no SoftDevice to report")
    }

    @Test
    fun `internal storage is rejected before INFO_UF2 is even read`() = runTest {
        // This is the Downloads mis-tap. Rejecting on the cheap check first means no I/O against a wrong volume.
        val fake = FakeVolume(removable = false, info = rakInfo)

        val rejected = assertIs<VolumeInspection.Rejected>(inspectMaintenanceVolume(treeUri, fake))

        assertEquals(UsbMaintenanceRefusal.DestinationNotRemovable, rejected.reason)
        assertEquals(0, fake.readCount, "Must not read from a volume already known to be wrong")
    }

    @Test
    fun `a removable volume without INFO_UF2 is not a bootloader drive`() = runTest {
        // Also the CDC-only bootloader case, where no mass-storage volume exists at all.
        val rejected = assertIs<VolumeInspection.Rejected>(inspectMaintenanceVolume(treeUri, FakeVolume(info = null)))

        assertEquals(UsbMaintenanceRefusal.NotABootloaderVolume, rejected.reason)
    }

    @Test
    fun `an INFO_UF2 without a board id is not a bootloader drive`() = runTest {
        val fake = FakeVolume(info = "UF2 Bootloader 0.4.3\r\nModel: Something\r\n")

        val rejected = assertIs<VolumeInspection.Rejected>(inspectMaintenanceVolume(treeUri, fake))

        assertEquals(UsbMaintenanceRefusal.NotABootloaderVolume, rejected.reason)
    }

    // ── Image choice ─────────────────────────────────────────────────────────

    @Test
    fun `erase uses the volume's softdevice when it agrees with the map`() {
        val choice =
            chooseMaintenanceImage(
                UsbMaintenanceRequest.FactoryErase,
                nrf(SoftDeviceVariant.S140_6_1_1),
                MaintenanceVolume("WisBlock-RAK4631-Board", SoftDeviceVariant.S140_6_1_1),
            )

        assertEquals("nrf_erase2.uf2", assertIs<MaintenanceImageChoice.Resolved>(choice).asset.fileName)
    }

    @Test
    fun `erase refuses when the volume and the map disagree`() {
        val choice =
            chooseMaintenanceImage(
                UsbMaintenanceRequest.FactoryErase,
                nrf(SoftDeviceVariant.S140_6_1_1),
                MaintenanceVolume("WisBlock-RAK4631-Board", SoftDeviceVariant.S140_7_3_0),
            )

        assertEquals(
            UsbMaintenanceRefusal.SoftDeviceConflict,
            assertIs<MaintenanceImageChoice.Refused>(choice).reason,
            "A disagreement must refuse — writing either image risks the SoftDevice",
        )
    }

    @Test
    fun `erase falls back to the map when the volume reports no softdevice`() {
        val choice =
            chooseMaintenanceImage(
                UsbMaintenanceRequest.FactoryErase,
                nrf(SoftDeviceVariant.S140_7_3_0),
                MaintenanceVolume("SomeBoard", softDevice = null),
            )

        assertEquals("nrf_erase_sd7_3.uf2", assertIs<MaintenanceImageChoice.Resolved>(choice).asset.fileName)
    }

    @Test
    fun `erase refuses when neither the volume nor the map knows the softdevice`() {
        val choice =
            chooseMaintenanceImage(
                UsbMaintenanceRequest.FactoryErase,
                nrf(variant = null),
                MaintenanceVolume("SomeBoard", softDevice = null),
            )

        assertEquals(UsbMaintenanceRefusal.UnknownSoftDevice, assertIs<MaintenanceImageChoice.Refused>(choice).reason)
    }

    @Test
    fun `erase on rp2040 resolves without consulting a softdevice`() {
        val choice =
            chooseMaintenanceImage(
                UsbMaintenanceRequest.FactoryErase,
                rp2040(),
                MaintenanceVolume("RPI-RP2", softDevice = null),
            )

        assertEquals("pico_erase.uf2", assertIs<MaintenanceImageChoice.Resolved>(choice).asset.fileName)
    }

    @Test
    fun `bootloader upgrade resolves from the volume board id rather than the build target`() {
        // Hardware reports itself as a T-Echo while the catalog target says rak4631: the volume wins, because it is the
        // thing physically in front of us.
        val choice =
            chooseMaintenanceImage(
                UsbMaintenanceRequest.BootloaderUpgrade,
                nrf(SoftDeviceVariant.S140_6_1_1),
                MaintenanceVolume("nRF52840-TEcho-v1", SoftDeviceVariant.S140_6_1_1),
            )

        assertTrue(assertIs<MaintenanceImageChoice.Resolved>(choice).asset.fileName.contains("lilygo_techo"))
    }

    @Test
    fun `bootloader upgrade refuses an unrecognized board id`() {
        val choice =
            chooseMaintenanceImage(
                UsbMaintenanceRequest.BootloaderUpgrade,
                nrf(SoftDeviceVariant.S140_6_1_1),
                MaintenanceVolume("RPI-RP2", softDevice = null),
            )

        assertEquals(UsbMaintenanceRefusal.UnknownBoardId, assertIs<MaintenanceImageChoice.Refused>(choice).reason)
    }
}
