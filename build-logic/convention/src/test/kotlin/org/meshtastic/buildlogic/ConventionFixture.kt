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

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import java.io.File

/**
 * A throwaway Gradle build that applies convention plugins from the plugin-under-test classpath. It carries the repo's
 * real version catalog, because the conventions look their plugin ids, libraries and versions up by alias.
 */
internal class ConventionFixture(private val projectDir: File, buildScript: String) {
    init {
        projectDir.resolve("settings.gradle.kts").writeText("rootProject.name = \"fixture\"\n")
        projectDir.resolve("gradle").mkdirs()
        File(System.getProperty("meshtastic.versionCatalog")).copyTo(projectDir.resolve("gradle/libs.versions.toml"))
        projectDir.resolve("config.properties").writeText(CONFIG_PROPERTIES)
        projectDir.resolve("local.properties").writeText("sdk.dir=${androidSdk().invariantSeparatorsPath}\n")
        projectDir.resolve("build.gradle.kts").writeText(buildScript)
    }

    fun build(vararg arguments: String): BuildResult = GradleRunner.create()
        .withProjectDir(projectDir)
        .withPluginClasspath()
        .withArguments("--quiet", "--stacktrace", *arguments)
        .build()

    /** Runs the fixture's `printFixture` task and returns the `fixture:key=value` lines it printed. */
    fun values(vararg arguments: String): Map<String, String> = build("printFixture", *arguments)
        .output
        .lines()
        .filter { it.startsWith(PREFIX) }
        .associate { line -> line.removePrefix(PREFIX).substringBefore('=') to line.substringAfter('=') }

    companion object {
        const val PREFIX = "fixture:"

        private const val CONFIG_PROPERTIES = "MIN_SDK=26\nTARGET_SDK=37\nCOMPILE_SDK=37\nCOMPILE_SDK_MINOR=2\n"

        fun androidSdk(): File {
            val candidates =
                listOfNotNull(System.getenv("ANDROID_HOME"), System.getenv("ANDROID_SDK_ROOT")) +
                    listOf(
                        "${System.getProperty("user.home")}/Library/Android/sdk",
                        "${System.getProperty("user.home")}/Android/Sdk",
                    )
            return candidates.map(::File).firstOrNull { it.isDirectory }
                ?: error("Android SDK not found; set ANDROID_HOME")
        }
    }
}
