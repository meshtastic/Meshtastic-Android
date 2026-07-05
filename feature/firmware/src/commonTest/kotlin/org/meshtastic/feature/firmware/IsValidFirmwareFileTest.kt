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

import org.meshtastic.core.common.util.CommonUri
import org.meshtastic.core.model.DeviceHardware
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for [isValidFirmwareFile] — the pure function that filters firmware binaries from other artifacts that share
 * the same extension (e.g. `littlefs-*`, `bleota*`, `mt-*`, `*.factory.*`).
 */
class IsValidFirmwareFileTest {

    // ── Positive cases ──────────────────────────────────────────────────────

    @Test
    fun `standard firmware bin matches`() {
        assertTrue(isValidFirmwareFile("firmware-heltec-v3-2.7.17.bin", "heltec-v3", ".bin"))
    }

    @Test
    fun `standard firmware uf2 matches`() {
        assertTrue(isValidFirmwareFile("firmware-pico-2.5.0.uf2", "pico", ".uf2"))
    }

    @Test
    fun `target with underscore separator matches`() {
        assertTrue(isValidFirmwareFile("firmware-rak4631_eink-2.7.17.bin", "rak4631_eink", ".bin"))
    }

    @Test
    fun `filename starting with target and version matches`() {
        assertTrue(isValidFirmwareFile("heltec-v3-2.7.17.bin", "heltec-v3", ".bin"))
    }

    @Test
    fun `filename starting with target and extension matches`() {
        assertTrue(isValidFirmwareFile("heltec-v3.bin", "heltec-v3", ".bin"))
    }

    @Test
    fun `ota zip matches for nrf target`() {
        assertTrue(isValidFirmwareFile("firmware-rak4631-2.5.0-ota.zip", "rak4631", ".zip"))
    }

    // ── Exclusion patterns ──────────────────────────────────────────────────

    @Test
    fun `rejects littlefs prefix`() {
        assertFalse(isValidFirmwareFile("littlefs-heltec-v3-2.7.17.bin", "heltec-v3", ".bin"))
    }

    @Test
    fun `rejects bleota prefix`() {
        assertFalse(isValidFirmwareFile("bleota-heltec-v3-2.7.17.bin", "heltec-v3", ".bin"))
    }

    @Test
    fun `rejects bleota0 prefix`() {
        assertFalse(isValidFirmwareFile("bleota0-heltec-v3-2.7.17.bin", "heltec-v3", ".bin"))
    }

    @Test
    fun `rejects mt- prefix`() {
        assertFalse(isValidFirmwareFile("mt-heltec-v3-2.7.17.bin", "heltec-v3", ".bin"))
    }

    @Test
    fun `rejects factory binary`() {
        assertFalse(isValidFirmwareFile("firmware-heltec-v3-2.7.17.factory.bin", "heltec-v3", ".bin"))
    }

    // ── Wrong extension / target mismatch ───────────────────────────────────

    @Test
    fun `rejects wrong extension`() {
        assertFalse(isValidFirmwareFile("firmware-heltec-v3-2.7.17.bin", "heltec-v3", ".uf2"))
    }

    @Test
    fun `rejects when target not present`() {
        assertFalse(isValidFirmwareFile("firmware-tbeam-2.7.17.bin", "heltec-v3", ".bin"))
    }

    @Test
    fun `rejects target substring without boundary`() {
        // "pico" appears in "pico2w" but "pico" should not match "pico2w" without a boundary char
        assertFalse(isValidFirmwareFile("firmware-pico2w-2.7.17.uf2", "pico", ".uf2"))
    }

    @Test
    fun `rejects target prefix collisions`() {
        assertFalse(isValidFirmwareFile("firmware-tbeam-s3-core-2.8.0.bin", "tbeam", ".bin"))
        assertFalse(isValidFirmwareFile("firmware-t-echo-lite-2.8.0.bin", "t-echo", ".bin"))
        assertFalse(isValidFirmwareFile("firmware-t-deck-pro-2.8.0.bin", "t-deck", ".bin"))
        assertFalse(isValidFirmwareFile("firmware-nano-g1-explorer-2.8.0.bin", "nano-g1", ".bin"))
    }

    @Test
    fun `accepts full colliding target names`() {
        assertTrue(isValidFirmwareFile("firmware-tbeam-s3-core-2.8.0.bin", "tbeam-s3-core", ".bin"))
        assertTrue(isValidFirmwareFile("firmware-t-echo-lite-2.8.0.bin", "t-echo-lite", ".bin"))
        assertTrue(isValidFirmwareFile("firmware-t-deck-pro-2.8.0.bin", "t-deck-pro", ".bin"))
        assertTrue(isValidFirmwareFile("firmware-nano-g1-explorer-2.8.0.bin", "nano-g1-explorer", ".bin"))
    }

    // ── Edge cases ──────────────────────────────────────────────────────────

    @Test
    fun `empty filename returns false`() {
        assertFalse(isValidFirmwareFile("", "heltec-v3", ".bin"))
    }

    @Test
    fun `empty target returns false`() {
        // Empty target makes the regex match anything, but contains("") is always true —
        // the function still requires the extension
        assertFalse(isValidFirmwareFile("firmware.bin", "", ".uf2"))
    }

    @Test
    fun `nrf BLE local firmware accepts target matching ota zip`() {
        val hardware = DeviceHardware(architecture = "nrf52", platformioTarget = "rak4631")

        assertEquals(
            LocalFirmwareFileValidation.Valid,
            validateLocalFirmwareFileName("firmware-rak4631-2.8.0-ota.zip", hardware, FirmwareUpdateMethod.Ble),
        )
    }

    @Test
    fun `nrf BLE local firmware rejects zip without ota suffix`() {
        val hardware = DeviceHardware(architecture = "nrf52", platformioTarget = "rak4631")

        assertInvalidReason(
            LocalFirmwareFileValidationReason.RequiresOtaZip,
            validateLocalFirmwareFileName("firmware-rak4631-2.8.0.zip", hardware, FirmwareUpdateMethod.Ble),
        )
    }

    @Test
    fun `nrf BLE local firmware rejects generic architecture zip`() {
        val hardware = DeviceHardware(architecture = "nrf52", platformioTarget = "rak4631")

        assertInvalidReason(
            LocalFirmwareFileValidationReason.RequiresOtaZip,
            validateLocalFirmwareFileName("firmware-nrf52840-2.8.0.zip", hardware, FirmwareUpdateMethod.Ble),
        )
    }

    @Test
    fun `nrf BLE local firmware rejects wrong target ota zip`() {
        val hardware = DeviceHardware(architecture = "nrf52", platformioTarget = "rak4631")

        assertInvalidReason(
            LocalFirmwareFileValidationReason.TargetMismatch,
            validateLocalFirmwareFileName("firmware-tbeam-2.8.0-ota.zip", hardware, FirmwareUpdateMethod.Ble),
        )
    }

    @Test
    fun `esp32 local firmware rejects ota helper binary`() {
        val hardware = DeviceHardware(architecture = "esp32", platformioTarget = "esp32")

        assertInvalidReason(
            LocalFirmwareFileValidationReason.TargetMismatch,
            validateLocalFirmwareFileName("mt-esp32-ota.bin", hardware, FirmwareUpdateMethod.Wifi),
        )
    }

    @Test
    fun `esp32 local firmware rejects bleota helper binary`() {
        val hardware = DeviceHardware(architecture = "esp32", platformioTarget = "c3")

        assertInvalidReason(
            LocalFirmwareFileValidationReason.TargetMismatch,
            validateLocalFirmwareFileName("bleota-c3.bin", hardware, FirmwareUpdateMethod.Ble),
        )
    }

    @Test
    fun `esp32 local firmware rejects factory binary`() {
        val hardware = DeviceHardware(architecture = "esp32", platformioTarget = "heltec-v3")

        assertInvalidReason(
            LocalFirmwareFileValidationReason.TargetMismatch,
            validateLocalFirmwareFileName("firmware-heltec-v3-2.8.0.factory.bin", hardware, FirmwareUpdateMethod.Wifi),
        )
    }

    @Test
    fun `esp32 local firmware accepts target matching app binary`() {
        val hardware = DeviceHardware(architecture = "esp32", platformioTarget = "heltec-v3")

        assertEquals(
            LocalFirmwareFileValidation.Valid,
            validateLocalFirmwareFileName("firmware-heltec-v3-2.8.0.bin", hardware, FirmwareUpdateMethod.Wifi),
        )
    }

    @Test
    fun `usb local firmware accepts matching uf2 and rejects wrong target`() {
        val hardware = DeviceHardware(architecture = "rp2040", platformioTarget = "pico")

        assertEquals(
            LocalFirmwareFileValidation.Valid,
            validateLocalFirmwareFileName("firmware-pico-2.8.0.uf2", hardware, FirmwareUpdateMethod.Usb),
        )
        assertInvalidReason(
            LocalFirmwareFileValidationReason.TargetMismatch,
            validateLocalFirmwareFileName("firmware-pico2w-2.8.0.uf2", hardware, FirmwareUpdateMethod.Usb),
        )
    }

    @Test
    fun `local firmware payload extension follows update method and architecture`() {
        val nrfHardware = DeviceHardware(architecture = "nrf52", platformioTarget = "rak4631")
        val espHardware = DeviceHardware(architecture = "esp32", platformioTarget = "heltec-v3")
        val usbHardware = DeviceHardware(architecture = "rp2040", platformioTarget = "pico")

        assertEquals(".zip", localFirmwarePayloadExtension(nrfHardware, FirmwareUpdateMethod.Ble))
        assertEquals(".bin", localFirmwarePayloadExtension(espHardware, FirmwareUpdateMethod.Ble))
        assertEquals(".bin", localFirmwarePayloadExtension(espHardware, FirmwareUpdateMethod.Wifi))
        assertEquals(".uf2", localFirmwarePayloadExtension(usbHardware, FirmwareUpdateMethod.Usb))
        assertNull(localFirmwarePayloadExtension(nrfHardware, FirmwareUpdateMethod.Unknown))
    }

    @Test
    fun `pending local firmware validates unchanged confirmation context`() {
        val hardware = DeviceHardware(architecture = "nrf52", platformioTarget = "rak4631")
        val readyState = readyState(hardware = hardware, updateMethod = FirmwareUpdateMethod.Ble)
        val pendingFile =
            pendingFile(
                fileName = "firmware-rak4631-2.8.0-ota.zip",
                platformioTarget = "rak4631",
                updateMethod = FirmwareUpdateMethod.Ble,
            )

        assertEquals(LocalFirmwareFileValidation.Valid, validatePendingLocalFirmwareFile(pendingFile, readyState))
    }

    @Test
    fun `pending local firmware rejects changed target before confirmation`() {
        val hardware = DeviceHardware(architecture = "nrf52", platformioTarget = "tbeam")
        val readyState = readyState(hardware = hardware, updateMethod = FirmwareUpdateMethod.Ble)
        val pendingFile =
            pendingFile(
                fileName = "firmware-rak4631-2.8.0-ota.zip",
                platformioTarget = "rak4631",
                updateMethod = FirmwareUpdateMethod.Ble,
            )

        assertInvalidReason(
            LocalFirmwareFileValidationReason.TargetMismatch,
            validatePendingLocalFirmwareFile(pendingFile, readyState),
        )
    }

    @Test
    fun `pending local firmware rejects changed update method before confirmation`() {
        val hardware = DeviceHardware(architecture = "esp32", platformioTarget = "tbeam")
        val readyState = readyState(hardware = hardware, updateMethod = FirmwareUpdateMethod.Wifi)
        val pendingFile =
            pendingFile(
                fileName = "firmware-tbeam-2.8.0.bin",
                platformioTarget = "tbeam",
                updateMethod = FirmwareUpdateMethod.Ble,
            )

        assertInvalidReason(
            LocalFirmwareFileValidationReason.ConfirmationContextChanged,
            validatePendingLocalFirmwareFile(pendingFile, readyState),
        )
    }

    @Test
    fun `pending local firmware rejects changed address before confirmation`() {
        val hardware = DeviceHardware(architecture = "nrf52", platformioTarget = "rak4631")
        val readyState =
            readyState(hardware = hardware, updateMethod = FirmwareUpdateMethod.Ble, address = "11:22:33:44:55:77")
        val pendingFile =
            pendingFile(
                fileName = "firmware-rak4631-2.8.0-ota.zip",
                platformioTarget = "rak4631",
                updateMethod = FirmwareUpdateMethod.Ble,
                address = "11:22:33:44:55:66",
            )

        assertInvalidReason(
            LocalFirmwareFileValidationReason.ConfirmationContextChanged,
            validatePendingLocalFirmwareFile(pendingFile, readyState),
        )
    }

    @Test
    fun `pending local firmware prioritizes changed context over target mismatch`() {
        val hardware = DeviceHardware(architecture = "nrf52", platformioTarget = "tbeam")
        val readyState =
            readyState(hardware = hardware, updateMethod = FirmwareUpdateMethod.Ble, address = "11:22:33:44:55:77")
        val pendingFile =
            pendingFile(
                fileName = "firmware-rak4631-2.8.0-ota.zip",
                platformioTarget = "rak4631",
                updateMethod = FirmwareUpdateMethod.Ble,
                address = "11:22:33:44:55:66",
            )

        assertInvalidReason(
            LocalFirmwareFileValidationReason.ConfirmationContextChanged,
            validatePendingLocalFirmwareFile(pendingFile, readyState),
        )
    }

    @Test
    fun `preferred local firmware archive filenames follow retriever order`() {
        val esp32Hardware = DeviceHardware(architecture = "esp32", platformioTarget = "tbeam")
        val nrfHardware = DeviceHardware(architecture = "nrf52", platformioTarget = "rak4631")
        val usbHardware = DeviceHardware(architecture = "rp2040", platformioTarget = "pico")

        assertEquals(
            listOf("firmware-tbeam-2.7.15-update.bin", "firmware-tbeam-2.7.15.bin"),
            preferredLocalFirmwareArchiveFilenames(
                "firmware-esp32-2.7.15.zip",
                esp32Hardware,
                FirmwareUpdateMethod.Wifi,
            ),
        )
        assertEquals(
            listOf("firmware-rak4631-2.8.0-ota.zip"),
            preferredLocalFirmwareArchiveFilenames(
                "firmware-nrf52840-2.8.0.zip",
                nrfHardware,
                FirmwareUpdateMethod.Ble,
            ),
        )
        assertEquals(
            listOf("firmware-pico-2.8.0.uf2"),
            preferredLocalFirmwareArchiveFilenames("firmware-rp2040-2.8.0.zip", usbHardware, FirmwareUpdateMethod.Usb),
        )
    }

    @Test
    fun `validate rejects blank target with MissingTarget`() {
        val hardware = DeviceHardware(architecture = "nrf52", platformioTarget = "", hwModelSlug = "")
        assertInvalidReason(
            LocalFirmwareFileValidationReason.MissingTarget,
            validateLocalFirmwareFileName("firmware-rak4631-2.8.0-ota.zip", hardware, FirmwareUpdateMethod.Ble),
        )
    }

    @Test
    fun `validate rejects Unknown update method with UnsupportedUpdateMethod`() {
        val hardware = DeviceHardware(architecture = "nrf52", platformioTarget = "rak4631")
        assertInvalidReason(
            LocalFirmwareFileValidationReason.UnsupportedUpdateMethod,
            validateLocalFirmwareFileName("firmware-rak4631-2.8.0-ota.zip", hardware, FirmwareUpdateMethod.Unknown),
        )
    }

    @Test
    fun `esp32 rejects wrong extension with RequiresBin`() {
        val hardware = DeviceHardware(architecture = "esp32", platformioTarget = "heltec-v3")
        assertInvalidReason(
            LocalFirmwareFileValidationReason.RequiresBin,
            validateLocalFirmwareFileName("firmware-heltec-v3-2.8.0.uf2", hardware, FirmwareUpdateMethod.Wifi),
        )
    }

    @Test
    fun `usb rejects wrong extension with RequiresUf2`() {
        val hardware = DeviceHardware(architecture = "rp2040", platformioTarget = "pico")
        assertInvalidReason(
            LocalFirmwareFileValidationReason.RequiresUf2,
            validateLocalFirmwareFileName("firmware-pico-2.8.0.bin", hardware, FirmwareUpdateMethod.Usb),
        )
    }

    private fun readyState(
        hardware: DeviceHardware,
        updateMethod: FirmwareUpdateMethod,
        address: String = "11:22:33:44:55:66",
    ) = FirmwareUpdateState.Ready(
        release = null,
        deviceHardware = hardware,
        address = address,
        showBootloaderWarning = false,
        updateMethod = updateMethod,
    )

    private fun pendingFile(
        fileName: String,
        platformioTarget: String,
        updateMethod: FirmwareUpdateMethod,
        address: String = "11:22:33:44:55:66",
    ) = PendingLocalFirmwareFile(
        uri = CommonUri.parse("file:///downloads/$fileName"),
        fileName = fileName,
        deviceName = "Test device",
        platformioTarget = platformioTarget,
        updateMethod = updateMethod,
        address = address,
    )

    private fun assertInvalidReason(expected: LocalFirmwareFileValidationReason, actual: LocalFirmwareFileValidation) {
        assertEquals(LocalFirmwareFileValidation.Invalid(expected), actual)
    }
}
