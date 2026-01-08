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
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.datadog) apply false
    alias(libs.plugins.devtools.ksp) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
    alias(libs.plugins.firebase.perf) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.protobuf) apply false
    alias(libs.plugins.secrets) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.kover)
    alias(libs.plugins.spotless) apply false
    alias(libs.plugins.dokka)
    alias(libs.plugins.meshtastic.root)
}



kover {
    reports {
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

                // Suppress generated code
                packages("hilt_aggregated_deps")
                packages("org.meshtastic.core.strings")
            }
        }
    }
}

subprojects {
    // Apply Dokka to all subprojects to ensure they are available for aggregation
    apply(plugin = "org.jetbrains.dokka")

    dokka {
        dokkaSourceSets.configureEach {
            perPackageOption {
                matchingRegex.set("hilt_aggregated_deps")
                suppress.set(true)
            }
            perPackageOption {
                matchingRegex.set("org.meshtastic.core.strings.*")
                suppress.set(true)
            }
            listOf("java", "kotlin").forEach { lang ->
                val dir = file("src/main/$lang")
                if (dir.exists()) {
                    sourceLink {
                        enableJdkDocumentationLink.set(true)
                        enableKotlinStdLibDocumentationLink.set(true)
                        reportUndocumented.set(true)
                        localDirectory.set(dir)

                        val relativePath = project.projectDir.relativeTo(rootProject.projectDir).path.replace("\\", "/")
                        remoteUrl("https://github.com/meshtastic/Meshtastic-Android/blob/main/$relativePath/src/main/$lang")
                        remoteLineSuffix.set("#L")
                    }
                }
            }
        }
    }
}

dependencies {
    kover(projects.app)

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

    dokka(project(":app"))
    dokka(project(":core:analytics"))
    dokka(project(":core:common"))
    dokka(project(":core:data"))
    dokka(project(":core:database"))
    dokka(project(":core:datastore"))
    dokka(project(":core:di"))
    dokka(project(":core:model"))
    dokka(project(":core:navigation"))
    dokka(project(":core:network"))
    dokka(project(":core:prefs"))
    dokka(project(":core:proto"))
    dokka(project(":core:service"))
    dokka(project(":core:ui"))
    dokka(project(":feature:intro"))
    dokka(project(":feature:messaging"))
    dokka(project(":feature:map"))
    dokka(project(":feature:node"))
    dokka(project(":feature:settings"))
}

dokka {
    moduleName.set("Meshtastic App")
    dokkaPublications.html {
        suppressInheritedMembers.set(true)
    }
}


