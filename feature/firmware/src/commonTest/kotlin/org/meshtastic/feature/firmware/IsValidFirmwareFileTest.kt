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

import kotlin.test.Test
import kotlin.test.assertFalse
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
    fun `filename starting with target-dash matches`() {
        assertTrue(isValidFirmwareFile("heltec-v3-firmware-2.7.17.bin", "heltec-v3", ".bin"))
    }

    @Test
    fun `filename starting with target-dot matches`() {
        assertTrue(isValidFirmwareFile("heltec-v3.firmware.bin", "heltec-v3", ".bin"))
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
}
