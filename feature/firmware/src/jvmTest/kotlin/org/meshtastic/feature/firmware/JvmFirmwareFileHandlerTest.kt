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

import io.ktor.client.HttpClient
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.common.util.CommonUri
import org.meshtastic.core.model.DeviceHardware
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNull

class JvmFirmwareFileHandlerTest {
    private val client = HttpClient()
    private val handler = JvmFirmwareFileHandler(client)
    private val hardware = DeviceHardware(hwModel = 1, architecture = "esp32", platformioTarget = "heltec-v3")

    @AfterTest fun tearDown() = client.close()

    /** A zip with one more entry than extraction allows, none of them firmware, so nothing is written. */
    private fun overLimitZip(): File {
        val file = Files.createTempFile("firmware-handler-test", ".zip").toFile().apply { deleteOnExit() }
        ZipOutputStream(file.outputStream()).use { zip ->
            repeat(MAX_FIRMWARE_ZIP_ENTRIES + 1) {
                zip.putNextEntry(ZipEntry("entry$it"))
                zip.closeEntry()
            }
        }
        return file
    }

    @Test
    fun `a downloaded zip over the extraction limits yields null`() = runTest {
        val zip = overLimitZip()
        val artifact = FirmwareArtifact(uri = CommonUri.parse(zip.toURI().toString()), fileName = zip.name)

        assertNull(handler.extractFirmwareFromZip(artifact, hardware, ".bin"))
    }

    @Test
    fun `a picked archive over the extraction limits yields null`() = runTest {
        val zip = overLimitZip()

        assertNull(handler.extractFirmware(CommonUri.parse(zip.toURI().toString()), hardware, ".bin"))
    }
}
