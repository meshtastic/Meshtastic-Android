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

/**
 * `meshtastic.android.lint` picks its target by whichever AGP plugin is applied, else falls back to `com.android.lint`.
 */
class AndroidLintConventionTest {
    @Test
    fun `application modules get the shared lint settings`(@TempDir projectDir: File) {
        val values =
            ConventionFixture(
                projectDir,
                lintScript(
                    plugins = listOf("com.android.application"),
                    setup = "android { namespace = \"fixture\"; compileSdk = 36 }",
                    lint =
                    "project.extensions.getByType(com.android.build.api.dsl.ApplicationExtension::class.java).lint",
                ),
            )
                .values()

        assertEquals(EXPECTED, values["lint"])
    }

    @Test
    fun `library modules get the shared lint settings`(@TempDir projectDir: File) {
        val values =
            ConventionFixture(
                projectDir,
                lintScript(
                    plugins = listOf("com.android.library"),
                    setup = "android { namespace = \"fixture\"; compileSdk = 36 }",
                    lint =
                    "project.extensions.getByType(com.android.build.api.dsl.LibraryExtension::class.java).lint",
                ),
            )
                .values()

        assertEquals(EXPECTED, values["lint"])
    }

    @Test
    fun `kotlin multiplatform android library modules get the shared lint settings`(@TempDir projectDir: File) {
        val values =
            ConventionFixture(
                projectDir,
                lintScript(
                    plugins =
                    listOf("org.jetbrains.kotlin.multiplatform", "com.android.kotlin.multiplatform.library"),
                    setup = "kotlin { androidLibrary { namespace = \"fixture\"; compileSdk = 36 } }",
                    lint =
                    "(project.extensions.getByType(org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension::class.java).targets.getByName(\"android\") as com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget).lint",
                ),
            )
                .values()

        assertEquals(EXPECTED, values["lint"])
    }

    @Test
    fun `modules without an android plugin fall back to the standalone lint plugin`(@TempDir projectDir: File) {
        val values =
            ConventionFixture(
                projectDir,
                lintScript(
                    plugins = emptyList(),
                    setup = "",
                    lint = "project.extensions.getByType(com.android.build.api.dsl.Lint::class.java)",
                    extra = "println(\"fixture:standalone=\${project.plugins.hasPlugin(\"com.android.lint\")}\")",
                ),
            )
                .values()

        assertEquals("true", values["standalone"])
        assertEquals(EXPECTED, values["lint"])
    }

    private fun lintScript(plugins: List<String>, setup: String, lint: String, extra: String = ""): String =
        """
        plugins {
            ${(plugins + "meshtastic.android.lint").joinToString("\n    ") { "id(\"$it\")" }}
        }
        $setup
        tasks.register("printFixture") {
            doLast {
                val lint = $lint
                println("fixture:lint=checkDependencies=${'$'}{lint.checkDependencies} abortOnError=${'$'}{lint.abortOnError} disable=${'$'}{lint.disable.sorted()}")
                $extra
            }
        }
        """
            .trimIndent()

    private companion object {
        const val EXPECTED = "checkDependencies=true abortOnError=false disable=[GradleDependency]"
    }
}
