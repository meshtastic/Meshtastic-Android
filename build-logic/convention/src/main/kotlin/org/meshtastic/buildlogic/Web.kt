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

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.configuration.BuildFeatures
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.gradle.work.DisableCachingByDefault
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinHierarchyTemplate
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrCompilation
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrLink
import org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import javax.inject.Inject

/** The Gradle property that registers the wasmJs target and includes `:webApp`; `settings.gradle.kts` reads it too. */
const val WEB_TARGET_PROPERTY = "meshtastic.web"

private const val WASM_PARALLEL_LINKS = 2

/** Whether this build registers the wasmJs target (`-Pmeshtastic.web=true`). */
val Project.webTargetEnabled: Boolean
    get() = providers.gradleProperty(WEB_TARGET_PROPERTY).map(String::toBoolean).getOrElse(false)

/**
 * KGP's wasm npm resolver applies plugins to the root project from every wasmJs project, which Isolated Projects
 * rejects, so a web build turns it off on the command line while every other build keeps it.
 */
internal fun Project.requireIsolatedProjectsOffForWeb(buildFeatures: BuildFeatures) {
    if (webTargetEnabled && buildFeatures.isolatedProjects.active.getOrElse(false)) {
        throw GradleException(
            "-P$WEB_TARGET_PROPERTY=true needs Isolated Projects off: add -Dorg.gradle.isolated-projects=false. " +
                "KGP's wasm npm resolver configures the root project from each wasmJs project.",
        )
    }
}

/** What a module with the web target tells `meshtastic.kmp.web`. */
abstract class MeshtasticWebExtension(private val kotlin: KotlinMultiplatformExtension) {
    /** Runs this module's wasmJs tests in headless Chrome. Off for a module whose tests cannot pass there yet. */
    abstract val browserTests: Property<Boolean>

    /**
     * Test filter patterns, matched against `Class.method` (`*ClassTest.*`, `*ClassTest.method*`), that
     * `wasmJsBrowserTest` skips: tests that fail in the browser, each listed with the reason in its module's build
     * script.
     */
    abstract val browserTestExclusions: SetProperty<String>

    /** Whether this build registers the wasmJs target. */
    abstract val enabled: Property<Boolean>

    /** Configures `wasmJsMain`, which exists only when the web target is registered. */
    fun wasmJsMain(action: KotlinSourceSet.() -> Unit) {
        if (enabled.get()) kotlin.sourceSets.getByName("wasmJsMain").action()
    }
}

private val KotlinCompilation<*>.isWeb: Boolean
    get() = platformType == KotlinPlatformType.wasm || platformType == KotlinPlatformType.js

/**
 * Registers the wasmJs target when [webTargetEnabled], and always builds the `nonWeb` source sets beside it, so code
 * that has no browser equivalent lives in `nonWebMain` whether or not this build compiles for the web.
 */
@OptIn(ExperimentalWasmDsl::class, ExperimentalKotlinGradlePluginApi::class)
internal fun Project.configureKmpWebTarget() {
    val kotlin = extensions.getByType(KotlinMultiplatformExtension::class.java)
    val web = extensions.create<MeshtasticWebExtension>("meshtasticWeb", kotlin)
    web.enabled.set(webTargetEnabled)
    web.enabled.disallowChanges()
    web.browserTests.convention(true)

    kotlin.apply {
        if (webTargetEnabled) {
            wasmJs {
                // browser() on every module :webApp reaches, or KGP's root npm resolver fails. Karma until Kotlin 2.5's
                // Playwright test DSL (KT-87082): Node.js cannot run these tests, because nearly every module links
                // Compose through core:model, and skiko's wasm loader works only in a browser.
                browser {
                    testTask {
                        useKarma {
                            useChromeHeadless()
                            useConfigDirectory(isolated.rootProject.projectDirectory.dir("config/karma").asFile)
                        }
                    }
                }
            }
        }

        // A compilation predicate, because withAndroidTarget()/withApple() drop androidMain under
        // com.android.kotlin.multiplatform.library (KT-80409). "native" is nested so iosMain itself, not only the iOS
        // leaf compilations, sees nonWebMain.
        applyHierarchyTemplate(KotlinHierarchyTemplate.default) {
            common {
                group("nonWeb") {
                    withCompilations { !it.isWeb }
                    group("native")
                }
            }
        }

        // meshtastic.kmp.jvm.android's group comes from its own template, which cannot nest it under nonWeb.
        val nonWebMain = sourceSets.getByName("nonWebMain")
        val nonWebTest = sourceSets.getByName("nonWebTest")
        sourceSets.named { it == "jvmAndroidMain" }.configureEach { dependsOn(nonWebMain) }
        sourceSets.named { it == "jvmAndroidTest" }.configureEach { dependsOn(nonWebTest) }
    }

    if (webTargetEnabled) {
        configureWasmJsCompilations()

        val browserTests = web.browserTests
        val exclusions = web.browserTestExclusions
        tasks
            .withType<KotlinJsTest>()
            .named { it == "wasmJsBrowserTest" }
            .configureEach {
                enabled = browserTests.get()
                exclusions.get().forEach { filter.excludeTestsMatching(it) }
            }
        configureSkikoForWasmTests(kotlin)

        // Compose registers this on every wasmJs browser() library and fails it without binaries.executable().
        tasks.named { it == "checkComposeUiTestConfigurationForWasmJs" }.configureEach { enabled = false }
    }
}

/**
 * Puts skiko's wasm runtime beside each test binary. Nearly every module links Compose through core:model, so its test
 * binary imports `skiko.mjs`, which the Compose plugin copies only into modules that apply it.
 */
private fun Project.configureSkikoForWasmTests(kotlin: KotlinMultiplatformExtension) {
    val testRuntime = configurations.named("wasmJsTestRuntimeClasspath")
    val dependencyFactory = dependencies
    val skikoRuntime =
        configurations.dependencyScope("meshtasticSkikoWasmRuntime") {
            // The runtime jar's version is that of the skiko the test binary already links.
            dependencies.addAllLater(
                testRuntime.map { classpath ->
                    classpath.incoming.resolutionResult.allComponents
                        .mapNotNull { it.moduleVersion }
                        .filter { it.group == "org.jetbrains.skiko" }
                        .map { it.version }
                        .distinct()
                        .map { version ->
                            dependencyFactory.create("org.jetbrains.skiko:skiko-js-wasm-runtime:$version")
                        }
                },
            )
        }
    val skikoRuntimeFiles =
        configurations.resolvable("meshtasticSkikoWasmRuntimeFiles") { extendsFrom(skikoRuntime.get()) }
    val unpack =
        tasks.register<UnpackSkikoWasmRuntime>("unpackSkikoWasmRuntimeForTests") {
            runtimeJars.from(skikoRuntimeFiles)
            // The Compose plugin syncs the same files into the modules it is applied to.
            composeSyncsIt.set(provider { pluginManager.hasPlugin("org.jetbrains.compose") })
            outputDir.set(layout.buildDirectory.dir("meshtastic/skiko-wasm-test-runtime"))
        }
    kotlin.targets
        .named { it == "wasmJs" }
        .configureEach {
            compilations
                .named { it == "test" }
                .configureEach {
                    (this as KotlinJsIrCompilation).binaries.configureEach {
                        linkSyncTask.configure { from.from(unpack.flatMap { it.outputDir }) }
                    }
                }
        }
}

/** Unpacks skiko's `skiko.mjs` and `skiko.wasm` from its runtime jar, or leaves the directory empty. */
@DisableCachingByDefault(because = "Unpacking one small jar is cheaper than a cache lookup")
abstract class UnpackSkikoWasmRuntime : DefaultTask() {
    @get:Classpath abstract val runtimeJars: ConfigurableFileCollection

    @get:Input abstract val composeSyncsIt: Property<Boolean>

    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @get:Inject abstract val archives: ArchiveOperations

    @get:Inject abstract val files: FileSystemOperations

    @TaskAction
    fun unpack() {
        val jars = if (composeSyncsIt.get()) emptySet() else runtimeJars.files
        files.sync {
            jars.forEach { jar -> from(archives.zipTree(jar)) { include("*.mjs", "*.wasm") } }
            into(outputDir)
        }
    }
}

/** Shared by every wasmJs link: linking several test binaries at once runs the Kotlin daemon out of heap. */
internal abstract class WasmLinkLimit : BuildService<BuildServiceParameters.None>

/** What every wasmJs project shares: the interop opt-in and the tooling downloads `settings.gradle.kts` declares. */
internal fun Project.configureWasmJsCompilations() {
    configureWasmToolingDownloads()
    val linkLimit =
        gradle.sharedServices.registerIfAbsent("meshtasticWasmLinkLimit", WasmLinkLimit::class.java) {
            maxParallelUsages.set(WASM_PARALLEL_LINKS)
        }
    tasks.withType<KotlinJsIrLink>().configureEach { usesService(linkLimit) }
    extensions.configure<KotlinMultiplatformExtension> {
        sourceSets
            .named { it.startsWith("wasmJs") }
            .configureEach { languageSettings.optIn("kotlin.js.ExperimentalWasmJsInterop") }
    }
}

/** `:webApp`: a wasmJs-only executable, configured as the KMP conventions configure a library. */
@OptIn(ExperimentalWasmDsl::class)
internal fun Project.configureWebApplication() {
    extensions.configure<KotlinMultiplatformExtension> {
        wasmJs {
            outputModuleName.set("webApp")
            browser { commonWebpackConfig { outputFileName = "webApp.js" } }
            binaries.executable()
        }
    }
    configureWasmJsCompilations()

    val warningsAsErrors = kotlinWarningsAsErrors
    tasks.withType<KotlinCompilationTask<*>>().configureEach {
        compilerOptions.allWarningsAsErrors.set(warningsAsErrors)
    }
}
