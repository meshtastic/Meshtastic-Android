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
 * A temperature's *number* is rendered by [org.meshtastic.core.common.util.NumberFormatter] — directly, or through
 * `MetricFormatter.temperature`/`UnitConversions.toTempString` — never by a `%f` conversion in a hand-written template.
 *
 * `%.1f` formats with a fixed decimal point, so a locale that writes `0,0` gets `0.0` and disagrees with every other
 * number on the same screen. The Environment Telemetry log shipped `"%s %.1f°C"` for both ambient and soil temperature
 * and read wrong for exactly those readers.
 *
 * The rule deliberately says nothing about *conversion*, because who converts differs by screen and both answers are
 * legitimate: the node detail card takes raw Celsius and converts at the call site, while this log screen receives
 * values already converted by `MetricsViewModel.filteredEnvironmentMetrics` and must only label them. Requiring
 * `MetricFormatter.temperature` everywhere would double-convert the second kind — a mistake made while writing this
 * very rule, and the reason the pattern below matches only the number format and not the conversion.
 *
 * Sibling to [MeasurementSystemSourceTest]: that rule governs which unit system is chosen, this one governs how the
 * number is written once it is.
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
            "A \"%.1f°C\"/\"%.1f°F\" template writes the number with a fixed decimal point, ignoring the " +
                "locale's separator. Format it with NumberFormatter.format(value, 1) and append the degree symbol, " +
                "or use MetricFormatter.temperature(celsius, isFahrenheit) where the value still needs converting. " +
                "Offending files:\n" +
                offenders.joinToString("\n"),
        )
    }
}
