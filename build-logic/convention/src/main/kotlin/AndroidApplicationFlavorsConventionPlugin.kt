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
import com.geeksville.mesh.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.exclude

class AndroidApplicationFlavorsConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            extensions.configure<ApplicationExtension> {
                configureFlavors(this)
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
        }
    }
}
