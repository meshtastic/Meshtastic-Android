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
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * An abstract class's `@Test` functions run only through a concrete subclass, and with none the test task still reports
 * green. Every abstract class in a test source set that declares a `@Test` must reach a concrete subclass.
 */
class AbstractTestBaseTest {

    private val testSourceSet = Regex("/src/[A-Za-z]*[Tt]est/")

    private val testClasses =
        Konsist.scopeFromProject()
            .files
            .filterNot { it.isNestedAgentWorktree() }
            .filter { testSourceSet.containsMatchIn(it.scanPath) }
            .flatMap { it.classes() }

    private val subclasses: Map<String, List<KoClassDeclaration>> =
        testClasses.flatMap { c -> c.parents().map { it.name to c } }.groupBy({ it.first }, { it.second })

    private fun reachesConcrete(name: String, seen: Set<String> = emptySet()): Boolean =
        subclasses[name].orEmpty().any { c ->
            KoModifier.ABSTRACT !in c.modifiers || (c.name !in seen && reachesConcrete(c.name, seen + c.name))
        }

    @Test
    fun `the scan reaches commonTest classes`() {
        assertTrue(
            testClasses.any { "/src/commonTest/" in it.containingFile.scanPath },
            emptyScanMessage("test source scan"),
        )
    }

    @Test
    fun `every abstract test base has a concrete subclass`() {
        val dormant =
            testClasses
                .filter { KoModifier.ABSTRACT in it.modifiers }
                .filter { base -> base.functions().any { f -> f.annotations.any { it.name == "Test" } } }
                .filterNot { reachesConcrete(it.name) }
                .map { "${it.fullyQualifiedName ?: it.name} (${it.containingFile.scanPath})" }

        assertTrue(
            dormant.isEmpty(),
            "abstract test bases with no concrete subclass run nothing:\n${dormant.joinToString("\n")}",
        )
    }
}
