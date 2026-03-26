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

        if (this is ApplicationExtension) {
            defaultConfig.targetSdk = targetSdkVersion
        }

        compileOptions.sourceCompatibility = JavaVersion.VERSION_17
        compileOptions.targetCompatibility = JavaVersion.VERSION_17
    }

    configureMokkery()
    configureKotlin<KotlinAndroidProjectExtension>()
}

/** Configure Kotlin Multiplatform options */
internal fun Project.configureKotlinMultiplatform() {
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

            // Configure androidHostTest if it exists
            val androidHostTest = findByName("androidHostTest")
            androidHostTest?.dependencies {
                implementation(kotlin("test"))
                implementation(libs.library("kotest-assertions"))
                implementation(libs.library("kotest-property"))
                implementation(libs.library("turbine"))
                implementation(libs.library("robolectric"))
                implementation(libs.library("androidx-test-core"))
            }

            // Configure jvmTest if it exists
            val jvmTest = findByName("jvmTest")
            jvmTest?.dependencies { implementation(libs.library("kotest-runner-junit6")) }
        }
    }
}

/** Configure base Kotlin options for JVM (non-Android) */
internal fun Project.configureKotlinJvm() {
    configureKotlin<KotlinJvmProjectExtension>()
}

/** Configure base Kotlin options */
private inline fun <reified T : KotlinBaseExtension> Project.configureKotlin() {
    extensions.configure<T> {
        // Using Java 17 for better compatibility with consumers (e.g. plugins, older environments)
        // while still supporting modern Kotlin features.
        jvmToolchain(17)

        if (this is KotlinMultiplatformExtension) {
            targets.configureEach {
                compilations.configureEach {
                    compileTaskProvider.configure {
                        compilerOptions {
                            freeCompilerArgs.addAll(
                                "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
                                "-opt-in=kotlin.uuid.ExperimentalUuidApi",
                                "-opt-in=kotlin.time.ExperimentalTime",
                                "-opt-in=kotlinx.cinterop.ExperimentalForeignApi",
                                "-Xexpect-actual-classes",
                                "-Xcontext-parameters",
                                "-Xannotation-default-target=param-property",
                                "-Xskip-prerelease-check",
                            )
                        }
                    }
                }
            }
        }
    }

    val warningsAsErrors = providers.gradleProperty("warningsAsErrors").map { it.toBoolean() }.getOrElse(false)

    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            allWarningsAsErrors.set(warningsAsErrors)
            freeCompilerArgs.addAll(
                // Enable experimental coroutines APIs, including Flow
                "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
                "-opt-in=kotlin.uuid.ExperimentalUuidApi",
                "-opt-in=kotlin.time.ExperimentalTime",
                "-opt-in=kotlinx.cinterop.ExperimentalForeignApi",
                "-Xexpect-actual-classes",
                "-Xcontext-parameters",
                "-Xannotation-default-target=param-property",
                "-Xskip-prerelease-check",
            )
        }
    }
}
