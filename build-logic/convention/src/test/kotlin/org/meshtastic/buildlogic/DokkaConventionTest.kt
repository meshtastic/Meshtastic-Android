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
package org.meshtastic.buildlogic

import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** `meshtastic.dokka` source set suppression on a KMP library, through TestKit. */
class DokkaConventionTest {
    @Test
    fun `every kept source set keeps the source sets it depends on`(@TempDir projectDir: File) {
        val values = fixture(projectDir).values()

        assertTrue("jvmMain" in values.getValue("kept").split(','), "values=$values")
        assertEquals("", values["orphans"], "values=$values")
    }

    private fun fixture(projectDir: File) = ConventionFixture(
        projectDir,
        """
            import org.jetbrains.dokka.gradle.DokkaExtension
            import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

            plugins {
                id("meshtastic.kmp.library")
                id("meshtastic.kmp.web")
                id("meshtastic.kmp.jvm.android")
            }

            tasks.register("printFixture") {
                doLast {
                    val kotlinSets = project.extensions.getByType(KotlinMultiplatformExtension::class.java).sourceSets
                    val kept = project.extensions.getByType(DokkaExtension::class.java).dokkaSourceSets
                        .filter { !it.suppress.get() }
                        .map { it.name }
                        .toSet()
                    // A kept source set whose dependsOn parent is suppressed fails Dokka with "no source module".
                    val orphans = kept.flatMap { name ->
                        kotlinSets.findByName(name)?.dependsOn.orEmpty()
                            .map { it.name }
                            .filter { it !in kept }
                            .map { name + "->" + it }
                    }
                    println("fixture:kept=" + kept.sorted().joinToString(","))
                    println("fixture:orphans=" + orphans.sorted().joinToString(","))
                }
            }
            """
            .trimIndent(),
    )
}
