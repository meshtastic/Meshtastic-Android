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

import com.lemonappdev.konsist.api.KoModifier
import com.lemonappdev.konsist.api.Konsist
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Expect classes, objects and interfaces are Beta and need `-Xexpect-actual-classes`; a platform seam is otherwise an
 * interface bound through Koin or a CompositionLocal, or an `expect fun`. The allowlist is the current set, pinned
 * exactly: a new entry needs a reason neither of those serves, and a removed one comes off the list.
 */
class ExpectDeclarationTest {

    private val allowed =
        setOf(
            "org.meshtastic.core.common.util.DateFormatter",
            "org.meshtastic.core.database.MeshtasticDatabaseConstructor",
            "org.meshtastic.core.takserver.AtakFileWriter",
            "org.meshtastic.core.takserver.ZipArchiver",
        )

    private val sourceFiles = Konsist.scopeFromProject().files.filterNot { it.isNestedAgentWorktree() }

    @Test
    fun `the scan actually reaches commonMain sources`() {
        val paths = sourceFiles.map { it.scanPath }.filter { "/src/commonMain/" in it }

        assertTrue(paths.isNotEmpty(), emptyScanMessage("commonMain scan"))
        assertTrue(
            paths.any {
                it.endsWith("/core/common/src/commonMain/kotlin/org/meshtastic/core/common/util/DateFormatter.kt")
            },
            "expected core/common commonMain sources in scope; got ${paths.size} files, e.g. ${paths.take(3)}",
        )
    }

    @Test
    fun `expect classes and objects are exactly the allowlisted set`() {
        // KoObjectDeclaration has no hasExpectModifier in this Konsist version, so all three read the modifier list.
        val declared =
            sourceFiles
                .flatMap { file ->
                    file
                        .classes()
                        .filter { KoModifier.EXPECT in it.modifiers }
                        .map { it.fullyQualifiedName ?: it.name } +
                        file
                            .interfaces()
                            .filter { KoModifier.EXPECT in it.modifiers }
                            .map { it.fullyQualifiedName ?: it.name } +
                        file
                            .objects()
                            .filter { KoModifier.EXPECT in it.modifiers }
                            .map { it.fullyQualifiedName ?: it.name }
                }
                .toSet()

        assertEquals(
            allowed,
            declared,
            "A new expect class or object needs a reason an interface or an expect fun cannot serve; add it to the " +
                "allowlist only with one. A removed one comes off the allowlist too.",
        )
    }
}
