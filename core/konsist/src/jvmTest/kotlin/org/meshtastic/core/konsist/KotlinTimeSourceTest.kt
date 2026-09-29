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
 * `Instant` and `Clock` come from `kotlin.time`. The kotlinx-datetime compat artifact the build resolves still ships
 * the deprecated `kotlinx.datetime` copies, so they keep compiling and only this rule stops them spreading. Reads file
 * text, so a fully qualified use counts as well as an import.
 */
class KotlinTimeSourceTest {

    // Assembled so this file does not match its own rule.
    private val bannedNames = listOf("Instant", "Clock").map { listOf("kotlinx", "datetime", it).joinToString(".") }

    private val banned = Regex(bannedNames.joinToString("|") { Regex.escape(it) + "\\b" })

    private val sourceFiles = Konsist.scopeFromProject().files.filterNot { it.isNestedAgentWorktree() }

    @Test
    fun `the scan actually reaches project sources`() {
        val paths = sourceFiles.map { it.scanPath }

        assertTrue(paths.isNotEmpty(), emptyScanMessage("project-wide scan"))
        assertTrue(
            paths.any { it.endsWith("/core/model/src/commonMain/kotlin/org/meshtastic/core/model/Message.kt") },
            "expected core/model sources in scope; got ${paths.size} files, e.g. ${paths.take(3)}",
        )
    }

    @Test
    fun `no source uses the kotlinx datetime Instant or Clock`() {
        val offenders =
            sourceFiles.flatMap { file ->
                file.text.lines().withIndex().mapNotNull { (index, line) ->
                    if (banned.containsMatchIn(line)) "${file.scanPath}:${index + 1}: ${line.trim()}" else null
                }
            }

        assertTrue(
            offenders.isEmpty(),
            "Use kotlin.time.Instant and kotlin.time.Clock instead of $bannedNames:\n" + offenders.joinToString("\n"),
        )
    }
}
