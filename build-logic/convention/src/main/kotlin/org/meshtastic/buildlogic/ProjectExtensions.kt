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

import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependencyBundle
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.testing.AbstractTestTask
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.gradle.plugin.use.PluginDependency
import org.gradle.testretry.TestRetryTaskExtension
import java.io.FileInputStream
import java.util.Properties

private const val MAX_TEST_RETRIES = 2
private const val MAX_TEST_FAILURES = 10

val Project.libs
    get(): VersionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

fun VersionCatalog.library(alias: String): Provider<MinimalExternalModuleDependency> = findLibrary(alias).get()

fun VersionCatalog.bundle(alias: String): Provider<ExternalModuleDependencyBundle> = findBundle(alias).get()

fun VersionCatalog.plugin(alias: String): Provider<PluginDependency> = findPlugin(alias).get()

fun VersionCatalog.version(alias: String): String = findVersion(alias).get().requiredVersion

val Project.configProperties: Properties
    get() {
        val key = "meshtastic.configProperties"
        val ext = extensions.extraProperties
        @Suppress("UNCHECKED_CAST")
        if (ext.has(key)) return ext.get(key) as Properties
        val properties = Properties()
        val propertiesFile = isolated.rootProject.projectDirectory.file("config.properties").asFile
        if (propertiesFile.exists()) {
            FileInputStream(propertiesFile).use { properties.load(it) }
        }
        ext.set(key, properties)
        return properties
    }

/** Configure common test options like parallel execution and logging. */
internal fun Project.configureTestOptions() {
    // Gradle 9 requires junit-platform-launcher on every test runtime classpath when
    // useJUnitPlatform() is active.  Add it lazily to all *UnitTestRuntimeClasspath and
    // *TestRuntimeClasspath configurations so all Android and JVM test tasks get it
    // without requiring per-module declarations.
    configurations
        .matching { it.name.endsWith("UnitTestRuntimeClasspath") || it.name.endsWith("TestRuntimeClasspath") }
        .configureEach {
            val launcher = libs.library("junit-platform-launcher")
            project.dependencies.add(name, launcher)
        }

    tasks.withType<Test>().configureEach {
        // JUnit 5: activate JUnit Platform — but NOT for androidHostTest (Robolectric) tasks
        // in KMP modules.  Those tasks run JUnit 4 natively; applying useJUnitPlatform()
        // would force kotlin-test-junit5 selection which conflicts with the kotlin-test-junit
        // that Kotlin auto-selects for Robolectric @RunWith tests when Platform is absent.
        if (name != "testAndroidHostTest") {
            useJUnitPlatform()
        }
        // Parallelize unit tests at the Gradle fork level.
        // In CI, use all available processors; locally use half to keep the machine responsive.
        val isCi = project.findProperty("ci") == "true"
        maxParallelForks =
            if (isCi) {
                Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
            } else {
                (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
            }
        maxHeapSize = "2g"

        // JUnit Jupiter parallel execution within each Gradle fork.
        // Classes run sequentially ("same_thread") because 19+ ViewModel test classes use
        // Dispatchers.setMain() — a JVM-global singleton that races when classes execute
        // concurrently in the same JVM. Cross-module parallelism via Gradle forks (above)
        // already provides the primary test speedup.
        systemProperty("junit.jupiter.execution.parallel.enabled", "true")
        systemProperty("junit.jupiter.execution.parallel.mode.default", "same_thread")
        systemProperty("junit.jupiter.execution.parallel.mode.classes.default", "same_thread")
        systemProperty("junit.jupiter.execution.parallel.config.strategy", "dynamic")
        systemProperty("junit.jupiter.execution.parallel.config.dynamic.factor", "1")

        // Allow modules with no discovered tests to pass without failing the build
        filter { isFailOnNoMatchingTests = false }

        // Show test results in the console
        testLogging { events(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED) }
    }

    // Gradle 9+ fails when test sources exist but no test classes are discovered (e.g. all
    // tests are commented out). Disable to avoid breaking builds for modules with WIP tests.
    tasks.withType<AbstractTestTask>().configureEach { failOnNoDiscoveredTests.set(false) }

    // Configure test retry if the plugin is applied
    pluginManager.withPlugin("org.gradle.test-retry") {
        tasks.withType<Test>().configureEach {
            extensions.configure<TestRetryTaskExtension> {
                maxRetries.set(MAX_TEST_RETRIES)
                maxFailures.set(MAX_TEST_FAILURES)
                failOnPassedAfterRetry.set(false)
            }
        }
    }
}
