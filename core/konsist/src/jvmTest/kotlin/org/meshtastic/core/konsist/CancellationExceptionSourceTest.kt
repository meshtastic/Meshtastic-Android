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
 * Cancellation is caught and rethrown as `kotlinx.coroutines.CancellationException`, the name the constitution's
 * operational standards require. The stdlib `kotlin.coroutines.cancellation` spelling is banned whether imported or
 * written out in full, so the rule reads file text rather than imports.
 */
class CancellationExceptionSourceTest {

    // Assembled so this file does not match its own rule.
    private val bannedName = listOf("kotlin", "coroutines", "cancellation", "CancellationException").joinToString(".")

    private val sourceFiles = Konsist.scopeFromProject().files.filterNot { it.isNestedAgentWorktree() }

    @Test
    fun `the scan actually reaches project sources`() {
        val paths = sourceFiles.map { it.scanPath }

        assertTrue(paths.isNotEmpty(), emptyScanMessage("project-wide scan"))
        assertTrue(
            paths.any {
                it.endsWith("/core/common/src/commonMain/kotlin/org/meshtastic/core/common/util/Exceptions.kt")
            },
            "expected core/common sources in scope; got ${paths.size} files, e.g. ${paths.take(3)}",
        )
    }

    @Test
    fun `no source names the stdlib CancellationException`() {
        val offenders = sourceFiles.flatMap { file ->
            file.text.lines().withIndex().mapNotNull { (index, line) ->
                if (bannedName in line) "${file.scanPath}:${index + 1}: ${line.trim()}" else null
            }
        }

        assertTrue(
            offenders.isEmpty(),
            "Use kotlinx.coroutines.CancellationException instead of $bannedName:\n" + offenders.joinToString("\n"),
        )
    }
}
