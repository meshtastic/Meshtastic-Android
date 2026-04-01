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
package org.meshtastic.feature.firmware.ota.dfu

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DfuZipParserTest {

    @Test
    fun parseValidZipEntries() {
        val manifestJson =
            """
            {
              "manifest": {
                "application": {
                  "bin_file": "app.bin",
                  "dat_file": "app.dat"
                }
              }
            }
            """
                .trimIndent()

        val entries =
            mapOf(
                "manifest.json" to manifestJson.encodeToByteArray(),
                "app.bin" to byteArrayOf(0x01, 0x02, 0x03),
                "app.dat" to byteArrayOf(0x04, 0x05),
            )

        val packageResult = parseDfuZipEntries(entries)

        assertTrue(packageResult.firmware.contentEquals(byteArrayOf(0x01, 0x02, 0x03)))
        assertTrue(packageResult.initPacket.contentEquals(byteArrayOf(0x04, 0x05)))
    }

    @Test
    fun failsWhenManifestIsMissing() {
        val entries = mapOf("app.bin" to byteArrayOf(), "app.dat" to byteArrayOf())

        val ex = assertFailsWith<DfuException.InvalidPackage> { parseDfuZipEntries(entries) }
        assertEquals("manifest.json not found in DFU zip", ex.message)
    }

    @Test
    fun failsWhenManifestIsInvalid() {
        val entries = mapOf("manifest.json" to "not json".encodeToByteArray())

        val ex = assertFailsWith<DfuException.InvalidPackage> { parseDfuZipEntries(entries) }
        assertTrue(ex.message?.startsWith("Failed to parse manifest.json") == true)
    }

    @Test
    fun failsWhenNoEntryFound() {
        val manifestJson =
            """
            {
              "manifest": {}
            }
            """
                .trimIndent()

        val entries = mapOf("manifest.json" to manifestJson.encodeToByteArray())

        val ex = assertFailsWith<DfuException.InvalidPackage> { parseDfuZipEntries(entries) }
        assertEquals("No firmware entry found in manifest.json", ex.message)
    }

    @Test
    fun failsWhenDatFileNotFound() {
        val manifestJson =
            """
            {
              "manifest": {
                "application": {
                  "bin_file": "app.bin",
                  "dat_file": "app.dat"
                }
              }
            }
            """
                .trimIndent()

        val entries = mapOf("manifest.json" to manifestJson.encodeToByteArray(), "app.bin" to byteArrayOf(0x01))

        val ex = assertFailsWith<DfuException.InvalidPackage> { parseDfuZipEntries(entries) }
        assertEquals("Init packet 'app.dat' not found in zip", ex.message)
    }

    @Test
    fun failsWhenBinFileNotFound() {
        val manifestJson =
            """
            {
              "manifest": {
                "application": {
                  "bin_file": "app.bin",
                  "dat_file": "app.dat"
                }
              }
            }
            """
                .trimIndent()

        val entries = mapOf("manifest.json" to manifestJson.encodeToByteArray(), "app.dat" to byteArrayOf(0x01))

        val ex = assertFailsWith<DfuException.InvalidPackage> { parseDfuZipEntries(entries) }
        assertEquals("Firmware 'app.bin' not found in zip", ex.message)
    }
}
