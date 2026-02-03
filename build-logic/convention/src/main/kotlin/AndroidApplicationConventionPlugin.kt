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
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.meshtastic.buildlogic.configureKotlinAndroid
import org.meshtastic.buildlogic.configureTestOptions

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {

            apply(plugin = "com.android.application")
            apply(plugin = "org.gradle.test-retry")
            apply(plugin = "meshtastic.android.lint")
            apply(plugin = "meshtastic.detekt")
            apply(plugin = "meshtastic.spotless")
            apply(plugin = "meshtastic.analytics")
            apply(plugin = "meshtastic.kover")
            apply(plugin = "meshtastic.dokka")

            extensions.configure<ApplicationExtension> {
                configureKotlinAndroid(this)
                
                defaultConfig {
                    testInstrumentationRunner = "com.geeksville.mesh.TestRunner"
                    vectorDrawables.useSupportLibrary = true
                }

                testOptions.animationsDisabled = true

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
                        enableAndroidTestCoverage = true
                    }
                }

                buildFeatures {
                    buildConfig = true
                }
            }
            configureTestOptions()
        }
    }
}
