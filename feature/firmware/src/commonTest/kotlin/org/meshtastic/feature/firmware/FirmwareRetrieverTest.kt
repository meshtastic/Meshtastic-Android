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
@file:Suppress("MagicNumber")

package org.meshtastic.feature.firmware

import kotlinx.coroutines.test.runTest
import org.meshtastic.core.common.util.CommonUri
import org.meshtastic.core.database.entity.FirmwareRelease
import org.meshtastic.core.model.DeviceHardware
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for [FirmwareRetriever] covering the manifest-first ESP32 firmware resolution strategy and fallback heuristics.
 * Uses [FakeFirmwareFileHandler] instead of MockK for KMP compatibility.
 */
class FirmwareRetrieverTest {

    private companion object {
        const val BASE_URL = "https://raw.githubusercontent.com/meshtastic/meshtastic.github.io/master"

        val TEST_RELEASE = FirmwareRelease(id = "v2.7.17", zipUrl = "https://example.com/esp32-s3.zip")

        val TEST_HARDWARE =
            DeviceHardware(hwModelSlug = "HELTEC_V3", platformioTarget = "heltec-v3", architecture = "esp32-s3")

        /** A valid .mt.json manifest with an app0 entry. */
        val MANIFEST_JSON =
            """
            {
                "files": [
                    {
                        "name": "firmware-heltec-v3-2.7.17.bin",
                        "md5": "abc123",
                        "bytes": 2097152,
                        "part_name": "app0"
                    },
                    {
                        "name": "firmware-heltec-v3-2.7.17.factory.bin",
                        "md5": "def456",
                        "bytes": 4194304,
                        "part_name": "factory"
                    }
                ]
            }
            """
                .trimIndent()
    }

    // -----------------------------------------------------------------------
    // ESP32 manifest-first resolution
    // -----------------------------------------------------------------------

    @Test
    fun `retrieveEsp32Firmware uses manifest when available`() = runTest {
        val handler = FakeFirmwareFileHandler()
        val retriever = FirmwareRetriever(handler)

        // Manifest is available
        handler.textResponses["$BASE_URL/firmware-2.7.17/firmware-heltec-v3-2.7.17.mt.json"] = MANIFEST_JSON

        // Direct download of the manifest-resolved filename succeeds
        handler.existingUrls.add("$BASE_URL/firmware-2.7.17/firmware-heltec-v3-2.7.17.bin")

        val result = retriever.retrieveEsp32Firmware(TEST_RELEASE, TEST_HARDWARE) {}

        assertNotNull(result, "Should resolve firmware via manifest")
        assertEquals("firmware-heltec-v3-2.7.17.bin", result.fileName)
    }

    @Test
    fun `retrieveEsp32Firmware falls back to current naming when manifest unavailable`() = runTest {
        val handler = FakeFirmwareFileHandler()
        val retriever = FirmwareRetriever(handler)

        // No manifest
        // Current naming direct download succeeds
        handler.existingUrls.add("$BASE_URL/firmware-2.7.17/firmware-heltec-v3-2.7.17.bin")

        val result = retriever.retrieveEsp32Firmware(TEST_RELEASE, TEST_HARDWARE) {}

        assertNotNull(result, "Should resolve firmware via current naming fallback")
        assertEquals("firmware-heltec-v3-2.7.17.bin", result.fileName)
    }

    @Test
    fun `retrieveEsp32Firmware falls back to legacy naming when current naming fails`() = runTest {
        val handler = FakeFirmwareFileHandler()
        val retriever = FirmwareRetriever(handler)

        // No manifest, no current naming
        // Legacy naming succeeds
        handler.existingUrls.add("$BASE_URL/firmware-2.7.17/firmware-heltec-v3-2.7.17-update.bin")

        val result = retriever.retrieveEsp32Firmware(TEST_RELEASE, TEST_HARDWARE) {}

        assertNotNull(result, "Should resolve firmware via legacy naming fallback")
        assertEquals("firmware-heltec-v3-2.7.17-update.bin", result.fileName)
    }

    @Test
    fun `retrieveEsp32Firmware falls back to zip extraction when all direct downloads fail`() = runTest {
        val handler = FakeFirmwareFileHandler()
        val retriever = FirmwareRetriever(handler)

        // No manifest, no direct downloads succeed
        // Zip download succeeds and extraction finds a matching file
        handler.zipDownloadResult =
            FirmwareArtifact(
                uri = CommonUri.parse("file:///tmp/firmware_release.zip"),
                fileName = "firmware_release.zip",
                isTemporary = true,
            )
        handler.zipExtractionResult =
            FirmwareArtifact(
                uri = CommonUri.parse("file:///tmp/firmware-heltec-v3-2.7.17.bin"),
                fileName = "firmware-heltec-v3-2.7.17.bin",
                isTemporary = true,
            )

        val result = retriever.retrieveEsp32Firmware(TEST_RELEASE, TEST_HARDWARE) {}

        assertNotNull(result, "Should resolve firmware via zip fallback")
        assertTrue(
            handler.downloadedUrls.any { it.contains("firmware_release.zip") || it.contains(".zip") },
            "Should have attempted zip download",
        )
    }

    @Test
    fun `retrieveEsp32Firmware returns null when all strategies fail`() = runTest {
        val handler = FakeFirmwareFileHandler()
        val retriever = FirmwareRetriever(handler)

        // Everything fails — no manifest, no direct downloads, no zip
        val result = retriever.retrieveEsp32Firmware(TEST_RELEASE, TEST_HARDWARE) {}

        assertNull(result, "Should return null when all strategies fail")
    }

    @Test
    fun `retrieveEsp32Firmware skips manifest when JSON is malformed`() = runTest {
        val handler = FakeFirmwareFileHandler()
        val retriever = FirmwareRetriever(handler)

        // Malformed manifest
        handler.textResponses["$BASE_URL/firmware-2.7.17/firmware-heltec-v3-2.7.17.mt.json"] = "{ not valid json }"

        // Current naming succeeds
        handler.existingUrls.add("$BASE_URL/firmware-2.7.17/firmware-heltec-v3-2.7.17.bin")

        val result = retriever.retrieveEsp32Firmware(TEST_RELEASE, TEST_HARDWARE) {}

        assertNotNull(result, "Should fall through to current naming when manifest is malformed")
        assertEquals("firmware-heltec-v3-2.7.17.bin", result.fileName)
    }

    @Test
    fun `retrieveEsp32Firmware skips manifest when no app0 entry`() = runTest {
        val handler = FakeFirmwareFileHandler()
        val retriever = FirmwareRetriever(handler)

        // Manifest with no app0 entry
        handler.textResponses["$BASE_URL/firmware-2.7.17/firmware-heltec-v3-2.7.17.mt.json"] =
            """{"files": [{"name": "bootloader.bin", "md5": "abc", "bytes": 1024, "part_name": "bootloader"}]}"""

        // Current naming succeeds
        handler.existingUrls.add("$BASE_URL/firmware-2.7.17/firmware-heltec-v3-2.7.17.bin")

        val result = retriever.retrieveEsp32Firmware(TEST_RELEASE, TEST_HARDWARE) {}

        assertNotNull(result, "Should fall through when manifest has no app0 entry")
        assertEquals("firmware-heltec-v3-2.7.17.bin", result.fileName)
    }

    @Test
    fun `retrieveEsp32Firmware strips v prefix from version for URLs`() = runTest {
        val handler = FakeFirmwareFileHandler()
        val retriever = FirmwareRetriever(handler)

        handler.existingUrls.add("$BASE_URL/firmware-2.7.17/firmware-heltec-v3-2.7.17.bin")

        retriever.retrieveEsp32Firmware(TEST_RELEASE, TEST_HARDWARE) {}

        // The manifest URL should use "2.7.17" not "v2.7.17"
        val manifestFetchUrl = handler.fetchedTextUrls.firstOrNull()
        if (manifestFetchUrl != null) {
            assertTrue("v2.7.17" !in manifestFetchUrl, "Manifest URL should not contain 'v' prefix: $manifestFetchUrl")
        }

        // checkUrlExists calls should use bare version
        handler.checkedUrls.forEach { url ->
            assertTrue("firmware-v2.7.17" !in url, "URL should not contain 'v' prefix in firmware path: $url")
        }
    }

    @Test
    fun `retrieveEsp32Firmware uses platformioTarget over hwModelSlug`() = runTest {
        val handler = FakeFirmwareFileHandler()
        val retriever = FirmwareRetriever(handler)

        handler.existingUrls.add("$BASE_URL/firmware-2.7.17/firmware-heltec-v3-2.7.17.bin")

        retriever.retrieveEsp32Firmware(TEST_RELEASE, TEST_HARDWARE) {}

        // All URLs should use "heltec-v3" (platformioTarget) not "HELTEC_V3" (hwModelSlug)
        val allUrls = handler.checkedUrls + handler.fetchedTextUrls + handler.downloadedUrls
        allUrls.forEach { url ->
            assertTrue("HELTEC_V3" !in url, "URL should use platformioTarget, not hwModelSlug: $url")
        }
    }

    @Test
    fun `retrieveEsp32Firmware uses hwModelSlug when platformioTarget is empty`() = runTest {
        val handler = FakeFirmwareFileHandler()
        val retriever = FirmwareRetriever(handler)
        val hardware = TEST_HARDWARE.copy(platformioTarget = "", hwModelSlug = "CUSTOM_BOARD")

        handler.existingUrls.add("$BASE_URL/firmware-2.7.17/firmware-CUSTOM_BOARD-2.7.17.bin")

        val result = retriever.retrieveEsp32Firmware(TEST_RELEASE, hardware) {}

        assertNotNull(result, "Should resolve using hwModelSlug fallback")
        assertEquals("firmware-CUSTOM_BOARD-2.7.17.bin", result.fileName)
    }

    // -----------------------------------------------------------------------
    // OTA firmware (nRF52 DFU zip)
    // -----------------------------------------------------------------------

    @Test
    fun `retrieveOtaFirmware constructs correct filename for nRF52`() = runTest {
        val handler = FakeFirmwareFileHandler()
        val retriever = FirmwareRetriever(handler)
        val hardware = DeviceHardware(hwModelSlug = "RAK4631", platformioTarget = "rak4631", architecture = "nrf52840")
        val release = FirmwareRelease(id = "v2.5.0", zipUrl = "https://example.com/nrf52.zip")

        handler.existingUrls.add("$BASE_URL/firmware-2.5.0/firmware-rak4631-2.5.0-ota.zip")

        val result = retriever.retrieveOtaFirmware(release, hardware) {}

        assertNotNull(result, "Should resolve OTA firmware for nRF52")
        assertEquals("firmware-rak4631-2.5.0-ota.zip", result.fileName)
    }

    @Test
    fun `retrieveOtaFirmware uses platformioTarget for variant`() = runTest {
        val handler = FakeFirmwareFileHandler()
        val retriever = FirmwareRetriever(handler)
        val hardware =
            DeviceHardware(
                hwModelSlug = "RAK4631",
                platformioTarget = "rak4631_nomadstar_meteor_pro",
                architecture = "nrf52840",
            )
        val release = FirmwareRelease(id = "v2.5.0", zipUrl = "https://example.com/nrf52.zip")

        handler.existingUrls.add("$BASE_URL/firmware-2.5.0/firmware-rak4631_nomadstar_meteor_pro-2.5.0-ota.zip")

        val result = retriever.retrieveOtaFirmware(release, hardware) {}

        assertNotNull(result, "Should resolve OTA firmware for nRF52 variant")
        assertEquals("firmware-rak4631_nomadstar_meteor_pro-2.5.0-ota.zip", result.fileName)
    }

    // -----------------------------------------------------------------------
    // USB firmware
    // -----------------------------------------------------------------------

    @Test
    fun `retrieveUsbFirmware constructs correct filename for RP2040`() = runTest {
        val handler = FakeFirmwareFileHandler()
        val retriever = FirmwareRetriever(handler)
        val hardware = DeviceHardware(hwModelSlug = "RPI_PICO", platformioTarget = "pico", architecture = "rp2040")
        val release = FirmwareRelease(id = "v2.5.0", zipUrl = "https://example.com/rp2040.zip")

        handler.existingUrls.add("$BASE_URL/firmware-2.5.0/firmware-pico-2.5.0.uf2")

        val result = retriever.retrieveUsbFirmware(release, hardware) {}

        assertNotNull(result, "Should resolve USB firmware for RP2040")
        assertEquals("firmware-pico-2.5.0.uf2", result.fileName)
    }

    // -----------------------------------------------------------------------
    // Test infrastructure
    // -----------------------------------------------------------------------

    /**
     * A fake [FirmwareFileHandler] for testing [FirmwareRetriever] without network or filesystem.
     *
     * Configure behavior by populating:
     * - [existingUrls] — URLs that [checkUrlExists] returns true for
     * - [textResponses] — URL → text body for [fetchText]
     * - [zipDownloadResult] / [zipExtractionResult] — for zip fallback path
     */
    private class FakeFirmwareFileHandler : FirmwareFileHandler {
        /** URLs that [checkUrlExists] will return true for. */
        val existingUrls = mutableSetOf<String>()

        /** URL → text body for [fetchText]. */
        val textResponses = mutableMapOf<String, String>()

        /** Result returned by [downloadFile] when the filename is "firmware_release.zip". */
        var zipDownloadResult: FirmwareArtifact? = null

        /** Result returned by [extractFirmwareFromZip]. */
        var zipExtractionResult: FirmwareArtifact? = null

        // Tracking
        val checkedUrls = mutableListOf<String>()
        val fetchedTextUrls = mutableListOf<String>()
        val downloadedUrls = mutableListOf<String>()

        override fun cleanupAllTemporaryFiles() {}

        override suspend fun checkUrlExists(url: String): Boolean {
            checkedUrls.add(url)
            return url in existingUrls
        }

        override suspend fun fetchText(url: String): String? {
            fetchedTextUrls.add(url)
            return textResponses[url]
        }

        override suspend fun downloadFile(
            url: String,
            fileName: String,
            onProgress: (Float) -> Unit,
        ): FirmwareArtifact? {
            downloadedUrls.add(url)
            onProgress(1f)

            // Zip download path
            if (fileName == "firmware_release.zip") {
                return zipDownloadResult
            }

            // Direct download: only succeed if the URL was registered as existing
            return if (url in existingUrls) {
                FirmwareArtifact(
                    uri = CommonUri.parse("file:///tmp/$fileName"),
                    fileName = fileName,
                    isTemporary = true,
                )
            } else {
                null
            }
        }

        override suspend fun extractFirmware(
            uri: CommonUri,
            hardware: DeviceHardware,
            fileExtension: String,
            preferredFilename: String?,
        ): FirmwareArtifact? = null

        override suspend fun extractFirmwareFromZip(
            zipFile: FirmwareArtifact,
            hardware: DeviceHardware,
            fileExtension: String,
            preferredFilename: String?,
        ): FirmwareArtifact? = zipExtractionResult

        override suspend fun getFileSize(file: FirmwareArtifact): Long = 0L

        override suspend fun readBytes(artifact: FirmwareArtifact): ByteArray = ByteArray(0)

        override suspend fun importFromUri(uri: CommonUri): FirmwareArtifact? = null

        override suspend fun extractZipEntries(artifact: FirmwareArtifact): Map<String, ByteArray> = emptyMap()

        override suspend fun deleteFile(file: FirmwareArtifact) {}

        override suspend fun copyToUri(source: FirmwareArtifact, destinationUri: CommonUri): Long = 0L
    }
}
