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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val json = Json { ignoreUnknownKeys = true }

class FirmwareManifestTest {

    /**
     * Regression for the manifest-never-parses bug: the real published `.mt.json` has `hwModel` as an **integer** (the
     * HardwareModel enum value) plus assorted scalar/array metadata. A previous `hwModel: String` field threw
     * `JsonDecodingException` on every real manifest, silently falling back to filename heuristics. The model now only
     * consumes `files`, so the real shape must parse and resolve `app0`.
     */
    @Test
    fun `real published manifest shape parses and resolves app0`() {
        val raw =
            """
            {
              "version": "2.7.25.104df5f",
              "build_epoch": 1780963200,
              "hwModel": 43,
              "platformioTarget": "heltec-v3",
              "mcu": "esp32s3",
              "repo": "meshtastic/firmware",
              "has_mui": false,
              "files": [
                { "name": "firmware-heltec-v3-2.7.25.104df5f.elf", "md5": "a22e", "bytes": 22268744 },
                { "name": "firmware-heltec-v3-2.7.25.104df5f.bin", "md5": "6a45", "bytes": 2108864, "part_name": "app0" },
                { "name": "mt-esp32s3-ota.bin", "md5": "497d", "bytes": 636544, "part_name": "app1" }
              ],
              "part": [
                { "name": "app0", "type": "app", "subtype": "ota_0", "offset": "0x10000", "size": "0x330000" }
              ]
            }
            """
                .trimIndent()

        val manifest = json.decodeFromString<FirmwareManifest>(raw)
        val otaEntry = manifest.files.firstOrNull { it.partName == "app0" }

        assertEquals(3, manifest.files.size)
        assertEquals("firmware-heltec-v3-2.7.25.104df5f.bin", otaEntry?.name)
        assertEquals(2108864L, otaEntry?.bytes)
    }

    @Test
    fun `find app0 entry for OTA firmware`() {
        val raw =
            """
            {
              "files": [
                { "name": "firmware-t-deck-2.7.17.bin", "part_name": "app0", "md5": "abc", "bytes": 2097152 },
                { "name": "mt-esp32s3-ota.bin", "part_name": "app1", "md5": "def", "bytes": 636928 }
              ]
            }
            """
                .trimIndent()

        val manifest = json.decodeFromString<FirmwareManifest>(raw)
        val otaEntry = manifest.files.firstOrNull { it.partName == "app0" }

        assertEquals("firmware-t-deck-2.7.17.bin", otaEntry?.name)
        assertEquals("abc", otaEntry?.md5)
        assertEquals(2097152L, otaEntry?.bytes)
    }

    @Test
    fun `returns null when no app0 entry exists`() {
        val raw =
            """
            {
              "files": [
                { "name": "mt-esp32s3-ota.bin", "part_name": "app1" },
                { "name": "littlefs.bin", "part_name": "spiffs" }
              ]
            }
            """
                .trimIndent()

        val manifest = json.decodeFromString<FirmwareManifest>(raw)
        val otaEntry = manifest.files.firstOrNull { it.partName == "app0" }

        assertNull(otaEntry)
    }

    @Test
    fun `empty files list is valid`() {
        val raw = """{ "files": [] }"""
        val manifest = json.decodeFromString<FirmwareManifest>(raw)
        assertTrue(manifest.files.isEmpty())
    }

    @Test
    fun `missing files field uses default`() {
        val raw = """{}"""
        val manifest = json.decodeFromString<FirmwareManifest>(raw)
        assertTrue(manifest.files.isEmpty())
    }

    @Test
    fun `unknown keys including scalar metadata are ignored`() {
        val raw =
            """
            {
              "hwModel": 9,
              "unknown_field": "whatever",
              "files": [
                { "name": "firmware.bin", "part_name": "app0", "extra": true }
              ]
            }
            """
                .trimIndent()

        val manifest = json.decodeFromString<FirmwareManifest>(raw)
        assertEquals(1, manifest.files.size)
        assertEquals("firmware.bin", manifest.files[0].name)
    }

    @Test
    fun `file entry defaults for optional fields`() {
        val raw =
            """
            {
              "files": [{ "name": "test.bin" }]
            }
            """
                .trimIndent()

        val manifest = json.decodeFromString<FirmwareManifest>(raw)
        val file = manifest.files[0]
        assertEquals("test.bin", file.name)
        assertEquals("", file.partName)
        assertEquals("", file.md5)
        assertEquals(0L, file.bytes)
    }
}
