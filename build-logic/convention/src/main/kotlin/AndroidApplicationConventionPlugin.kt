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

import com.android.build.api.dsl.ApplicationExtension
import com.geeksville.mesh.buildlogic.configureFlavors
import com.geeksville.mesh.buildlogic.configureKotlinAndroid
import com.geeksville.mesh.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.exclude
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {

            apply(plugin = "com.android.application")
            apply(plugin = "org.jetbrains.kotlin.android")
            apply(plugin = "meshtastic.android.lint")
            apply(plugin = "meshtastic.android.room")
            apply(plugin = "meshtastic.hilt")
            apply(plugin = "com.autonomousapps.dependency-analysis")

            extensions.configure<ApplicationExtension> {
                configureKotlinAndroid(this)
                configureFlavors(this)
                defaultConfig.targetSdk = 36
                testOptions.animationsDisabled = true

                defaultConfig {
                    targetSdk = 36
                    testInstrumentationRunner = "com.geeksville.mesh.TestRunner"
                    vectorDrawables.useSupportLibrary = true
                }

                buildTypes {
                    getByName("release") {
                        isMinifyEnabled = true
                        isShrinkResources = true
                        proguardFiles(
                            getDefaultProguardFile("proguard-android-optimize.txt"),
                            "proguard-rules.pro"
                        )
                    }
                    getByName("debug") {
                        isDebuggable = true
                        isPseudoLocalesEnabled = true
                    }
                }

                buildFeatures {
                    buildConfig = true
                }
                dependencies {
                    // F-Droid specific dependencies
                    "fdroidImplementation"(libs.findBundle("osm").get())
                    "fdroidImplementation"(libs.findLibrary("osmdroid-geopackage").get()) {
                        exclude(group = "com.j256.ormlite")
                    }

                    // Google specific dependencies
                    "googleImplementation"(libs.findBundle("maps-compose").get())
                    "googleImplementation"(libs.findLibrary("awesome-app-rating").get())
                    "googleImplementation"(platform(libs.findLibrary("firebase-bom").get()))
                    "googleImplementation"(libs.findBundle("datadog").get())
                }

            }


            
            extensions.configure<JavaPluginExtension> {
                toolchain {
                    languageVersion.set(JavaLanguageVersion.of(21))
                }
            }

            tasks.withType<KotlinCompile>().configureEach {
                compilerOptions {
                    freeCompilerArgs.addAll(
                        "-opt-in=kotlin.RequiresOptIn",
                        "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
                        "-Xcontext-receivers",
                        "-Xannotation-default-target=param-property",
                    )
                }
            }
        }
    }
}
