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
import com.geeksville.mesh.buildlogic.libs
import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.exclude

class AndroidApplicationFirebaseConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = libs.findPlugin("firebase-crashlytics").get().get().pluginId)
            apply(plugin = libs.findPlugin("firebase-perf").get().get().pluginId)
            apply(plugin = libs.findPlugin("google-services").get().get().pluginId)
            extensions.configure<ApplicationExtension> {
                dependencies {
                    val bom = libs.findLibrary("firebase-bom").get()
                    "googleImplementation"(platform(bom))
                    "googleImplementation"(libs.findBundle("firebase").get()) {
                        /*
                        Exclusion of protobuf / protolite dependencies is necessary as the
                        datastore-proto brings in protobuf dependencies. These are the source of truth
                        for Now in Android.
                        That's why the duplicate classes from below dependencies are excluded.
                        */
                        exclude(group = "com.google.protobuf", module = "protobuf-java")
                        exclude(group = "com.google.protobuf", module = "protobuf-kotlin")
                        exclude(group = "com.google.protobuf", module = "protobuf-javalite")
                        exclude(
                            group = "com.google.firebase",
                            module = "protolite-well-known-types"
                        )
                    }
                }
            }

            extensions.configure<ApplicationExtension> {
                buildTypes.configureEach {
                    // Disable the Crashlytics mapping file upload. This feature should only be
                    // enabled if a Firebase backend is available and configured in
                    // google-services.json.
                    configure<CrashlyticsExtension> {
                        mappingFileUploadEnabled = false
                    }
                }
            }
        }
    }
}
