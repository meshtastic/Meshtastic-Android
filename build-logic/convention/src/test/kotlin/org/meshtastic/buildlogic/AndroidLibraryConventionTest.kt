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

/** `meshtastic.android.library` through TestKit, with the repo's real version catalog. */
class AndroidLibraryConventionTest {
    @Test
    fun `sdk levels come from config properties`(@TempDir projectDir: File) {
        val values = fixture(projectDir).values()

        assertEquals("37", values["compileSdk"])
        assertEquals("2", values["compileSdkMinor"])
        assertEquals("26", values["minSdk"])
    }

    @Test
    fun `java sources compile at 21 and the toolchain is jdk 25`(@TempDir projectDir: File) {
        val values = fixture(projectDir).values()

        assertEquals("VERSION_21", values["javaSource"])
        assertEquals("VERSION_21", values["javaTarget"])
        assertEquals("25", values["toolchain"])
    }

    @Test
    fun `applies the shared lint settings`(@TempDir projectDir: File) {
        val values = fixture(projectDir).values()

        assertEquals("checkDependencies=true abortOnError=false disable=[GradleDependency]", values["lint"])
    }

    @Test
    fun `composes the android lint detekt spotless dokka and kover conventions`(@TempDir projectDir: File) {
        val applied = fixture(projectDir).values()["applied"].orEmpty().split(',')

        assertEquals(
            listOf(
                "com.android.library",
                "dev.detekt",
                "com.diffplug.spotless",
                "org.jetbrains.dokka",
                "org.jetbrains.kotlinx.kover",
            ),
            applied,
        )
    }

    private fun fixture(projectDir: File) = ConventionFixture(
        projectDir,
        """
            import com.android.build.api.dsl.LibraryExtension

            plugins { id("meshtastic.android.library") }

            extensions.configure<LibraryExtension> { namespace = "fixture" }

            tasks.register("printFixture") {
                doLast {
                    val android = project.extensions.getByType(LibraryExtension::class.java)
                    println("fixture:compileSdk=${'$'}{android.compileSdk}")
                    println("fixture:compileSdkMinor=${'$'}{android.compileSdkMinor}")
                    println("fixture:minSdk=${'$'}{android.defaultConfig.minSdk}")
                    println("fixture:javaSource=${'$'}{android.compileOptions.sourceCompatibility.name}")
                    println("fixture:javaTarget=${'$'}{android.compileOptions.targetCompatibility.name}")
                    println("fixture:toolchain=${'$'}{project.extensions.getByType(JavaPluginExtension::class.java).toolchain.languageVersion.get().asInt()}")
                    val lint = android.lint
                    println("fixture:lint=checkDependencies=${'$'}{lint.checkDependencies} abortOnError=${'$'}{lint.abortOnError} disable=${'$'}{lint.disable.sorted()}")
                    println("fixture:applied=" + listOf("com.android.library", "dev.detekt", "com.diffplug.spotless", "org.jetbrains.dokka", "org.jetbrains.kotlinx.kover").filter { project.pluginManager.hasPlugin(it) }.joinToString(","))
                }
            }
            """
            .trimIndent(),
    )
}
