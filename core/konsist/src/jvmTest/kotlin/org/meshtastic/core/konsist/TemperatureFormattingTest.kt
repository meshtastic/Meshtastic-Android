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
package org.meshtastic.core.konsist

import com.lemonappdev.konsist.api.Konsist
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Temperature is formatted by `MetricFormatter.temperature` or `UnitConversions.toTempString`, never by a hand-written
 * format template.
 *
 * A template like `"%.1f°F"` looks harmless and is not: it picks the *unit symbol* by branching on the Fahrenheit
 * preference while passing the value through untouched, so a Fahrenheit reader is shown the Celsius number under an °F
 * label. It also formats the number with `%f`, which ignores the locale's decimal separator — the thing
 * [org.meshtastic.core.common.util.NumberFormatter] exists to get right. The Environment Telemetry log shipped exactly
 * this for both ambient and soil temperature; both were read as merely a formatting nit until the missing conversion
 * was spotted.
 *
 * Sibling rule to [MeasurementSystemSourceTest]: that one governs *which* unit system is chosen, this one governs
 * whether the chosen system is actually applied to the number.
 */
class TemperatureFormattingTest {

    /** A printf float conversion glued directly to a degree-Celsius/Fahrenheit symbol. */
    private val handRolledTemperatureFormat = Regex("""%[-0-9.]*f°[CF]""")

    // The rule enforcer names the forbidden pattern in its own source, so it is excluded from its own scan.
    private fun scannedFiles() = Konsist.scopeFromProject()
        .files
        .filterNot { it.isNestedAgentWorktree() }
        .filterNot { "/core/konsist/" in it.scanPath }

    @Test
    fun `the scan actually reaches the metric sources`() {
        val paths = scannedFiles().map { it.scanPath }

        assertTrue(paths.isNotEmpty(), emptyScanMessage("project-wide temperature-format scan"))
        assertTrue(
            paths.any { it.endsWith("MetricFormatter.kt") },
            "expected core/common formatting sources in scope; got ${paths.size} files, e.g. ${paths.take(3)}",
        )
    }

    @Test
    fun `temperature is never formatted with a hand-written degree template`() {
        val offenders =
            scannedFiles().filter { handRolledTemperatureFormat.containsMatchIn(it.text) }.map { it.scanPath }

        assertTrue(
            offenders.isEmpty(),
            "A \"%.1f°C\"/\"%.1f°F\" template branches on the unit but not the value, so it labels a Celsius " +
                "reading as Fahrenheit, and it bypasses NumberFormatter's locale-aware decimal separator. Use " +
                "MetricFormatter.temperature(celsius, isFahrenheit) or Float.toTempString(isFahrenheit). " +
                "Offending files:\n" +
                offenders.joinToString("\n"),
        )
    }
}
