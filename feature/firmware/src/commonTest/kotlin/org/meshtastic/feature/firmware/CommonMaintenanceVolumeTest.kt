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

    /** The real maintenance-UF2 manifest, embedded verbatim — see UsbMaintenanceGateTest for why. */
    private val testManifest =
        Json { ignoreUnknownKeys = true }
            .decodeFromString<MaintenanceUf2Manifest>(
                """
                {
                  "manifestVersion": 1,
                  "otafixReleaseTag": "0.9.2-OTAFIX2.3-BP1.5",
                  "otafixBase": "https://github.com/meshtastic/Adafruit_nRF52_Bootloader_OTAFIX/releases/download/0.9.2-OTAFIX2.3-BP1.5",
                  "erase": {
                    "nrf52": {
                      "6.1.1": {
                        "fileName": "nrf_erase2.uf2",
                        "sha256": "4b778a3def19854415db64cb51bfd29c15b11cc46006353dd518f62d09efe3fe",
                        "expectedFirstTargetAddress": 155648
                      },
                      "7.3.0": {
                        "fileName": "nrf_erase_sd7_3.uf2",
                        "sha256": "13941bedce009e61255c37b1524d11ca604e88c38e7588bb8b391e2998da468f",
                        "expectedFirstTargetAddress": 159744
                      }
                    },
                    "rp2040": {
                      "fileName": "pico_erase.uf2",
                      "sha256": "08aa7d561e8b8bf2f9b061b3506fb4d8f135e832efe0f3ae978241db2da0c853"
                    }
                  },
                  "otafixByBoardId": {
                    "HT-n5262": {
                      "otafixBoardSlug": "heltec_t114",
                      "sha256": "ae92d3577cb58dd9b43c9b61ffb9bfffda05b0eca4113a0ec42a37cd8be53b19"
                    },
                    "MinewSemi-MX25LE01": {
                      "otafixBoardSlug": "minewsemi_mx25le01",
                      "sha256": "e09564fd8dd03fc25d76dcb732a0214c79653da3b130240949b783254d3dfc1b"
                    },
                    "TRACKER L1": {
                      "otafixBoardSlug": "wio_tracker_l1",
                      "sha256": "70fbce0eda9d70d7bd8a4367057badf5ec310838bf3221370d45a56f04956b9e"
                    },
                    "WisBlock-RAK4631-Board": {
                      "otafixBoardSlug": "wiscore_rak4631_board",
                      "sha256": "8741bc677a3c24f28422c5ffb80761de7d98a127a3b0191ba6585bf57ce9f305"
                    },
                    "WisMesh-Tag": {
                      "otafixBoardSlug": "wismesh_tag",
                      "sha256": "96d42e1990e17251e8c625e98a1551cac12c6e29111bc2e59ab7c9fe6dec8758"
                    },
                    "nRF52840-SeeedSenseCAPSolarP1-v1": {
                      "otafixBoardSlug": "sensecap_solar_p1",
                      "sha256": "9b4bce48c1b4830617715c5619457bce6b21f3079803e35e13433de7701290f5"
                    },
                    "nRF52840-SeeedXiao-v1": {
                      "otafixBoardSlug": "xiao_nrf52840_ble",
                      "sha256": "ff8a0916e98cceb394fd66590bccc17f63612c11ff56b086ef88bd436c8df67f"
                    },
                    "nRF52840-SeeedXiaoSense-v1": {
                      "otafixBoardSlug": "xiao_nrf52840_ble_sense",
                      "sha256": "fc233d83a1011419625fcb50b49084578460c25bbc0270374ca176757a3c40da"
                    },
                    "nRF52840-T1000-E-v1": {
                      "otafixBoardSlug": "t1000_e",
                      "sha256": "5c065e11b8acd5b0cefa9295f98bca1512306cfa478856aa76a871124a904cc4"
                    },
                    "nRF52840-TEcho-v1": {
                      "otafixBoardSlug": "lilygo_techo",
                      "sha256": "2ddb36188ffe521c270bb2ce8441d742d0fe45325c57e4db6475bf63162a59b0"
                    },
                    "nRF52840-ThinkNode-M3-v1": {
                      "otafixBoardSlug": "thinknode_m3",
                      "sha256": "bf90979f2f6adc96ef6ca09c280b2ab7e66cb8ce2654fc80da9b20407bfb8708"
                    },
                    "nRF52840-ThinkNodeM1-v1": {
                      "otafixBoardSlug": "thinknode_m1",
                      "sha256": "aa0721b573c60e0b179274d5a5296bac7a8436faf339cfc03116ebe8a4375795"
                    },
                    "nRF52840-ThinkNodeM6-v1": {
                      "otafixBoardSlug": "thinknode_m6",
                      "sha256": "aaf94953a540a18f3e48f4cdec0c78290ad3c5f8740aea26fa3b3ce3632a8d4a"
                    },
                    "nRF52840-promicro": {
                      "otafixBoardSlug": "promicro_nrf52840",
                      "sha256": "46ef3440f151d6f2606075bcd1aa83db25a660da7d25b988aeb47ef350c98794"
                    }
                  },
                  "otafixSupportedTargets": [
                    "rak4631",
                    "rak_wismeshtag",
                    "t-echo",
                    "heltec-mesh-node-t114",
                    "nrf52_promicro_diy_tcxo",
                    "thinknode_m1",
                    "thinknode_m3",
                    "thinknode_m6",
                    "tracker-t1000-e",
                    "seeed_wio_tracker_L1",
                    "seeed_wio_tracker_L1_eink",
                    "seeed_solar_node",
                    "seeed_xiao_nrf52840_kit"
                  ]
                }
                """
                    .trimIndent(),
            )

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
                testManifest,
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
                testManifest,
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
                testManifest,
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
                testManifest,
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
                testManifest,
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
                testManifest,
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
                testManifest,
                UsbMaintenanceRequest.BootloaderUpgrade,
                nrf(SoftDeviceVariant.S140_6_1_1),
                MaintenanceVolume("RPI-RP2", softDevice = null),
            )

        assertEquals(UsbMaintenanceRefusal.UnknownBoardId, assertIs<MaintenanceImageChoice.Refused>(choice).reason)
    }
}
