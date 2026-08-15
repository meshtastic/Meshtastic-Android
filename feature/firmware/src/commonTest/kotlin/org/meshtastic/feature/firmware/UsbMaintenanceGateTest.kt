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
        val gate = usbMaintenanceGate(nrf(), FirmwareUpdateMethod.Usb, hasRelease = true)

        assertTrue(gate.show, "nRF52840 over USB should offer maintenance")
        assertNull(gate.eraseRefusal, "A resolved SoftDevice must not refuse")
    }

    @Test
    fun `gate is shown for rp2040 over usb and never refuses on softdevice`() {
        val gate = usbMaintenanceGate(rp2040(), FirmwareUpdateMethod.Usb, hasRelease = true)

        assertTrue(gate.show, "RP2040 over USB should offer maintenance")
        assertNull(gate.eraseRefusal, "RP2040 has no SoftDevice to resolve")
        assertFalse(gate.showBootloaderUpgrade, "OTAFIX is nRF-only")
    }

    @Test
    fun `gate is hidden for esp32 even over usb`() {
        assertFalse(usbMaintenanceGate(esp32(), FirmwareUpdateMethod.Usb, hasRelease = true).show)
    }

    @Test
    fun `gate is hidden for every non-usb transport`() {
        listOf(FirmwareUpdateMethod.Ble, FirmwareUpdateMethod.Wifi, FirmwareUpdateMethod.Unknown).forEach { method ->
            assertFalse(
                usbMaintenanceGate(nrf(), method, hasRelease = true).show,
                "Maintenance must not be offered over $method — the flow needs the UF2 mass-storage drive",
            )
        }
    }

    @Test
    fun `gate is hidden without a release because there would be nothing to reflash`() {
        assertFalse(usbMaintenanceGate(nrf(), FirmwareUpdateMethod.Usb, hasRelease = false).show)
    }

    // ── Fail-closed SoftDevice refusal (R4) ──────────────────────────────────

    @Test
    fun `unresolved softdevice shows the action but refuses it`() {
        val gate = usbMaintenanceGate(nrf(variant = null), FirmwareUpdateMethod.Usb, hasRelease = true)

        assertTrue(gate.show, "The action stays visible so the refusal can be explained")
        assertEquals(UsbMaintenanceRefusal.UnknownSoftDevice, gate.eraseRefusal)
    }

    @Test
    fun `no erase image is resolved for an unresolved softdevice`() {
        assertNull(eraseUf2For(nrf(variant = null)), "An unknown variant must never fall back to a default image")
    }

    @Test
    fun `each softdevice variant resolves to its own image and target address`() {
        val six = eraseUf2For(nrf(variant = SoftDeviceVariant.S140_6_1_1))
        val seven = eraseUf2For(nrf(variant = SoftDeviceVariant.S140_7_3_0))

        assertNotNull(six)
        assertNotNull(seven)
        assertEquals("nrf_erase2.uf2", six.fileName)
        assertEquals("nrf_erase_sd7_3.uf2", seven.fileName)
        assertEquals(APP_START_S140_6_1_1, six.expectedFirstTargetAddress)
        assertEquals(APP_START_S140_7_3_0, seven.expectedFirstTargetAddress)
        assertTrue(six.sha256 != seven.sha256, "The two variants must not share a digest")
    }

    @Test
    fun `esp32 resolves no erase image`() {
        assertNull(eraseUf2For(esp32()))
    }

    @Test
    fun `rp2040 resolves the pico erase image with no address invariant`() {
        val asset = eraseUf2For(rp2040())

        assertNotNull(asset)
        assertEquals("pico_erase.uf2", asset.fileName)
        assertNull(asset.expectedFirstTargetAddress, "RP2040 carries no variant-to-address invariant")
    }

    // ── OTAFIX bootloader image resolution (R5) ──────────────────────────────

    @Test
    fun `otafix is offered only for supported targets`() {
        assertTrue(
            usbMaintenanceGate(nrf(target = "rak4631"), FirmwareUpdateMethod.Usb, hasRelease = true)
                .showBootloaderUpgrade,
        )
        assertTrue(
            usbMaintenanceGate(nrf(target = "heltec-mesh-node-t114"), FirmwareUpdateMethod.Usb, hasRelease = true)
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
                    usbMaintenanceGate(nrf(target = target), FirmwareUpdateMethod.Usb, hasRelease = true)
                        .showBootloaderUpgrade,
                    "$target is not an OTAFIX-supported product",
                )
            }
    }

    // ── Board-ID resolution: the actual safety gate (R5) ─────────────────────

    @Test
    fun `every shipped otafix image resolves and no two boards share a digest or filename`() {
        assertEquals(14, otafixBoardIds.size, "OTAFIX 2.2-BP1.3 ships 14 update images")
        val images = otafixBoardIds.map { assertNotNull(otafixUf2ForBoardId(it), "no image for $it") }
        assertEquals(images.size, images.map { it.sha256 }.toSet().size, "No two boards may share a digest")
        assertEquals(images.size, images.map { it.fileName }.toSet().size, "No two boards may share a filename")
    }

    @Test
    fun `board id selects the image rather than the build target`() {
        val rak = otafixUf2ForBoardId("WisBlock-RAK4631-Board")
        val techo = otafixUf2ForBoardId("nRF52840-TEcho-v1")

        assertNotNull(rak)
        assertNotNull(techo)
        assertTrue(rak.fileName.contains("wiscore_rak4631_board"))
        assertTrue(techo.fileName.contains("lilygo_techo"))
        // Both boards report USB 239A/0029 in bootloader mode, so USB identity could not have told them apart.
        assertTrue(rak.sha256 != techo.sha256)
    }

    @Test
    fun `xiao sense is distinguished from plain xiao only by board id`() {
        val plain = otafixUf2ForBoardId("nRF52840-SeeedXiao-v1")
        val sense = otafixUf2ForBoardId("nRF52840-SeeedXiaoSense-v1")

        assertNotNull(plain)
        assertNotNull(sense)
        assertTrue(plain.fileName.contains("xiao_nrf52840_ble_bootloader"))
        assertTrue(sense.fileName.contains("xiao_nrf52840_ble_sense"))
        assertTrue(plain.sha256 != sense.sha256, "OTAFIX's README warns these must not be interchanged")
    }

    @Test
    fun `an unrecognized board id refuses rather than falling back`() {
        assertNull(otafixUf2ForBoardId("SomeOtherBoard-v9"))
        assertNull(otafixUf2ForBoardId(""))
    }

    @Test
    fun `board id is parsed from an INFO_UF2 payload and tolerates surrounding lines`() {
        val info =
            "UF2 Bootloader 0.9.2-OTAFIX2.2-BP1.3\r\n" +
                "Model: WisBlock RAK4631 Board\r\n" +
                "Board-ID: WisBlock-RAK4631-Board\r\n" +
                "Date: Apr 13 2026\r\n"

        assertEquals("WisBlock-RAK4631-Board", parseUf2BoardId(info))
        assertNotNull(parseUf2BoardId(info)?.let { otafixUf2ForBoardId(it) })
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
        assertNotNull(otafixUf2ForBoardId(parseUf2BoardId(seeedL1Info)!!))
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
            otafixUf2ForBoardId(parseUf2BoardId(rak4631OtafixInfo)!!),
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
        assertEquals(0x26000L, APP_START_S140_6_1_1)
        assertEquals(0x27000L, APP_START_S140_7_3_0)
        assertEquals(APP_START_S140_6_1_1, eraseUf2ForVariant(SoftDeviceVariant.S140_6_1_1).expectedFirstTargetAddress)
        assertEquals(APP_START_S140_7_3_0, eraseUf2ForVariant(SoftDeviceVariant.S140_7_3_0).expectedFirstTargetAddress)
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
                mapped = SoftDeviceVariant.S140_6_1_1,
                reportedFromDrive = SoftDeviceVariant.S140_7_3_0,
            )
        assertTrue(conflict is EraseImageResolution.Conflict, "Disagreement must refuse, not pick a side")
    }

    @Test
    fun `agreement resolves to the reported variant's image`() {
        val resolved =
            resolveNrfEraseImage(
                mapped = SoftDeviceVariant.S140_7_3_0,
                reportedFromDrive = SoftDeviceVariant.S140_7_3_0,
            )

        assertTrue(resolved is EraseImageResolution.Resolved)
        assertEquals("nrf_erase_sd7_3.uf2", resolved.asset.fileName)
        assertEquals(APP_START_S140_7_3_0, resolved.asset.expectedFirstTargetAddress)
    }

    @Test
    fun `an old bootloader with no softdevice line falls back to the bundled map`() {
        val resolved = resolveNrfEraseImage(mapped = SoftDeviceVariant.S140_6_1_1, reportedFromDrive = null)

        assertTrue(resolved is EraseImageResolution.Resolved)
        assertEquals("nrf_erase2.uf2", resolved.asset.fileName)
    }

    @Test
    fun `a drive report rescues an unmapped model`() {
        // THINKNODE_M8 has no firmware variant on master and so no map row; the drive can still answer.
        val resolved = resolveNrfEraseImage(mapped = null, reportedFromDrive = SoftDeviceVariant.S140_6_1_1)

        assertTrue(resolved is EraseImageResolution.Resolved)
        assertEquals(SoftDeviceVariant.S140_6_1_1, resolved.variant)
    }

    @Test
    fun `neither source resolving means unresolved`() {
        assertEquals(EraseImageResolution.Unresolved, resolveNrfEraseImage(mapped = null, reportedFromDrive = null))
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

        assertEquals(APP_START_S140_7_3_0, uf2FirstTargetAddress(block))
    }

    @Test
    fun `non-uf2 payloads yield no target address`() {
        assertNull(uf2FirstTargetAddress(ByteArray(UF2_BLOCK_BYTES)), "Zeroed bytes carry no UF2 magic")
        assertNull(uf2FirstTargetAddress(ByteArray(16)), "A short payload cannot hold a UF2 block")
    }
}
