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

/** `meshtastic.kmp.web` through TestKit, with the repo's real version catalog. */
class KmpWebConventionTest {
    @Test
    fun `registers no wasmJs target without the web property but still builds nonWeb`(@TempDir projectDir: File) {
        val values = fixture(projectDir).values()

        assertTrue("wasmJs" !in values.getValue("targets").split(','), "values=$values")
        assertEquals("commonMain", values["nonWebMain"])
        assertTrue("nonWebMain" in values.getValue("jvmMain").split(','), "values=$values")
        assertEquals("commonMain", values["nonAndroidMain"])
        assertTrue("nonAndroidMain" in values.getValue("jvmMain").split(','), "values=$values")
        // iosMain reaches it through appleMain and nativeMain.
        values["nativeMain"]?.let { assertTrue("nonAndroidMain" in it.split(','), "values=$values") }
    }

    @Test
    fun `the web property registers wasmJs outside nonWeb`(@TempDir projectDir: File) {
        val values = fixture(projectDir).values("-Pmeshtastic.web=true")

        assertTrue("wasmJs" in values.getValue("targets").split(','), "values=$values")
        assertTrue("nonWebMain" !in values.getValue("wasmJsMain").split(','), "values=$values")
        assertTrue("nonWebMain" in values.getValue("jvmMain").split(','), "values=$values")
        assertTrue("nonAndroidMain" in values.getValue("wasmJsMain").split(','), "values=$values")
        assertTrue("nonAndroidMain" in values.getValue("jvmMain").split(','), "values=$values")
        assertEquals("commonMain", values["nonAndroidMain"])
    }

    @Test
    fun `a browser test run is backstopped at twelve minutes`(@TempDir projectDir: File) {
        val values = fixture(projectDir).values("-Pmeshtastic.web=true")

        assertEquals("PT12M", values["browserTestTimeout"], "values=$values")
    }

    @Test
    fun `nests nativeMain and jvmAndroidMain under nonWebMain`(@TempDir projectDir: File) {
        val values = fixture(projectDir, jvmAndroid = true).values()

        assertTrue("nonWebMain" in values.getValue("jvmAndroidMain").split(','), "values=$values")
        // No iOS target, and so no nativeMain, on a host Kotlin/Native does not run on.
        values["nativeMain"]?.let { assertTrue("nonWebMain" in it.split(','), "values=$values") }
        // jvmAndroidMain holds Android, so it never nests under nonAndroidMain.
        assertTrue("nonAndroidMain" !in values.getValue("jvmAndroidMain").split(','), "values=$values")
        assertTrue("nonAndroidMain" in values.getValue("jvmMain").split(','), "values=$values")
        values["nativeMain"]?.let { assertTrue("nonAndroidMain" in it.split(','), "values=$values") }
    }

    @Test
    fun `the web property fails fast under isolated projects`(@TempDir projectDir: File) {
        val result =
            fixture(projectDir)
                .buildAndFail(
                    "printFixture",
                    "-Pmeshtastic.web=true",
                    "-Dorg.gradle.isolated-projects=true",
                )

        assertTrue("-Dorg.gradle.isolated-projects=false" in result.output, result.output)
    }

    private fun fixture(projectDir: File, jvmAndroid: Boolean = false) = ConventionFixture(
        projectDir,
        """
            import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

            plugins {
                id("meshtastic.kmp.library")
                id("meshtastic.kmp.web")
                ${if (jvmAndroid) "id(\"meshtastic.kmp.jvm.android\")" else ""}
            }

            val kotlinExtension = extensions.getByType(KotlinMultiplatformExtension::class.java)
            // The closure each shared source set depends on, read at configuration time.
            val parents = listOf("nonWebMain", "nonAndroidMain", "jvmMain", "wasmJsMain", "jvmAndroidMain", "nativeMain")
                .mapNotNull { name -> kotlinExtension.sourceSets.findByName(name)?.let { name to it } }
                .associate { (name, set) -> name to set.dependsOn.map { it.name }.sorted().joinToString(",") }
            val targetNames = kotlinExtension.targets.names.filter { it != "metadata" }.sorted().joinToString(",")
            val browserTestTimeout = tasks.findByName("wasmJsBrowserTest")?.timeout?.orNull

            tasks.register("printFixture") {
                doLast {
                    println("fixture:targets=" + targetNames)
                    println("fixture:browserTestTimeout=" + browserTestTimeout)
                    parents.forEach { (name, deps) -> println("fixture:" + name + "=" + deps) }
                }
            }
            """
            .trimIndent(),
    )
}
