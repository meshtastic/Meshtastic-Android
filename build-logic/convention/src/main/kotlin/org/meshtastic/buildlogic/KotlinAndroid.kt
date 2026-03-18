/*
 * Copyright (c) 2025 Meshtastic LLC
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

/**
 * Configure base Kotlin with Android options
 */
internal fun Project.configureKotlinAndroid(
    commonExtension: CommonExtension,
) {
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

    configureKotlin<KotlinAndroidProjectExtension>()
}

/**
 * Configure Kotlin Multiplatform options
 */
internal fun Project.configureKotlinMultiplatform() {
    extensions.configure<KotlinMultiplatformExtension> {
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

    configureKotlin<KotlinMultiplatformExtension>()
}

/**
 * Configure a shared `jvmAndroidMain` source set using Kotlin's hierarchy template DSL.
 *
 * This is for modules that intentionally share JVM-only implementations between the desktop
 * `jvm()` target and the Android target without hand-written `dependsOn` edges.
 */
@OptIn(ExperimentalKotlinGradlePluginApi::class)
internal fun Project.configureJvmAndroidMainHierarchy() {
    extensions.configure<KotlinMultiplatformExtension> {
        applyHierarchyTemplate(KotlinHierarchyTemplate.default) {
            common {
                group("jvmAndroid") {
                    withCompilations { compilation ->
                        compilation.target.targetName == "android" ||
                            compilation.target.targetName == "jvm"
                    }
                }
            }
        }
    }
}

/**
 * Configure common test dependencies for KMP modules
 */
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
            }

            // Configure jvmTest if it exists
            val jvmTest = findByName("jvmTest")
            jvmTest?.dependencies {
                implementation(libs.library("kotest-runner-junit6"))
            }
        }
    }
}

/**
 * Configure base Kotlin options for JVM (non-Android)
 */
internal fun Project.configureKotlinJvm() {
    configureKotlin<KotlinJvmProjectExtension>()
}

/**
 * Configure base Kotlin options
 */
private inline fun <reified T : KotlinBaseExtension> Project.configureKotlin() {
    extensions.configure<T> {
        // Using Java 17 for better compatibility with consumers (e.g. plugins, older environments)
        // while still supporting modern Kotlin features.
        jvmToolchain(17)
    }

    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            allWarningsAsErrors.set(false)
            freeCompilerArgs.addAll(
                // Enable experimental coroutines APIs, including Flow
                "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
                "-opt-in=kotlin.uuid.ExperimentalUuidApi",
                "-opt-in=kotlin.time.ExperimentalTime",
                "-Xexpect-actual-classes",
                "-Xcontext-parameters",
                "-Xannotation-default-target=param-property",
                "-Xskip-prerelease-check"
            )
        }
    }
}
