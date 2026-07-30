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
    fun `otafix is offered only for mapped boards`() {
        assertTrue(
            usbMaintenanceGate(nrf(target = "rak4631"), FirmwareUpdateMethod.Usb, hasRelease = true)
                .showBootloaderUpgrade,
        )
        assertFalse(
            usbMaintenanceGate(nrf(target = "heltec-mesh-node-t114"), FirmwareUpdateMethod.Usb, hasRelease = true)
                .showBootloaderUpgrade,
            "An unmapped board hides the action rather than guessing a bootloader built for other hardware",
        )
    }

    @Test
    fun `otafix images are board specific`() {
        val rak = otafixUf2For("rak4631")
        val t1000 = otafixUf2For("tracker-t1000-e")

        assertNotNull(rak)
        assertNotNull(t1000)
        assertTrue(rak.fileName.contains("wiscore_rak4631_board"))
        assertTrue(t1000.fileName.contains("t1000_e"))
        assertTrue(rak.sha256 != t1000.sha256, "Distinct boards must not share a bootloader digest")
    }

    @Test
    fun `otafix rejects boards that merely share the rak4631 build target`() {
        // WISMESH Hub/Tap/Tag, Nomadstar Meteor Pro and RAK3401 all build against the wiscore_rak4631 board, but they
        // are different products. Matching at board level would offer one product's bootloader to the others.
        listOf("rak2560", "rak_wismeshtap", "rak_wismeshtag", "rak4631_nomadstar_meteor_pro", "rak3401-1watt")
            .forEach { target ->
                assertNull(otafixUf2For(target), "$target must not resolve the RAK4631 bootloader")
            }
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
