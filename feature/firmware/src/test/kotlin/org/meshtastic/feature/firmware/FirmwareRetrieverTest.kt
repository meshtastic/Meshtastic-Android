/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.meshtastic.core.database.entity.FirmwareRelease
import org.meshtastic.core.model.DeviceHardware
import java.io.File

class FirmwareRetrieverTest {

    private val fileHandler: FirmwareFileHandler = mockk()
    private val retriever = FirmwareRetriever(fileHandler)

    @Test
    fun `retrieveEsp32Firmware uses mt-arch-ota bin when Unified OTA is supported and no screen`() = runTest {
        val release = FirmwareRelease(id = "v2.5.0", zipUrl = "https://example.com/esp32.zip")
        val hardware =
            DeviceHardware(
                hwModelSlug = "HELTEC_V3",
                platformioTarget = "heltec-v3",
                architecture = "esp32-s3",
                supportsUnifiedOta = true,
                hasMui = false,
                hasInkHud = false,
            )
        val expectedFile = File("mt-esp32s3-ota.bin")

        coEvery { fileHandler.checkUrlExists(any()) } returns true
        coEvery { fileHandler.downloadFile(any(), any(), any()) } returns expectedFile

        val result = retriever.retrieveEsp32Firmware(release, hardware) {}

        assertEquals(expectedFile, result)
        coVerify {
            fileHandler.checkUrlExists(
                "https://raw.githubusercontent.com/meshtastic/meshtastic.github.io/master/firmware-2.5.0/mt-esp32s3-ota.bin",
            )
        }
    }

    @Test
    fun `retrieveEsp32Firmware skips mt-arch-ota bin for devices with MUI`() = runTest {
        val release = FirmwareRelease(id = "v2.5.0", zipUrl = "https://example.com/esp32.zip")
        val hardware =
            DeviceHardware(
                hwModelSlug = "T_DECK",
                platformioTarget = "tdeck-tft",
                architecture = "esp32-s3",
                supportsUnifiedOta = true,
                hasMui = true,
            )
        val expectedFile = File("firmware-tdeck-tft-2.5.0.bin")

        coEvery { fileHandler.checkUrlExists(any()) } returns true
        coEvery { fileHandler.downloadFile(any(), any(), any()) } returns expectedFile

        val result = retriever.retrieveEsp32Firmware(release, hardware) {}

        assertEquals(expectedFile, result)
        coVerify(exactly = 0) { fileHandler.checkUrlExists(match { it.contains("mt-esp32s3-ota.bin") }) }
        coVerify {
            fileHandler.checkUrlExists(
                "https://raw.githubusercontent.com/meshtastic/meshtastic.github.io/master/firmware-2.5.0/firmware-tdeck-tft-2.5.0.bin",
            )
        }
    }

    @Test
    fun `retrieveEsp32Firmware falls back to board-specific bin when mt-arch-ota bin is missing`() = runTest {
        val release = FirmwareRelease(id = "v2.5.0", zipUrl = "https://example.com/esp32.zip")
        val hardware =
            DeviceHardware(
                hwModelSlug = "HELTEC_V3",
                platformioTarget = "heltec-v3",
                architecture = "esp32-s3",
                supportsUnifiedOta = true,
                hasMui = false,
            )
        val expectedFile = File("firmware-heltec-v3-2.5.0.bin")

        // Generic fast OTA check fails
        coEvery { fileHandler.checkUrlExists(match { it.contains("mt-esp32s3-ota.bin") }) } returns false
        // ZIP download fails too for the OTA attempt to reach second retrieve call
        coEvery { fileHandler.downloadFile(any(), "firmware_release.zip", any()) } returns null

        // Board-specific check succeeds
        coEvery { fileHandler.checkUrlExists(match { it.contains("firmware-heltec-v3") }) } returns true
        coEvery { fileHandler.downloadFile(any(), "firmware-heltec-v3-2.5.0.bin", any()) } returns expectedFile
        coEvery { fileHandler.extractFirmware(any<File>(), any(), any(), any()) } returns null

        val result = retriever.retrieveEsp32Firmware(release, hardware) {}

        assertEquals(expectedFile, result)
        coVerify {
            fileHandler.checkUrlExists(
                "https://raw.githubusercontent.com/meshtastic/meshtastic.github.io/master/firmware-2.5.0/mt-esp32s3-ota.bin",
            )
            fileHandler.checkUrlExists(
                "https://raw.githubusercontent.com/meshtastic/meshtastic.github.io/master/firmware-2.5.0/firmware-heltec-v3-2.5.0.bin",
            )
        }
    }

    @Test
    fun `retrieveEsp32Firmware uses legacy filename for devices without Unified OTA`() = runTest {
        val release = FirmwareRelease(id = "v2.5.0", zipUrl = "https://example.com/esp32.zip")
        val hardware =
            DeviceHardware(
                hwModelSlug = "TLORA_V2",
                platformioTarget = "tlora-v2",
                architecture = "esp32",
                supportsUnifiedOta = false,
            )
        val expectedFile = File("firmware-tlora-v2-2.5.0.bin")

        coEvery { fileHandler.checkUrlExists(any()) } returns true
        coEvery { fileHandler.downloadFile(any(), any(), any()) } returns expectedFile

        val result = retriever.retrieveEsp32Firmware(release, hardware) {}

        assertEquals(expectedFile, result)
        coVerify {
            fileHandler.checkUrlExists(
                "https://raw.githubusercontent.com/meshtastic/meshtastic.github.io/master/firmware-2.5.0/firmware-tlora-v2-2.5.0.bin",
            )
        }
        // Verify we DID NOT check for mt-esp32-ota.bin
        coVerify(exactly = 0) { fileHandler.checkUrlExists(match { it.contains("mt-esp32-ota.bin") }) }
    }

    @Test
    fun `retrieveOtaFirmware uses correct zip extension for NRF52`() = runTest {
        val release = FirmwareRelease(id = "v2.5.0", zipUrl = "https://example.com/nrf52.zip")
        val hardware =
            DeviceHardware(
                hwModelSlug = "RAK4631",
                platformioTarget = "rak4631",
                architecture = "nrf52840",
                supportsUnifiedOta = false, // OTA via DFU zip
            )
        val expectedFile = File("firmware-rak4631-2.5.0-ota.zip")

        coEvery { fileHandler.checkUrlExists(any()) } returns true
        coEvery { fileHandler.downloadFile(any(), any(), any()) } returns expectedFile

        val result = retriever.retrieveOtaFirmware(release, hardware) {}

        assertEquals(expectedFile, result)
        coVerify {
            fileHandler.checkUrlExists(
                "https://raw.githubusercontent.com/meshtastic/meshtastic.github.io/master/firmware-2.5.0/firmware-rak4631-2.5.0-ota.zip",
            )
        }
    }

    @Test
    fun `retrieveOtaFirmware uses platformioTarget for NRF52 variant`() = runTest {
        val release = FirmwareRelease(id = "v2.5.0", zipUrl = "https://example.com/nrf52.zip")
        val hardware =
            DeviceHardware(
                hwModelSlug = "RAK4631",
                platformioTarget = "rak4631_nomadstar_meteor_pro",
                architecture = "nrf52840",
            )
        val expectedFile = File("firmware-rak4631_nomadstar_meteor_pro-2.5.0-ota.zip")

        coEvery { fileHandler.checkUrlExists(any()) } returns true
        coEvery { fileHandler.downloadFile(any(), any(), any()) } returns expectedFile

        val result = retriever.retrieveOtaFirmware(release, hardware) {}

        assertEquals(expectedFile, result)
        coVerify {
            fileHandler.checkUrlExists(
                "https://raw.githubusercontent.com/meshtastic/meshtastic.github.io/master/firmware-2.5.0/firmware-rak4631_nomadstar_meteor_pro-2.5.0-ota.zip",
            )
        }
    }

    @Test
    fun `retrieveOtaFirmware uses correct filename for STM32`() = runTest {
        val release = FirmwareRelease(id = "v2.5.0", zipUrl = "https://example.com/stm32.zip")
        val hardware =
            DeviceHardware(hwModelSlug = "ST_GENERIC", platformioTarget = "stm32-generic", architecture = "stm32")
        val expectedFile = File("firmware-stm32-generic-2.5.0-ota.zip")

        coEvery { fileHandler.checkUrlExists(any()) } returns true
        coEvery { fileHandler.downloadFile(any(), any(), any()) } returns expectedFile

        val result = retriever.retrieveOtaFirmware(release, hardware) {}

        assertEquals(expectedFile, result)
        coVerify {
            fileHandler.checkUrlExists(
                "https://raw.githubusercontent.com/meshtastic/meshtastic.github.io/master/firmware-2.5.0/firmware-stm32-generic-2.5.0-ota.zip",
            )
        }
    }

    @Test
    fun `retrieveUsbFirmware uses correct uf2 extension for RP2040`() = runTest {
        val release = FirmwareRelease(id = "v2.5.0", zipUrl = "https://example.com/rp2040.zip")
        val hardware = DeviceHardware(hwModelSlug = "RPI_PICO", platformioTarget = "pico", architecture = "rp2040")
        val expectedFile = File("firmware-pico-2.5.0.uf2")

        coEvery { fileHandler.checkUrlExists(any()) } returns true
        coEvery { fileHandler.downloadFile(any(), any(), any()) } returns expectedFile

        val result = retriever.retrieveUsbFirmware(release, hardware) {}

        assertEquals(expectedFile, result)
        coVerify {
            fileHandler.checkUrlExists(
                "https://raw.githubusercontent.com/meshtastic/meshtastic.github.io/master/" +
                    "firmware-2.5.0/firmware-pico-2.5.0.uf2",
            )
        }
    }

    @Test
    fun `retrieveUsbFirmware uses correct uf2 extension for NRF52`() = runTest {
        val release = FirmwareRelease(id = "v2.5.0", zipUrl = "https://example.com/nrf52.zip")
        val hardware = DeviceHardware(hwModelSlug = "T_ECHO", platformioTarget = "t-echo", architecture = "nrf52840")
        val expectedFile = File("firmware-t-echo-2.5.0.uf2")

        coEvery { fileHandler.checkUrlExists(any()) } returns true
        coEvery { fileHandler.downloadFile(any(), any(), any()) } returns expectedFile

        val result = retriever.retrieveUsbFirmware(release, hardware) {}

        assertEquals(expectedFile, result)
        coVerify {
            fileHandler.checkUrlExists(
                "https://raw.githubusercontent.com/meshtastic/meshtastic.github.io/master/firmware-2.5.0/firmware-t-echo-2.5.0.uf2",
            )
        }
    }
}
