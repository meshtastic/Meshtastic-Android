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
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.datadog.gradle.plugin.DdExtension
import com.datadog.gradle.plugin.InstrumentationMode
import com.datadog.gradle.plugin.SdkCheckLevel
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure

class AnalyticsConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            extensions.configure<ApplicationExtension> {
                defaultConfig {
                    productFlavors {
                        all {
                            if (name == "google") {
                                apply(plugin = "com.google.gms.google-services")
                                apply(plugin = "com.google.firebase.crashlytics")
                                apply(plugin = "com.datadoghq.dd-sdk-android-gradle-plugin")
                            }
                        }
                    }
                }
            }
            extensions.configure<ApplicationAndroidComponentsExtension> {
                onVariants { variant ->
                    if (variant.flavorName == "google") {
                        extensions.configure<DdExtension> {
                            variants {
                                register(variant.name) {
                                    site = "US5"
                                    composeInstrumentation = InstrumentationMode.AUTO
                                }
                            }
                            checkProjectDependencies = SdkCheckLevel.NONE
                        }
                    }
                }
            }
        }
    }
}
