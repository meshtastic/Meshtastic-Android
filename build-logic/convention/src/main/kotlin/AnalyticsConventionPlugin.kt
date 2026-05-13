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
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.datadog.gradle.plugin.DdExtension
import com.datadog.gradle.plugin.InjectBuildIdToAssetsTask
import com.datadog.gradle.plugin.SdkCheckLevel
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.withType
import org.meshtastic.buildlogic.libs
import org.meshtastic.buildlogic.plugin
import java.io.File

/**
 * Convention plugin for analytics (Google Services, Crashlytics, Datadog). Segregates these plugins to only affect the
 * "google" flavor and disables their tasks for "fdroid".
 */
class AnalyticsConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            applyAnalyticsPlugins()
            disableFdroidAnalyticsTasks()
            configureDatadogVariants()
        }
    }
}

private fun Project.applyAnalyticsPlugins() {
    extensions.configure<ApplicationExtension> {
        productFlavors.all {
            if (name == "google") {
                apply(plugin = libs.plugin("google-services").get().pluginId)
                apply(plugin = libs.plugin("firebase-crashlytics").get().pluginId)
                apply(plugin = libs.plugin("datadog").get().pluginId)
            }
        }
    }
}

private fun Project.disableFdroidAnalyticsTasks() {
    plugins.withId("com.google.gms.google-services") {
        tasks.configureEach {
            if (name.contains("GoogleServices", ignoreCase = true) && name.contains("fdroid", ignoreCase = true)) {
                enabled = false
            }
        }
    }

    plugins.withId("com.google.firebase.crashlytics") {
        tasks.configureEach {
            if (name.contains("Crashlytics", ignoreCase = true) && name.contains("fdroid", ignoreCase = true)) {
                enabled = false
            }
        }
    }

    // Disable Datadog analytics/upload tasks for fdroid, but NOT the buildId
    // inject/generate tasks. The Datadog plugin wires InjectBuildIdToAssetsTask via
    // variant.artifacts.toTransform(SingleArtifact.ASSETS), which replaces the merged
    // assets artifact for the entire variant. Disabling that task leaves its output
    // directory empty, causing compressAssets to produce zero files and stripping ALL
    // assets (including Compose Multiplatform .cvr resources) from the release APK.
    plugins.withId("com.datadoghq.dd-sdk-android-gradle-plugin") {
        tasks.configureEach {
            if (
                (name.contains("datadog", ignoreCase = true) || name.contains("uploadMapping", ignoreCase = true)) &&
                name.contains("fdroid", ignoreCase = true)
            ) {
                enabled = false
            }
        }

        // The inject task must stay enabled to maintain the AGP artifact pipeline,
        // but we strip the datadog.buildId file from its output to preserve fdroid
        // sterility — no analytics artifacts should ship in the open-source flavor.
        tasks.withType<InjectBuildIdToAssetsTask>().configureEach {
            if (name.contains("Fdroid", ignoreCase = true)) {
                doLast {
                    val buildIdFile = File(outputAssets.get().asFile, "datadog.buildId")
                    if (buildIdFile.exists()) {
                        buildIdFile.delete()
                    }
                }
            }
        }
    }
}

private fun Project.configureDatadogVariants() {
    extensions.configure<ApplicationAndroidComponentsExtension> {
        onVariants { variant ->
            if (variant.flavorName == "google") {
                extensions.findByType<DdExtension>()?.apply {
                    variants { register(variant.name) { site = "US5" } }
                    checkProjectDependencies = SdkCheckLevel.NONE
                }
            }
        }
    }
}
