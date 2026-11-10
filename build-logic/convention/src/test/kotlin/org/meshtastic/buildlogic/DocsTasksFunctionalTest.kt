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

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** `meshtastic.docs` needs no AGP or KGP on the fixture classpath, so it runs through TestKit as is. */
class DocsTasksFunctionalTest {
    @Test
    fun `plugin registers the three documentation tasks`(@TempDir projectDir: File) {
        writeFixture(projectDir)

        val result = runner(projectDir, "tasks", "--group", "documentation").build()

        listOf("generateDocsBundle", "validateDocsBundle", "publishDocsSite").forEach { task ->
            assertTrue(task in result.output, "expected $task in:\n${result.output}")
        }
    }

    @Test
    fun `validateDocsBundle schedules generateDocsBundle first without an explicit dependsOn`(
        @TempDir projectDir: File,
    ) {
        writeFixture(projectDir)

        val result = runner(projectDir, "validateDocsBundle", "--dry-run").build()

        val scheduled = result.output.lines().filter { it.endsWith(" SKIPPED") }.map { it.removeSuffix(" SKIPPED") }
        assertEquals(listOf(":generateDocsBundle", ":validateDocsBundle"), scheduled, result.output)
    }

    @Test
    fun `validateDocsBundle passes on a generated bundle and generateDocsBundle is then up to date`(
        @TempDir projectDir: File,
    ) {
        writeFixture(projectDir)
        projectDir
            .resolve("docs/en/user/getting-started.md")
            .also { it.parentFile.mkdirs() }
            .writeText("---\ntitle: Getting Started\nnav_order: 1\n---\n# Getting started\n\nHello.\n")
        projectDir.resolve(SCHEMA_PATH).also { it.parentFile.mkdirs() }.writeText("{}\n")

        val first = runner(projectDir, "validateDocsBundle").build()

        assertEquals(TaskOutcome.SUCCESS, first.task(":generateDocsBundle")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, first.task(":validateDocsBundle")?.outcome)
        val index = projectDir.resolve("build/generated/docs/common/index.json").readText()
        assertTrue("\"id\":\"getting-started\"" in index, index)

        val second = runner(projectDir, "generateDocsBundle").build()

        assertEquals(TaskOutcome.UP_TO_DATE, second.task(":generateDocsBundle")?.outcome)
    }

    private fun runner(projectDir: File, vararg arguments: String): GradleRunner =
        GradleRunner.create().withProjectDir(projectDir).withPluginClasspath().withArguments(*arguments)

    private fun writeFixture(projectDir: File) {
        projectDir.resolve("settings.gradle.kts").writeText("rootProject.name = \"docs-fixture\"\n")
        projectDir.resolve("build.gradle.kts").writeText("plugins { id(\"meshtastic.docs\") }\n")
    }

    private companion object {
        const val SCHEMA_PATH = "specs/20260507-161858-app-docs-markdown/contracts/keyword-index-schema.json"
    }
}
