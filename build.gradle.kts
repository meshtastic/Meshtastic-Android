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



plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.devtools.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
    alias(libs.plugins.ktorfit) apply false
    alias(libs.plugins.protobuf) apply false
    alias(libs.plugins.dependency.analysis)
    alias(libs.plugins.kover)
}



kover {
    reports {
        total {
            filters {
                excludes {
                    // Exclude generated classes
                    classes("*_Impl")
                    classes("*Binding")
                    classes("*Factory")
                    classes("*.BuildConfig")
                    classes("*.R")
                    classes("*.R$*")

                    // Exclude UI components
                    annotatedBy("*Preview")

                    // Exclude declarations
                    annotatedBy(
                        "*.HiltAndroidApp",
                        "*.AndroidEntryPoint",
                        "*.Module",
                        "*.Provides",
                        "*.Binds",
                        "*.Composable",
                    )
                }
            }
        }
    }
}

dependencies {
    kover(projects.app)
    kover(projects.meshServiceExample)

    kover(projects.core.analytics)
    kover(projects.core.common)
    kover(projects.core.data)
    kover(projects.core.datastore)
    kover(projects.core.model)
    kover(projects.core.navigation)
    kover(projects.core.network)
    kover(projects.core.prefs)
    kover(projects.feature.intro)
    kover(projects.feature.map)
    kover(projects.feature.node)
    kover(projects.feature.settings)
}