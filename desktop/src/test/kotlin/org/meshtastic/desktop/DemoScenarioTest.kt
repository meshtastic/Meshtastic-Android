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
package org.meshtastic.desktop

import kotlin.test.Test
import kotlin.test.assertTrue

/** Validates that the KMP shared module graph runs correctly on JVM without Android. */
class DemoScenarioTest {

    @Test
    fun `renderReport produces non-empty output and completes successfully`() {
        val report = DemoScenario.renderReport()
        assertTrue(report.isNotBlank(), "Report should not be blank")
        assertTrue(report.contains("All checks completed successfully"), "Report should indicate success")
    }

    @Test
    fun `renderReport exercises Base64 round-trip`() {
        val report = DemoScenario.renderReport()
        assertTrue(report.contains("✓ PASS"), "Base64 round-trip should pass")
    }

    @Test
    fun `renderReport exercises NumberFormatter`() {
        val report = DemoScenario.renderReport()
        assertTrue(report.contains("format(3.14159, 2) = 3.14"), "NumberFormatter should format correctly")
    }
}
