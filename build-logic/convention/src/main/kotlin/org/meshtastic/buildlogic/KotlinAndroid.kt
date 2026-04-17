/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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

        val javaVersion = if (project.name in PUBLISHED_MODULES) JavaVersion.VERSION_17 else JavaVersion.VERSION_21
        compileOptions.sourceCompatibility = javaVersion
        compileOptions.targetCompatibility = javaVersion

        testOptions.animationsDisabled = true
        testOptions.unitTests.isReturnDefaultValues = true

        // Exclude duplicate META-INF license files shipped by JUnit Platform JARs
        packaging.resources.excludes.addAll(
            listOf(
                "META-INF/LICENSE.md",
                "META-INF/LICENSE-notice.md",
            ),
        )
    }

    configureMokkery()
    configureKotlin<KotlinAndroidProjectExtension>()
}

/** Configure Kotlin Multiplatform options */
internal fun Project.configureKotlinMultiplatform() {
    // Skiko is an internal CMP implementation detail; third-party KMP libraries
    // (e.g. coil3) can carry an older skiko transitive requirement that Gradle
    // upgrades to the CMP-bundled version, triggering a "Skiko dependencies'
    // versions are incompatible" warning from CMP's compatibility checker.
    // Force the version to match CMP so the checker sees a consistent graph.
    // Pinned here rather than in the version catalog because this plugin is the
    // only consumer — bump together with the compose-multiplatform version.
    val skikoVersion = "0.144.5"
    configurations.configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.skiko") {
                useVersion(skikoVersion)
                because("Align Skiko with the version bundled by Compose Multiplatform")
            }
        }
    }

    extensions.configure<KotlinMultiplatformExtension> {
        // Standard KMP targets for Meshtastic
        jvm()

        // Configure the iOS targets for compile-only validation
        // We only add these for modules that already have KMP structure
        iosArm64()
        iosSimulatorArm64()

        // Configure the Android target if the plugin is applied
        pluginManager.withPlugin("com.android.kotlin.multiplatform.library") {
            extensions.findByType<KotlinMultiplatformAndroidLibraryTarget>()?.apply {
                compileSdk = configProperties.getProperty("COMPILE_SDK").toInt()
                minSdk = configProperties.getProperty("MIN_SDK").toInt()

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
            if (
                taskName.startsWith("link") && taskName.contains("test") ||
                taskName == "iosarm64test" ||
                taskName == "iossimulatorarm64test" ||
                taskName.endsWith("testbinaries")
            ) {
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
            }

            // Configure androidHostTest lazily — the source set is created when the
            // module's build script calls `withHostTest { }`, which runs *after* the
            // convention plugin's `apply`.  Using `matching + configureEach` defers
            // configuration until the source set actually materialises.
            matching { it.name == "androidHostTest" }.configureEach {
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
                }
            }

            // Configure jvmTest lazily for the same reason.
            matching { it.name == "jvmTest" }.configureEach {
                dependencies {
                    implementation(libs.library("kotest-runner-junit6"))
                }
            }
        }
    }
}

/** Configure base Kotlin options for JVM (non-Android) */
internal fun Project.configureKotlinJvm() {
    configureKotlin<KotlinJvmProjectExtension>()
}

/** Modules published for external consumers — use Java 17 for broader compatibility. */
private val PUBLISHED_MODULES = setOf("api", "model", "proto")

/** Compiler args shared across all Kotlin targets (JVM, Android, iOS, etc.). */
private val SHARED_COMPILER_ARGS = listOf(
    "-opt-in=kotlin.uuid.ExperimentalUuidApi",
    "-opt-in=kotlin.time.ExperimentalTime",
    "-Xexpect-actual-classes",
    "-Xcontext-parameters",
    "-Xannotation-default-target=param-property",
    "-Xskip-prerelease-check",
)

/** Configure base Kotlin options */
private inline fun <reified T : KotlinBaseExtension> Project.configureKotlin() {
    val isPublishedModule = project.name in PUBLISHED_MODULES

    extensions.configure<T> {
        val javaVersion = if (isPublishedModule) 17 else 21
        // Using Java 17 for published modules for better compatibility with consumers (e.g. plugins, older environments),
        // and Java 21 for the rest of the app.
        jvmToolchain(javaVersion)

        if (this is KotlinMultiplatformExtension) {
            targets.configureEach {
                val isJvmTarget = platformType.name == "jvm" || platformType.name == "androidJvm"
                compilations.configureEach {
                    compileTaskProvider.configure {
                        compilerOptions {
                            if (!isPublishedModule) {
                                freeCompilerArgs.add("-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi")
                            }
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
            jvmTarget.set(if (isPublishedModule) JvmTarget.JVM_17 else JvmTarget.JVM_21)
            allWarningsAsErrors.set(warningsAsErrors)
            if (!isPublishedModule) {
                freeCompilerArgs.add("-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi")
            }
            freeCompilerArgs.addAll(SHARED_COMPILER_ARGS)
            freeCompilerArgs.add("-jvm-default=no-compatibility")
        }
    }
}
