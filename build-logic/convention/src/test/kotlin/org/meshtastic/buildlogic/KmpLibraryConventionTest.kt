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

/** `meshtastic.kmp.library` through TestKit, with the repo's real version catalog. */
class KmpLibraryConventionTest {
    @Test
    fun `registers jvm and android targets and no device ios target`(@TempDir projectDir: File) {
        val targets = fixture(projectDir).values().getValue("targets").split(',')

        assertTrue("jvm" in targets && "android" in targets, "targets=$targets")
        assertTrue(targets.none { it == "iosArm64" }, "targets=$targets")
    }

    @Test
    fun `pins the kotlin and java toolchain to jdk 25`(@TempDir projectDir: File) {
        val values = fixture(projectDir).values()

        assertEquals("25", values["toolchain"])
    }

    @Test
    fun `android target takes minSdk from config properties and has android resources off`(@TempDir projectDir: File) {
        val values = fixture(projectDir).values()

        assertEquals("26", values["minSdk"])
        assertEquals("false", values["androidResources"])
    }

    @Test
    fun `warnings are errors only under the warningsAsErrors property`(@TempDir projectDir: File) {
        val fixture = fixture(projectDir)

        assertEquals("false", fixture.values()["warningsAsErrors"])
        assertEquals("true", fixture.values("-PwarningsAsErrors=true")["warningsAsErrors"])
    }

    private fun fixture(projectDir: File) = ConventionFixture(
        projectDir,
        """
            import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
            import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

            plugins { id("meshtastic.kmp.library") }

            tasks.register("printFixture") {
                doLast {
                    val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
                    println("fixture:targets=" + kotlin.targets.names.filter { it != "metadata" }.sorted().joinToString(","))
                    println("fixture:toolchain=" + project.extensions.getByType(JavaPluginExtension::class.java).toolchain.languageVersion.get().asInt())
                    val android = kotlin.targets.getByName("android") as com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
                    println("fixture:minSdk=${'$'}{android.minSdk}")
                    println("fixture:androidResources=${'$'}{android.androidResources.enable}")
                    val compile = project.tasks.withType(KotlinCompilationTask::class.java).map { it.compilerOptions.allWarningsAsErrors.get() }
                    println("fixture:warningsAsErrors=" + compile.distinct().joinToString(","))
                }
            }
            """
            .trimIndent(),
    )
}
