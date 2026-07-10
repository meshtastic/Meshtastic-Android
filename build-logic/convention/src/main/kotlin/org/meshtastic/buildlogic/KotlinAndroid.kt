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

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import dev.mokkery.gradle.MokkeryGradleExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinBaseExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinHierarchyTemplate
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/** Configure base Kotlin with Android options */
internal fun Project.configureKotlinAndroid(commonExtension: CommonExtension) {
    val compileSdkVersion = configProperties.getProperty("COMPILE_SDK").toInt()
    val minSdkVersion = configProperties.getProperty("MIN_SDK").toInt()
    val targetSdkVersion = configProperties.getProperty("TARGET_SDK").toInt()

    commonExtension.apply {
        compileSdk = compileSdkVersion

        defaultConfig.minSdk = minSdkVersion
        defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        if (this is ApplicationExtension) {
            defaultConfig.targetSdk = targetSdkVersion
        }

        compileOptions.sourceCompatibility = JavaVersion.VERSION_21
        compileOptions.targetCompatibility = JavaVersion.VERSION_21

        testOptions.animationsDisabled = true
        testOptions.unitTests.isReturnDefaultValues = true

        // Exclude duplicate META-INF license files shipped by JUnit Platform JARs
        packaging.resources.excludes.addAll(listOf("META-INF/LICENSE.md", "META-INF/LICENSE-notice.md"))
    }

    configureMokkery()
    configureKotlin<KotlinAndroidProjectExtension>()
}

/**
 * Whether the current build host can run the Kotlin/Native compiler.
 *
 * Kotlin/Native supports linux-x86_64, windows-x86_64, macos-arm64, and macos-x86_64. Other hosts (notably
 * linux-aarch64 CI runners) must skip Apple-target registration to avoid configuration-time crashes when KSP / Dokka
 * introspect the iOS compilations.
 */
private fun supportsKotlinNative(): Boolean {
    val os = System.getProperty("os.name").orEmpty().lowercase()
    val arch = System.getProperty("os.arch").orEmpty().lowercase()
    val isX64 = arch == "amd64" || arch == "x86_64"
    return when {
        os.contains("mac") || os.contains("darwin") -> true
        os.contains("linux") -> isX64
        os.contains("windows") -> isX64
        else -> false
    }
}

/** Configure Kotlin Multiplatform options */
internal fun Project.configureKotlinMultiplatform() {
    // Note: we used to force `org.jetbrains.skiko` to a hard-coded version here to
    // align coil3's older skiko requirement with CMP's. As of CMP 1.11.x the
    // compose-desktop module publishes `{strictly <version>}` constraints on
    // skiko, so Gradle resolves the conflict naturally. A hard-coded force would
    // silently downgrade skiko on the next CMP bump and break the renderer —
    // so we let CMP own the version.

    extensions.configure<KotlinMultiplatformExtension> {
        // Standard KMP targets for Meshtastic
        jvm()

        // iOS targets for compile-only validation. Only register on hosts where
        // Kotlin/Native can run — KSP attaches to every Kotlin target's compilation
        // and crashes at configuration time on unsupported hosts (e.g. linux-aarch64)
        // with "Could not create task ':…:kspKotlinIosArm64' > Unknown host target".
        // Supported set: https://kotlinlang.org/docs/native-target-support.html
        if (supportsKotlinNative()) {
            iosArm64()
            iosSimulatorArm64()
        }

        // Configure the Android target if the plugin is applied
        pluginManager.withPlugin("com.android.kotlin.multiplatform.library") {
            extensions.findByType<KotlinMultiplatformAndroidLibraryTarget>()?.apply {
                compileSdk = configProperties.getProperty("COMPILE_SDK").toInt()
                minSdk = configProperties.getProperty("MIN_SDK").toInt()

                // Default: disable Android resources for most KMP modules.
                // Modules that need resources (e.g. core:resources) override this
                // explicitly in their build.gradle.kts android {} block.
                androidResources.enable = false

                // Set the namespace automatically if not already set
                if (namespace == null) {
                    val pkg = this@configureKotlinMultiplatform.path.removePrefix(":").replace(":", ".")
                    namespace = "org.meshtastic.$pkg"
                }
            }
        }
    }

    // Disable iOS native test link & run tasks.
    // iOS targets exist only for compile-time validation; linking test
    // executables is extremely slow and causes `./gradlew test` to hang.
    tasks.configureEach {
        val taskName = name.lowercase()
        if (taskName.contains("iosarm64") || taskName.contains("iossimulatorarm64")) {
            val isDisabledIosTask =
                (taskName.startsWith("link") && taskName.contains("test")) ||
                    taskName == "iosarm64test" ||
                    taskName == "iossimulatorarm64test" ||
                    taskName.endsWith("testbinaries")
            if (isDisabledIosTask) {
                enabled = false
            }
        }
    }

    configureMokkery()
    configureKotlin<KotlinMultiplatformExtension>()
}

/** Configure Mokkery for the project */
internal fun Project.configureMokkery() {
    pluginManager.withPlugin(libs.plugin("mokkery").get().pluginId) {
        extensions.configure<MokkeryGradleExtension> { stubs.allowConcreteClassInstantiation.set(true) }
    }
}

/**
 * Configure a shared `jvmAndroidMain` source set using Kotlin's hierarchy template DSL.
 *
 * This is for modules that intentionally share JVM-only implementations between the desktop `jvm()` target and the
 * Android target without hand-written `dependsOn` edges.
 */
@OptIn(ExperimentalKotlinGradlePluginApi::class)
internal fun Project.configureJvmAndroidMainHierarchy() {
    extensions.configure<KotlinMultiplatformExtension> {
        applyHierarchyTemplate(KotlinHierarchyTemplate.default) {
            common {
                group("jvmAndroid") {
                    withCompilations { compilation ->
                        compilation.target.targetName == "android" || compilation.target.targetName == "jvm"
                    }
                }
            }
        }
    }
}

/** Configure common test dependencies for KMP modules */
internal fun Project.configureKmpTestDependencies() {
    extensions.configure<KotlinMultiplatformExtension> {
        sourceSets.apply {
            val commonTest = findByName("commonTest") ?: return@apply
            commonTest.dependencies {
                implementation(kotlin("test"))
                implementation(libs.library("kotest-assertions"))
                implementation(libs.library("kotest-property"))
                implementation(libs.library("turbine"))
                implementation(libs.library("kotlinx-coroutines-test"))
            }

            // Configure androidHostTest lazily — the source set is created when the
            // module's build script calls `withHostTest { }`, which runs *after* the
            // convention plugin's `apply`.  Using `matching + configureEach` defers
            // configuration until the source set actually materialises.
            matching { it.name == "androidHostTest" }
                .configureEach {
                    dependencies {
                        // kotlin.test auto-selects kotlin-test-junit because testAndroidHostTest
                        // does NOT use useJUnitPlatform() (see configureTestOptions).
                        // No explicit kotlin("test") or kotlin("test-junit") override needed —
                        // adding them would conflict with auto-selection and break resource merging.
                        implementation(libs.library("kotest-assertions"))
                        implementation(libs.library("kotest-property"))
                        implementation(libs.library("turbine"))
                        implementation(libs.library("robolectric"))
                        implementation(libs.library("androidx-test-core"))
                        implementation(libs.library("androidx-test-ext-junit"))
                    }
                }

            // Configure jvmTest lazily for the same reason.
            matching { it.name == "jvmTest" }
                .configureEach { dependencies { implementation(libs.library("kotest-runner-junit6")) } }
        }
    }
}

/** Configure base Kotlin options for JVM (non-Android) */
internal fun Project.configureKotlinJvm() {
    configureKotlin<KotlinJvmProjectExtension>()
}

/** Compiler args shared across all Kotlin targets (JVM, Android, iOS, etc.). */
private val SHARED_COMPILER_ARGS =
    listOf(
        "-Xexpect-actual-classes",
        "-Xskip-prerelease-check",
        // No -Xbackend-threads: parallel codegen races and crashes release builds (KT-83578).
    )

private const val JDK_VERSION = 21

/** Configure base Kotlin options */
private inline fun <reified T : KotlinBaseExtension> Project.configureKotlin() {
    extensions.configure<T> {
        jvmToolchain(JDK_VERSION)

        if (this is KotlinMultiplatformExtension) {
            targets.configureEach {
                val isJvmTarget = platformType.name == "jvm" || platformType.name == "androidJvm"
                compilations.configureEach {
                    compileTaskProvider.configure {
                        compilerOptions {
                            freeCompilerArgs.add("-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi")
                            freeCompilerArgs.addAll(SHARED_COMPILER_ARGS)
                            if (isJvmTarget) {
                                freeCompilerArgs.add("-jvm-default=no-compatibility")
                            }
                        }
                    }
                }
            }
        }
    }

    val warningsAsErrors = providers.gradleProperty("warningsAsErrors").map { it.toBoolean() }.getOrElse(false)

    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
            allWarningsAsErrors.set(warningsAsErrors)

            // For non-KMP modules, configure compiler args here since they don't use targets.compilations.
            // KMP modules already set these via the targets block above — only jvmTarget/warnings needed here.
            if (T::class != KotlinMultiplatformExtension::class) {
                freeCompilerArgs.add("-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi")
                freeCompilerArgs.addAll(SHARED_COMPILER_ARGS)
                freeCompilerArgs.add("-jvm-default=no-compatibility")
            }
        }
    }
}
