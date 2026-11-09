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

import kotlinx.serialization.json.Json
import org.meshtastic.core.model.DeviceHardware
import org.meshtastic.core.model.MaintenanceUf2Manifest
import org.meshtastic.core.model.OtafixBleDfuEntry
import org.meshtastic.core.model.SoftDeviceVariant
import org.meshtastic.feature.firmware.ota.dfu.BootloaderIdentity
import org.meshtastic.feature.firmware.ota.dfu.DfuException
import org.meshtastic.feature.firmware.ota.dfu.requireBootloaderMatches
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class BleBootloaderUpgradeGateTest {

    private val manifest =
        MaintenanceUf2Manifest(
            otafixReleaseTag = "2.6.0",
            otafixBase = "https://github.com/meshtastic/nRF52_Bootloader/releases/download/2.6.0",
            otafixBleDfuByTarget =
            mapOf(
                "rak4631" to
                    OtafixBleDfuEntry(
                        otafixBoardSlug = "wiscore_rak4631_board",
                        softDevice = "6.1.1",
                        bleModel = "WisBlock RAK4631",
                        sha256 = "6241730ee6c376ea5907d7cd0a672455528129379baf72936ed395fe64446cbf",
                    ),
            ),
        )

    private val rak4631 =
        DeviceHardware(
            hwModelSlug = "RAK4631",
            platformioTarget = "rak4631",
            architecture = "nrf52840",
            softDeviceVariant = SoftDeviceVariant.S140_6_1_1,
        )

    private val latest = KotlinVersion(2, 6, 0)

    private fun gate(
        bootloader: BootloaderStatus?,
        hardware: DeviceHardware = rak4631,
        method: FirmwareUpdateMethod = FirmwareUpdateMethod.Ble,
        hasRelease: Boolean = true,
    ) = bleBootloaderUpgradeFor(manifest, hardware, method, hasRelease, bootloader)

    @Test
    fun offersThePinnedPackageForAnOlderBootloader() {
        val zip = assertNotNull(gate(BootloaderStatus(InstalledBootloader.Known(KotlinVersion(2, 5, 0)), latest)))
        assertEquals("wiscore_rak4631_board_bootloader-2.6.0_s140_6.1.1.zip", zip.fileName)
        assertEquals("${manifest.otafixBase}/${zip.fileName}", zip.url)
        assertEquals("WisBlock RAK4631", zip.bleModel)
        assertEquals(SoftDeviceVariant.S140_6_1_1, zip.softDevice)
    }

    @Test
    fun offersForTheSharedLegacyVersion() {
        assertNotNull(gate(BootloaderStatus(InstalledBootloader.OlderThanFirstNumbered, latest)))
    }

    @Test
    fun hiddenWhenTheDeviceReportsNoBootloaderVersion() {
        assertNull(gate(null))
    }

    @Test
    fun hiddenWhenTheBootloaderIsCurrent() {
        assertNull(gate(BootloaderStatus(InstalledBootloader.Known(latest), latest)))
        assertNull(gate(BootloaderStatus(InstalledBootloader.Known(KotlinVersion(2, 7, 0)), latest)))
    }

    @Test
    fun hiddenWhenTheLatestTagHasNoOrder() {
        assertNull(gate(BootloaderStatus(InstalledBootloader.Known(KotlinVersion(2, 5, 0)), latest = null)))
    }

    @Test
    fun hiddenOffBleWithoutAReleaseOrOffNrf52() {
        val older = BootloaderStatus(InstalledBootloader.OlderThanFirstNumbered, latest)
        assertNull(gate(older, method = FirmwareUpdateMethod.Usb))
        assertNull(gate(older, hasRelease = false))
        assertNull(gate(older, hardware = rak4631.copy(architecture = "esp32-s3")))
    }

    @Test
    fun hiddenForAnUnmappedTarget() {
        val older = BootloaderStatus(InstalledBootloader.OlderThanFirstNumbered, latest)
        assertNull(gate(older, hardware = rak4631.copy(platformioTarget = "t-echo")))
    }

    @Test
    fun refusesWhenTheQuirksSoftDeviceDisagreesWithThePackage() {
        val older = BootloaderStatus(InstalledBootloader.OlderThanFirstNumbered, latest)
        assertNull(gate(older, hardware = rak4631.copy(softDeviceVariant = SoftDeviceVariant.S140_7_3_0)))
    }

    @Test
    fun refusesAnUnknownSoftDeviceOrAnUnsafeSlug() {
        val row = manifest.otafixBleDfuByTarget.getValue("rak4631")
        val badSd = manifest.copy(otafixBleDfuByTarget = mapOf("rak4631" to row.copy(softDevice = "8.0.0")))
        assertNull(bootloaderDfuZipFor(badSd, "rak4631", null))
        val badSlug = manifest.copy(otafixBleDfuByTarget = mapOf("rak4631" to row.copy(otafixBoardSlug = "../x")))
        assertNull(bootloaderDfuZipFor(badSlug, "rak4631", null))
    }

    @Test
    fun manifestsWithoutBlePackagesStillParseAndOfferNothing() {
        val json = Json { ignoreUnknownKeys = true }
        val old = json.decodeFromString<MaintenanceUf2Manifest>("""{"otafixReleaseTag":"2.6.0","otafixBase":"b"}""")
        assertEquals(emptyMap(), old.otafixBleDfuByTarget)
        assertNull(bootloaderDfuZipFor(old, "rak4631", null))

        val current =
            json.decodeFromString<MaintenanceUf2Manifest>(
                """
                {"otafixReleaseTag":"2.6.0","otafixBase":"b","otafixBleDfuByTarget":{"rak4631":{
                "otafixBoardSlug":"wiscore_rak4631_board","softDevice":"6.1.1","bleModel":"WisBlock RAK4631",
                "sha256":"6241730ee6c376ea5907d7cd0a672455528129379baf72936ed395fe64446cbf"}}}
                """
                    .trimIndent(),
            )
        assertEquals(manifest.otafixBleDfuByTarget, current.otafixBleDfuByTarget)
    }

    @Test
    fun identityMustNameTheBoardAndSoftDevice() {
        val zip = assertNotNull(bootloaderDfuZipFor(manifest, "rak4631", null))

        requireBootloaderMatches(BootloaderIdentity("WisBlock RAK4631", "0.9.2-OTAFIX2.5 s140 6.1.1"), zip)

        val refused =
            listOf(
                BootloaderIdentity("WisMesh Tag", "2.6.0 s140 6.1.1"),
                BootloaderIdentity("WisBlock RAK4631", "2.6.0 s140 7.3.0"),
                BootloaderIdentity(null, "2.6.0 s140 6.1.1"),
                BootloaderIdentity("WisBlock RAK4631", null),
            )
        for (identity in refused) {
            assertFailsWith<DfuException.BootloaderMismatch>(identity.toString()) {
                requireBootloaderMatches(identity, zip)
            }
        }
    }
}
