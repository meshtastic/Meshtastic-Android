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
    alias(libs.plugins.compose) apply false
    alias(libs.plugins.datadog) apply false
    alias(libs.plugins.devtools.ksp) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
    alias(libs.plugins.firebase.perf) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ktorfit) apply false
    alias(libs.plugins.protobuf) apply false
    alias(libs.plugins.secrets) apply false
    alias(libs.plugins.dependency.analysis)
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.kover)
    alias(libs.plugins.spotless) apply false
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
    kover(projects.core.ui)
    kover(projects.feature.intro)
    kover(projects.feature.messaging)
    kover(projects.feature.map)
    kover(projects.feature.node)
    kover(projects.feature.settings)
}

dependencyAnalysis {
    structure {
        ignoreKtx(true)

        // Hilt Android is required by the Hilt plugin, but isn't directly used in many cases. Group
        // these dependencies together so warnings aren't triggered. If neither of these are being
        // used, the module likely shouldn't be applying the Hilt plugin.
        bundle("hilt-core") {
            includeDependency("com.google.dagger:hilt-core")
            includeDependency(libs.hilt.android)
        }

        bundle("ktorfit") {
            includeDependency("de.jensklingenberg.ktorfit:ktorfit-lib")
            includeDependency("de.jensklingenberg.ktorfit:ktorfit-annotations")
        }
    }

    issues {
        all {
            onUnusedDependencies {
                severity("fail")
                exclude("androidx.compose.ui:ui-test-manifest")
            }
        }
    }
}