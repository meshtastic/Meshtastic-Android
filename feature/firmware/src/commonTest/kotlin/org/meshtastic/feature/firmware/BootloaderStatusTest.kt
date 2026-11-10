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

import org.meshtastic.core.model.DeviceHardware
import org.meshtastic.core.model.MaintenanceUf2Manifest
import org.meshtastic.core.model.SoftDeviceVariant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BootloaderStatusTest {

    private val manifest =
        MaintenanceUf2Manifest(otafixReleaseTag = "2.6.0", otafixSupportedTargets = listOf("rak4631"))

    private val rak4631 =
        DeviceHardware(
            hwModelSlug = "RAK4631",
            platformioTarget = "rak4631",
            architecture = "nrf52840",
            softDeviceVariant = SoftDeviceVariant.S140_6_1_1,
        )

    private fun packed(major: Int, minor: Int, patch: Int) = (major shl 16) or (minor shl 8) or patch

    private fun gate(bootloader: BootloaderStatus?) = usbMaintenanceGate(
        manifest = manifest,
        hardware = rak4631,
        updateMethod = FirmwareUpdateMethod.Usb,
        hasRelease = true,
        platformSupportsMaintenance = true,
        bootloader = bootloader,
    )

    @Test
    fun `zero reports no bootloader`() {
        assertNull(decodeBootloaderVersion(0))
        assertNull(bootloaderStatus(0, manifest))
    }

    @Test
    fun `a 2_6_0 bootloader decodes to its own version`() {
        assertEquals(InstalledBootloader.Known(KotlinVersion(2, 6, 0)), decodeBootloaderVersion(packed(2, 6, 0)))
    }

    @Test
    fun `the value a RAK4631 on bootloader 2_6_0 reports decodes to 2_6_0`() {
        assertEquals(InstalledBootloader.Known(KotlinVersion(2, 6, 0)), decodeBootloaderVersion(132608))
    }

    @Test
    fun `a major past one byte reads as unreported rather than truncated`() {
        assertNull(decodeBootloaderVersion(0x01000001))
        assertNull(decodeBootloaderVersion(-1))
    }

    @Test
    fun `0_9_2 means older than the first numbered release`() {
        assertEquals(InstalledBootloader.OlderThanFirstNumbered, decodeBootloaderVersion(packed(0, 9, 2)))
    }

    @Test
    fun `other versions below 2_6_0 keep their number`() {
        assertEquals(InstalledBootloader.Known(KotlinVersion(0, 8, 0)), decodeBootloaderVersion(packed(0, 8, 0)))
    }

    @Test
    fun `installed at the latest release is current`() {
        val status = bootloaderStatus(packed(2, 6, 0), manifest)
        assertEquals(KotlinVersion(2, 6, 0), status?.latest)
        assertTrue(status?.isCurrent == true)
    }

    @Test
    fun `installed past the latest release is current`() {
        assertTrue(bootloaderStatus(packed(2, 7, 0), manifest)?.isCurrent == true)
    }

    @Test
    fun `older and pre-2_6_0 bootloaders are not current`() {
        listOf(
            packed(2, 5, 9) to InstalledBootloader.Known(KotlinVersion(2, 5, 9)),
            packed(0, 9, 2) to InstalledBootloader.OlderThanFirstNumbered,
            packed(0, 8, 0) to InstalledBootloader.Known(KotlinVersion(0, 8, 0)),
        )
            .forEach { (value, installed) ->
                val status = assertNotNull(bootloaderStatus(value, manifest))
                assertEquals(installed, status.installed)
                assertFalse(status.isCurrent)
            }
    }

    @Test
    fun `a latest tag that is not plain x_y_z gives no comparison`() {
        val status = bootloaderStatus(packed(2, 6, 0), manifest.copy(otafixReleaseTag = "0.9.2-OTAFIX2.5"))
        assertEquals(InstalledBootloader.Known(KotlinVersion(2, 6, 0)), status?.installed)
        assertNull(status?.latest)
        assertFalse(status?.isCurrent == true)
        assertNull(parsePlainBootloaderVersion(""))
        assertNull(parsePlainBootloaderVersion("2.6"))
        assertNull(parsePlainBootloaderVersion("2.6.0.1"))
        assertNull(parsePlainBootloaderVersion("256.0.0"))
        assertNull(parsePlainBootloaderVersion("99999999999.0.0"))
    }

    @Test
    fun `a current bootloader withdraws the usb upgrade`() {
        assertFalse(gate(bootloaderStatus(packed(2, 6, 0), manifest)).showBootloaderUpgrade)
    }

    @Test
    fun `an older bootloader keeps the usb upgrade without the restart hint`() {
        val gate = gate(bootloaderStatus(packed(0, 9, 2), manifest))
        assertTrue(gate.showBootloaderUpgrade)
        assertNull(gate.latestBootloader, "the device card already shows installed against latest")
    }

    @Test
    fun `an unreported bootloader leaves the usb upgrade as it was`() {
        val gate = gate(null)
        assertTrue(gate.showBootloaderUpgrade)
        assertEquals("2.6.0", gate.latestBootloader)
    }

    @Test
    fun `an unparseable latest tag keeps the usb upgrade`() {
        val oddManifest = manifest.copy(otafixReleaseTag = "0.9.2-OTAFIX2.5")
        val status = assertNotNull(bootloaderStatus(packed(2, 6, 0), oddManifest))
        assertNull(status.latest)
        val gate =
            usbMaintenanceGate(
                manifest = oddManifest,
                hardware = rak4631,
                updateMethod = FirmwareUpdateMethod.Usb,
                hasRelease = true,
                platformSupportsMaintenance = true,
                bootloader = status,
            )
        assertTrue(gate.showBootloaderUpgrade)
        assertNull(gate.latestBootloader, "a reported bootloader drops the restart hint the unreported path keeps")
    }
}
