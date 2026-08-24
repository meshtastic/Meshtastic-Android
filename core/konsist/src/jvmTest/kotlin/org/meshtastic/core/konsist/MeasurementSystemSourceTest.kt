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
 * UI formatting follows the OS locale, never the radio's `DisplayConfig.DisplayUnits` — that field configures the
 * device's own screen and honoring it in-app produced a split-brain where distance obeyed the radio while temperature
 * obeyed the phone (#6840). App code takes units from `MeasurementSystem`/`getSystemMeasurementSystem()` in
 * `core/common` instead.
 *
 * Enforced as an architecture rule because the proto enum is one import away everywhere and a single new call site
 * silently reintroduces the split for every screen it formats.
 */
class MeasurementSystemSourceTest {

    /**
     * The one legitimate reference: the radio settings screen that *configures* the device's own display units. It
     * edits the proto field; it does not format app UI with it.
     */
    private val deviceConfigAllowlist = listOf("feature/settings/", "DisplayConfigItemList.kt")

    // The rule enforcer itself names the forbidden symbol in its strings, so it is excluded from its own scan.
    private fun scannedFiles() = Konsist.scopeFromProject()
        .files
        .filterNot { it.isNestedAgentWorktree() }
        .filterNot { "/core/konsist/" in it.scanPath }

    @Test
    fun `the scan actually reaches the display sources`() {
        val paths = scannedFiles().map { it.scanPath }

        assertTrue(paths.isNotEmpty(), emptyScanMessage("project-wide units scan"))
        assertTrue(
            paths.any { it.endsWith("NodeItem.kt") },
            "expected core/ui sources in scope; got ${paths.size} files, e.g. ${paths.take(3)}",
        )
    }

    @Test
    fun `the device-config allowlist still matches its one legitimate use`() {
        val allowlisted = scannedFiles().filter { file -> deviceConfigAllowlist.all { it in file.scanPath } }

        assertTrue(
            allowlisted.any { "DisplayConfig.DisplayUnits" in it.text },
            "DisplayConfigItemList.kt no longer references DisplayConfig.DisplayUnits — the allowlist is stale, " +
                "update or remove it so this rule keeps verifying something.",
        )
    }

    @Test
    fun `DisplayConfig DisplayUnits never reaches display code`() {
        val offenders =
            scannedFiles()
                .filterNot { file -> deviceConfigAllowlist.all { it in file.scanPath } }
                .filter { "DisplayConfig.DisplayUnits" in it.text }
                .map { it.scanPath }

        assertTrue(
            offenders.isEmpty(),
            "UI units come from the OS locale (MeasurementSystem), never the radio's DisplayConfig.DisplayUnits. " +
                "Offending files:\n" +
                offenders.joinToString("\n"),
        )
    }
}
