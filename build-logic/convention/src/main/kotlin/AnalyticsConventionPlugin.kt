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
import org.gradle.kotlin.dsl.findByType
import org.meshtastic.buildlogic.libs
import org.meshtastic.buildlogic.plugin

/**
 * Convention plugin for analytics (Google Services, Crashlytics, Datadog).
 * Segregates these plugins to only affect the "google" flavor and disables their tasks for "fdroid".
 */
class AnalyticsConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            // Apply plugins only when the "google" flavor is present.
            extensions.configure<ApplicationExtension> {
                productFlavors.all {
                    if (name == "google") {
                        apply(plugin = libs.plugin("google-services").get().pluginId)
                        apply(plugin = libs.plugin("firebase-crashlytics").get().pluginId)
                        apply(plugin = libs.plugin("datadog").get().pluginId)
                    }
                }
            }

            // More efficient task segregation: Only register task-disabling listeners if the plugins are applied.
            // This avoids iterating all tasks with a generic filter and improves configuration performance.
            plugins.withId("com.google.gms.google-services") {
                tasks.configureEach {
                    if (name.contains("fdroid", ignoreCase = true) && name.contains("GoogleServices")) {
                        enabled = false
                    }
                }
            }

            plugins.withId("com.google.firebase.crashlytics") {
                tasks.configureEach {
                    if (name.contains("fdroid", ignoreCase = true) &&
                        (name.contains("Crashlytics", ignoreCase = true) || name.contains("buildId", ignoreCase = true))
                    ) {
                        enabled = false
                    }
                }
            }

            plugins.withId("com.datadoghq.dd-sdk-android-gradle-plugin") {
                tasks.configureEach {
                    if (name.contains("fdroid", ignoreCase = true) && name.contains("Datadog", ignoreCase = true)) {
                        enabled = false
                    }
                }
            }

            // Configure variant-specific extensions.
            extensions.configure<ApplicationAndroidComponentsExtension> {
                onVariants { variant ->
                    if (variant.flavorName == "google") {
                        extensions.findByType<DdExtension>()?.apply {
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
