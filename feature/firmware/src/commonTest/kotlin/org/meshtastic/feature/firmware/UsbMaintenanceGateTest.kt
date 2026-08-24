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
import org.meshtastic.core.model.SoftDeviceVariant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests the pure availability gate and pinned-asset resolvers.
 *
 * These are the safety-critical decisions for factory erase, so they live in a pure function with no fakes: the
 * dangerous direction (writing a wrong-SoftDevice erase image) cannot be tested on hardware without destroying a
 * device, which makes table-driven unit coverage of the refusal logic the only real verification available.
 *
 * Concrete (not a `Common*Test` base) because nothing here touches `CommonUri`, so it needs no platform subclass and
 * cannot silently contribute zero tests.
 */
class UsbMaintenanceGateTest {

    /**
     * The real maintenance-UF2 manifest ([api/data/maintenanceUf2.json] in `meshtastic/api`, embedded verbatim), so
     * this test suite keeps exercising the exact board/digest table that ships, not a hand-trimmed fixture that could
     * drift from it silently.
     */
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

    private fun nrf(
        variant: SoftDeviceVariant? = SoftDeviceVariant.S140_6_1_1,
        target: String = "rak4631",
        slug: String = "RAK4631",
    ) = DeviceHardware(
        hwModelSlug = slug,
        platformioTarget = target,
        architecture = "nrf52840",
        softDeviceVariant = variant,
    )

    private fun rp2040(target: String = "pico") =
        DeviceHardware(hwModelSlug = "RPI_PICO", platformioTarget = target, architecture = "rp2040")

    private fun esp32() =
        DeviceHardware(hwModelSlug = "HELTEC_V3", platformioTarget = "heltec-v3", architecture = "esp32-s3")

    // ── Architecture and transport gating (R11) ──────────────────────────────

    @Test
    fun `gate is shown for nrf52840 over usb with a release`() {
        val gate = usbMaintenanceGate(testManifest, nrf(), FirmwareUpdateMethod.Usb, hasRelease = true)

        assertTrue(gate.show, "nRF52840 over USB should offer maintenance")
        assertNull(gate.eraseRefusal, "A resolved SoftDevice must not refuse")
    }

    @Test
    fun `gate is shown for rp2040 over usb and never refuses on softdevice`() {
        val gate = usbMaintenanceGate(testManifest, rp2040(), FirmwareUpdateMethod.Usb, hasRelease = true)

        assertTrue(gate.show, "RP2040 over USB should offer maintenance")
        assertNull(gate.eraseRefusal, "RP2040 has no SoftDevice to resolve")
        assertFalse(gate.showBootloaderUpgrade, "OTAFIX is nRF-only")
    }

    /**
     * The manifest is now fetched from `resource/maintenanceUf2` rather than compiled in, so a hostile or corrupt row
     * is reachable input. A traversal-shaped `fileName` must refuse *that image* and leave the rest of the screen
     * working — throwing out of a resolver every caller documents as nullable would surface as a blanket
     * `FirmwareUpdateState.Error` and hide the maintenance action for every device.
     */
    @Test
    fun `an unsafe erase filename refuses the image instead of throwing`() {
        val hostile =
            Json { ignoreUnknownKeys = true }
                .decodeFromString<MaintenanceUf2Manifest>(
                    """
                    {
                      "erase": {
                        "nrf52": {
                          "6.1.1": { "fileName": "../../etc/passwd", "sha256": "00" }
                        },
                        "rp2040": { "fileName": "sub/dir/pico_erase.uf2", "sha256": "00" }
                      }
                    }
                    """
                        .trimIndent(),
                )

        assertNull(eraseUf2For(hostile, nrf()), "A traversal fileName must resolve to null, not throw")
        assertNull(eraseUf2For(hostile, rp2040()), "A separator in fileName must resolve to null, not throw")

        val gate = usbMaintenanceGate(hostile, rp2040(), FirmwareUpdateMethod.Usb, hasRelease = true)
        assertEquals(UsbMaintenanceRefusal.MaintenanceDataUnavailable, gate.eraseRefusal)
    }

    /** Same contract for the OTAFIX side, whose file name is composed from two manifest-supplied strings. */
    @Test
    fun `an unsafe otafix slug or tag refuses the image instead of throwing`() {
        val hostile =
            Json { ignoreUnknownKeys = true }
                .decodeFromString<MaintenanceUf2Manifest>(
                    """
                    {
                      "otafixReleaseTag": "../../../evil",
                      "otafixBase": "https://example.invalid/releases",
                      "otafixByBoardId": {
                        "rak4631": { "otafixBoardSlug": "rak4631", "sha256": "00" }
                      }
                    }
                    """
                        .trimIndent(),
                )

        assertNull(otafixUf2ForBoardId(hostile, "rak4631"), "A traversal release tag must resolve to null, not throw")
    }

    /**
     * RP2040 has a UF2 erase path, so an absent image is missing data — not an unsupported architecture. The two
     * refusals carry different copy ("try again" vs "not possible on this device").
     */
    @Test
    fun `rp2040 with no erase data reports missing data rather than unsupported architecture`() {
        val empty = MaintenanceUf2Manifest()

        val gate = usbMaintenanceGate(empty, rp2040(), FirmwareUpdateMethod.Usb, hasRelease = true)

        assertEquals(UsbMaintenanceRefusal.MaintenanceDataUnavailable, gate.eraseRefusal)
    }

    @Test
    fun `gate is hidden for esp32 even over usb`() {
        assertFalse(usbMaintenanceGate(testManifest, esp32(), FirmwareUpdateMethod.Usb, hasRelease = true).show)
    }

    @Test
    fun `gate is hidden for every non-usb transport`() {
        listOf(FirmwareUpdateMethod.Ble, FirmwareUpdateMethod.Wifi, FirmwareUpdateMethod.Unknown).forEach { method ->
            assertFalse(
                usbMaintenanceGate(testManifest, nrf(), method, hasRelease = true).show,
                "Maintenance must not be offered over $method — the flow needs the UF2 mass-storage drive",
            )
        }
    }

    @Test
    fun `gate is hidden without a release because there would be nothing to reflash`() {
        assertFalse(usbMaintenanceGate(testManifest, nrf(), FirmwareUpdateMethod.Usb, hasRelease = false).show)
    }

    // ── Fail-closed SoftDevice refusal (R4) ──────────────────────────────────

    @Test
    fun `unresolved softdevice shows the action but refuses it`() {
        val gate = usbMaintenanceGate(testManifest, nrf(variant = null), FirmwareUpdateMethod.Usb, hasRelease = true)

        assertTrue(gate.show, "The action stays visible so the refusal can be explained")
        assertEquals(UsbMaintenanceRefusal.UnknownSoftDevice, gate.eraseRefusal)
    }

    @Test
    fun `no erase image is resolved for an unresolved softdevice`() {
        assertNull(
            eraseUf2For(testManifest, nrf(variant = null)),
            "An unknown variant must never fall back to a default image",
        )
    }

    @Test
    fun `each softdevice variant resolves to its own image and target address`() {
        val six = eraseUf2For(testManifest, nrf(variant = SoftDeviceVariant.S140_6_1_1))
        val seven = eraseUf2For(testManifest, nrf(variant = SoftDeviceVariant.S140_7_3_0))

        assertNotNull(six)
        assertNotNull(seven)
        assertEquals("nrf_erase2.uf2", six.fileName)
        assertEquals("nrf_erase_sd7_3.uf2", seven.fileName)
        assertEquals(0x26000L, six.expectedFirstTargetAddress)
        assertEquals(0x27000L, seven.expectedFirstTargetAddress)
        assertTrue(six.sha256 != seven.sha256, "The two variants must not share a digest")
    }

    @Test
    fun `esp32 resolves no erase image`() {
        assertNull(eraseUf2For(testManifest, esp32()))
    }

    @Test
    fun `rp2040 resolves the pico erase image with no address invariant`() {
        val asset = eraseUf2For(testManifest, rp2040())

        assertNotNull(asset)
        assertEquals("pico_erase.uf2", asset.fileName)
        assertNull(asset.expectedFirstTargetAddress, "RP2040 carries no variant-to-address invariant")
    }

    // ── OTAFIX bootloader image resolution (R5) ──────────────────────────────

    @Test
    fun `otafix is offered only for supported targets`() {
        assertTrue(
            usbMaintenanceGate(testManifest, nrf(target = "rak4631"), FirmwareUpdateMethod.Usb, hasRelease = true)
                .showBootloaderUpgrade,
        )
        assertTrue(
            usbMaintenanceGate(
                testManifest,
                nrf(target = "heltec-mesh-node-t114"),
                FirmwareUpdateMethod.Usb,
                hasRelease = true,
            )
                .showBootloaderUpgrade,
            "T114 is on OTAFIX's supported list even though the project names differ",
        )
    }

    @Test
    fun `otafix is not offered for products that merely share a supported build target`() {
        // WISMESH Hub/Tap, Nomadstar Meteor Pro and RAK3401 all build against wiscore_rak4631, and T-Echo Plus/Lite
        // against t-echo, but OTAFIX ships no bootloader for those products.
        listOf(
            "rak2560",
            "rak_wismeshtap",
            "rak4631_nomadstar_meteor_pro",
            "rak3401-1watt",
            "t-echo-plus",
            "t-echo-lite",
        )
            .forEach { target ->
                assertFalse(
                    usbMaintenanceGate(testManifest, nrf(target = target), FirmwareUpdateMethod.Usb, hasRelease = true)
                        .showBootloaderUpgrade,
                    "$target is not an OTAFIX-supported product",
                )
            }
    }

    // ── Board-ID resolution: the actual safety gate (R5) ─────────────────────

    @Test
    fun `every shipped otafix image resolves and no two boards share a digest or filename`() {
        assertEquals(
            14,
            testManifest.otafixByBoardId.keys.size,
            "${testManifest.otafixReleaseTag} ships 14 update images",
        )
        val images =
            testManifest.otafixByBoardId.keys.map {
                assertNotNull(otafixUf2ForBoardId(testManifest, it), "no image for $it")
            }
        assertEquals(images.size, images.map { it.sha256 }.toSet().size, "No two boards may share a digest")
        assertEquals(images.size, images.map { it.fileName }.toSet().size, "No two boards may share a filename")
    }

    /**
     * Board-ID -> (release filename, sha256), transcribed from the actual `0.9.2-OTAFIX2.3-BP1.5` release assets
     * (downloaded and hashed, not carried over from BP1.4), so a future edit that pairs the right hash with the wrong
     * board — or vice versa — fails here even though it would still pass the uniqueness-only checks above.
     */
    private val expectedOtafixAssetsByBoardId =
        mapOf(
            "HT-n5262" to
                (
                    "update-heltec_t114_bootloader-0.9.2-OTAFIX2.3-BP1.5_nosd.uf2" to
                        "ae92d3577cb58dd9b43c9b61ffb9bfffda05b0eca4113a0ec42a37cd8be53b19"
                    ),
            "MinewSemi-MX25LE01" to
                (
                    "update-minewsemi_mx25le01_bootloader-0.9.2-OTAFIX2.3-BP1.5_nosd.uf2" to
                        "e09564fd8dd03fc25d76dcb732a0214c79653da3b130240949b783254d3dfc1b"
                    ),
            "TRACKER L1" to
                (
                    "update-wio_tracker_l1_bootloader-0.9.2-OTAFIX2.3-BP1.5_nosd.uf2" to
                        "70fbce0eda9d70d7bd8a4367057badf5ec310838bf3221370d45a56f04956b9e"
                    ),
            "WisBlock-RAK4631-Board" to
                (
                    "update-wiscore_rak4631_board_bootloader-0.9.2-OTAFIX2.3-BP1.5_nosd.uf2" to
                        "8741bc677a3c24f28422c5ffb80761de7d98a127a3b0191ba6585bf57ce9f305"
                    ),
            "WisMesh-Tag" to
                (
                    "update-wismesh_tag_bootloader-0.9.2-OTAFIX2.3-BP1.5_nosd.uf2" to
                        "96d42e1990e17251e8c625e98a1551cac12c6e29111bc2e59ab7c9fe6dec8758"
                    ),
            "nRF52840-SeeedSenseCAPSolarP1-v1" to
                (
                    "update-sensecap_solar_p1_bootloader-0.9.2-OTAFIX2.3-BP1.5_nosd.uf2" to
                        "9b4bce48c1b4830617715c5619457bce6b21f3079803e35e13433de7701290f5"
                    ),
            "nRF52840-SeeedXiao-v1" to
                (
                    "update-xiao_nrf52840_ble_bootloader-0.9.2-OTAFIX2.3-BP1.5_nosd.uf2" to
                        "ff8a0916e98cceb394fd66590bccc17f63612c11ff56b086ef88bd436c8df67f"
                    ),
            "nRF52840-SeeedXiaoSense-v1" to
                (
                    "update-xiao_nrf52840_ble_sense_bootloader-0.9.2-OTAFIX2.3-BP1.5_nosd.uf2" to
                        "fc233d83a1011419625fcb50b49084578460c25bbc0270374ca176757a3c40da"
                    ),
            "nRF52840-T1000-E-v1" to
                (
                    "update-t1000_e_bootloader-0.9.2-OTAFIX2.3-BP1.5_nosd.uf2" to
                        "5c065e11b8acd5b0cefa9295f98bca1512306cfa478856aa76a871124a904cc4"
                    ),
            "nRF52840-TEcho-v1" to
                (
                    "update-lilygo_techo_bootloader-0.9.2-OTAFIX2.3-BP1.5_nosd.uf2" to
                        "2ddb36188ffe521c270bb2ce8441d742d0fe45325c57e4db6475bf63162a59b0"
                    ),
            "nRF52840-ThinkNode-M3-v1" to
                (
                    "update-thinknode_m3_bootloader-0.9.2-OTAFIX2.3-BP1.5_nosd.uf2" to
                        "bf90979f2f6adc96ef6ca09c280b2ab7e66cb8ce2654fc80da9b20407bfb8708"
                    ),
            "nRF52840-ThinkNodeM1-v1" to
                (
                    "update-thinknode_m1_bootloader-0.9.2-OTAFIX2.3-BP1.5_nosd.uf2" to
                        "aa0721b573c60e0b179274d5a5296bac7a8436faf339cfc03116ebe8a4375795"
                    ),
            "nRF52840-ThinkNodeM6-v1" to
                (
                    "update-thinknode_m6_bootloader-0.9.2-OTAFIX2.3-BP1.5_nosd.uf2" to
                        "aaf94953a540a18f3e48f4cdec0c78290ad3c5f8740aea26fa3b3ce3632a8d4a"
                    ),
            "nRF52840-promicro" to
                (
                    "update-promicro_nrf52840_bootloader-0.9.2-OTAFIX2.3-BP1.5_nosd.uf2" to
                        "46ef3440f151d6f2606075bcd1aa83db25a660da7d25b988aeb47ef350c98794"
                    ),
        )

    @Test
    fun `every otafix board id maps to its exact expected release filename and digest`() {
        assertEquals(
            expectedOtafixAssetsByBoardId.keys,
            testManifest.otafixByBoardId.keys,
            "This test's expectation table and the shipped board-id map have drifted apart",
        )
        expectedOtafixAssetsByBoardId.forEach { (boardId, expected) ->
            val (expectedFileName, expectedSha256) = expected
            val image = assertNotNull(otafixUf2ForBoardId(testManifest, boardId), "no image for $boardId")
            assertEquals(expectedFileName, image.fileName, "$boardId: wrong release filename")
            assertEquals(expectedSha256, image.sha256, "$boardId: wrong digest")
        }
    }

    @Test
    fun `board id selects the image rather than the build target`() {
        val rak = otafixUf2ForBoardId(testManifest, "WisBlock-RAK4631-Board")
        val techo = otafixUf2ForBoardId(testManifest, "nRF52840-TEcho-v1")

        assertNotNull(rak)
        assertNotNull(techo)
        assertTrue(rak.fileName.contains("wiscore_rak4631_board"))
        assertTrue(techo.fileName.contains("lilygo_techo"))
        // Both boards report USB 239A/0029 in bootloader mode, so USB identity could not have told them apart.
        assertTrue(rak.sha256 != techo.sha256)
    }

    @Test
    fun `xiao sense is distinguished from plain xiao only by board id`() {
        val plain = otafixUf2ForBoardId(testManifest, "nRF52840-SeeedXiao-v1")
        val sense = otafixUf2ForBoardId(testManifest, "nRF52840-SeeedXiaoSense-v1")

        assertNotNull(plain)
        assertNotNull(sense)
        assertTrue(plain.fileName.contains("xiao_nrf52840_ble_bootloader"))
        assertTrue(sense.fileName.contains("xiao_nrf52840_ble_sense"))
        assertTrue(plain.sha256 != sense.sha256, "OTAFIX's README warns these must not be interchanged")
    }

    @Test
    fun `an unrecognized board id refuses rather than falling back`() {
        assertNull(otafixUf2ForBoardId(testManifest, "SomeOtherBoard-v9"))
        assertNull(otafixUf2ForBoardId(testManifest, ""))
    }

    @Test
    fun `board id is parsed from an INFO_UF2 payload and tolerates surrounding lines`() {
        val info =
            "UF2 Bootloader 0.9.2-OTAFIX2.2-BP1.3\r\n" +
                "Model: WisBlock RAK4631 Board\r\n" +
                "Board-ID: WisBlock-RAK4631-Board\r\n" +
                "Date: Apr 13 2026\r\n"

        assertEquals("WisBlock-RAK4631-Board", parseUf2BoardId(info))
        assertNotNull(parseUf2BoardId(info)?.let { otafixUf2ForBoardId(testManifest, it) })
    }

    @Test
    fun `a payload without a board id line yields null`() {
        assertNull(parseUf2BoardId("UF2 Bootloader 0.2.6\r\nModel: Something\r\n"))
        assertNull(parseUf2BoardId(""), "A volume with no INFO_UF2.TXT is not a UF2 bootloader drive")
    }

    @Test
    fun `board id tolerates leading whitespace on its line just like the softdevice line`() {
        assertEquals("WisBlock-RAK4631-Board", parseUf2BoardId("  Board-ID: WisBlock-RAK4631-Board\r\n"))
    }

    // ── SoftDevice read from the drive: the authoritative gate (R4) ───────────

    /** Verbatim `INFO_UF2.TXT` from a stock Seeed Wio Tracker L1 (hwModel 99), captured 2026-07-30. */
    private val seeedL1Info =
        "UF2 Bootloader 0.9.2-dirty lib/nrfx (v2.0.0) lib/tinyusb (0.12.0-145-g9775e7691) " +
            "lib/uf2 (remotes/origin/configupdate-9-gadbb8c7)\r\n" +
            "Model: Seeed TRACKER L1\r\n" +
            "Board-ID: TRACKER L1\r\n" +
            "Date: May 15 2025\r\n" +
            "SoftDevice: S140 7.3.0\r\n"

    @Test
    fun `softdevice and board id are both read from a real stock bootloader payload`() {
        assertEquals(SoftDeviceVariant.S140_7_3_0, parseUf2SoftDevice(seeedL1Info))
        assertEquals("TRACKER L1", parseUf2BoardId(seeedL1Info))
        // The stock Seeed bootloader reports the same Board-ID as OTAFIX's build for this board.
        assertNotNull(otafixUf2ForBoardId(testManifest, parseUf2BoardId(seeedL1Info)!!))
    }

    /** Verbatim `INFO_UF2.TXT` from a RAK4631 running OTAFIX 2.2-BP1.3 (hwModel 9), captured 2026-07-30. */
    private val rak4631OtafixInfo =
        "UF2 Bootloader 0.9.2-OTAFIX2.2-BP1.3 lib/nrfx (v2.0.0) lib/tinyusb (0.12.0-145-g9775e7691) " +
            "lib/uf2 (remotes/origin/configupdate-9-gadbb8c7)\r\n" +
            "Model: WisBlock RAK4631 Board\r\n" +
            "Board-ID: WisBlock-RAK4631-Board\r\n" +
            "Date: Apr 13 2026\r\n" +
            "SoftDevice: S140 6.1.1\r\n"

    /**
     * Verbatim `INFO_UF2.TXT` from a RAK4631 on a stock 0.4.3 bootloader (May 2023), captured 2026-07-30.
     *
     * Two things this vintage proves: the `SoftDevice:` line goes back at least this far, so the bundled-map fallback
     * is belt-and-braces rather than the common path; and older bootloaders emit an extra `Ver:` line that 0.9.x
     * dropped, which the line-scanning parser must tolerate.
     */
    private val rak4631StockInfo =
        "UF2 Bootloader 0.4.3\r\n" +
            "Model: WisBlock RAK4631 Board\r\n" +
            "Board-ID: WisBlock-RAK4631-Board\r\n" +
            "Date: May 20 2023\r\n" +
            "Ver: 0.4.3\r\n" +
            "SoftDevice: S140 6.1.1\r\n"

    @Test
    fun `an old bootloader vintage still reports its softdevice and board id`() {
        assertEquals(SoftDeviceVariant.S140_6_1_1, parseUf2SoftDevice(rak4631StockInfo))
        assertEquals("WisBlock-RAK4631-Board", parseUf2BoardId(rak4631StockInfo))
    }

    @Test
    fun `board id is stable across bootloader vintages for the same board`() {
        // A stock 0.4.3 RAK and an OTAFIX 2.2 RAK report the same Board-ID, so the OTAFIX veto resolves correctly on a
        // device that has never been upgraded — the case that decides whether the upgrade is offerable at all.
        assertEquals(parseUf2BoardId(rak4631StockInfo), parseUf2BoardId(rak4631OtafixInfo))
    }

    @Test
    fun `both variants are parsed from real captured bootloader payloads`() {
        // The two sides of the split, each from hardware: an OTAFIX RAK4631 and a stock Seeed L1.
        assertEquals(SoftDeviceVariant.S140_6_1_1, parseUf2SoftDevice(rak4631OtafixInfo))
        assertEquals(SoftDeviceVariant.S140_7_3_0, parseUf2SoftDevice(seeedL1Info))

        assertEquals("WisBlock-RAK4631-Board", parseUf2BoardId(rak4631OtafixInfo))
        assertNotNull(
            otafixUf2ForBoardId(testManifest, parseUf2BoardId(rak4631OtafixInfo)!!),
            "An OTAFIX-flashed device must still resolve its own image, so re-running the upgrade is idempotent",
        )
    }

    @Test
    fun `softdevice line is parsed for both shipped variants`() {
        assertEquals(SoftDeviceVariant.S140_6_1_1, parseUf2SoftDevice("SoftDevice: S140 6.1.1\r\n"))
        assertEquals(SoftDeviceVariant.S140_7_3_0, parseUf2SoftDevice("SoftDevice: S140 7.3.0\r\n"))
    }

    @Test
    fun `each erase image targets the app start of the softdevice it is linked for`() {
        // Verified against hardware 2026-07-30: on a 6.1.1 RAK4631 the app vector table sits at 0x26000
        // (sp=0x20040000, top of nRF52840 RAM), and 0x27000 is mid-application. On a 7.3.0 device the app starts at
        // 0x27000, which makes 0x26000 the SoftDevice's last page — the direction that corrupts.
        val six = assertNotNull(eraseUf2ForVariant(testManifest, SoftDeviceVariant.S140_6_1_1))
        val seven = assertNotNull(eraseUf2ForVariant(testManifest, SoftDeviceVariant.S140_7_3_0))
        assertEquals(0x26000L, six.expectedFirstTargetAddress)
        assertEquals(0x27000L, seven.expectedFirstTargetAddress)
    }

    @Test
    fun `unsupported softdevice ids and absent lines yield null`() {
        assertNull(parseUf2SoftDevice("SoftDevice: S132 7.3.0\r\n"), "S132 is not an nRF52840 SoftDevice")
        assertNull(parseUf2SoftDevice("SoftDevice: S140 9.9.9\r\n"), "No erase image exists for an unknown version")
        assertNull(parseUf2SoftDevice("SoftDevice: \r\n"), "The bootloader omits the value when no SD is installed")
        assertNull(parseUf2SoftDevice("Board-ID: TRACKER L1\r\n"), "Absent line")
    }

    @Test
    fun `the drive outranks the bundled map`() {
        // The map says 6.1.1, the device says 7.3.0 with nothing else to go on — but they disagree, so refuse.
        val conflict =
            resolveNrfEraseImage(
                testManifest,
                mapped = SoftDeviceVariant.S140_6_1_1,
                reportedFromDrive = SoftDeviceVariant.S140_7_3_0,
            )
        assertTrue(conflict is EraseImageResolution.Conflict, "Disagreement must refuse, not pick a side")
    }

    @Test
    fun `agreement resolves to the reported variant's image`() {
        val resolved =
            resolveNrfEraseImage(
                testManifest,
                mapped = SoftDeviceVariant.S140_7_3_0,
                reportedFromDrive = SoftDeviceVariant.S140_7_3_0,
            )

        assertTrue(resolved is EraseImageResolution.Resolved)
        assertEquals("nrf_erase_sd7_3.uf2", resolved.asset.fileName)
        assertEquals(0x27000L, resolved.asset.expectedFirstTargetAddress)
    }

    @Test
    fun `an old bootloader with no softdevice line falls back to the bundled map`() {
        val resolved =
            resolveNrfEraseImage(testManifest, mapped = SoftDeviceVariant.S140_6_1_1, reportedFromDrive = null)

        assertTrue(resolved is EraseImageResolution.Resolved)
        assertEquals("nrf_erase2.uf2", resolved.asset.fileName)
    }

    @Test
    fun `a drive report rescues an unmapped model`() {
        // THINKNODE_M8 has no firmware variant on master and so no map row; the drive can still answer.
        val resolved =
            resolveNrfEraseImage(testManifest, mapped = null, reportedFromDrive = SoftDeviceVariant.S140_6_1_1)

        assertTrue(resolved is EraseImageResolution.Resolved)
        assertEquals(SoftDeviceVariant.S140_6_1_1, resolved.variant)
    }

    @Test
    fun `neither source resolving means unresolved`() {
        assertEquals(
            EraseImageResolution.Unresolved,
            resolveNrfEraseImage(testManifest, mapped = null, reportedFromDrive = null),
        )
    }

    // ── UF2 header parsing (R8) ──────────────────────────────────────────────

    @Test
    fun `uf2 target address is read from the first block`() {
        val block = ByteArray(UF2_BLOCK_BYTES)
        listOf(0x55, 0x46, 0x32, 0x0A).forEachIndexed { i, b -> block[i] = b.toByte() }
        block[UF2_TARGET_ADDR_OFFSET] = 0x00
        block[UF2_TARGET_ADDR_OFFSET + 1] = 0x70
        block[UF2_TARGET_ADDR_OFFSET + 2] = 0x02
        block[UF2_TARGET_ADDR_OFFSET + 3] = 0x00

        assertEquals(0x27000L, uf2FirstTargetAddress(block))
    }

    @Test
    fun `non-uf2 payloads yield no target address`() {
        assertNull(uf2FirstTargetAddress(ByteArray(UF2_BLOCK_BYTES)), "Zeroed bytes carry no UF2 magic")
        assertNull(uf2FirstTargetAddress(ByteArray(16)), "A short payload cannot hold a UF2 block")
    }
}
