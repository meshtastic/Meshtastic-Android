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

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
    alias(libs.plugins.android.lint)
    alias(libs.plugins.dependency.analysis)
}

group = "com.geeksville.mesh.buildlogic"

// Configure the build-logic plugins to target JDK 21
// This matches the JDK used to build the project, and is not related to what is running on device.
java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
    }
}

dependencies {
    compileOnly(libs.android.gradleApiPlugin)
    compileOnly(libs.serialization.gradlePlugin)
    compileOnly(libs.android.tools.common)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.datadog.gradlePlugin)
    compileOnly(libs.detekt.gradlePlugin)
    compileOnly(libs.firebase.crashlytics.gradlePlugin)
    compileOnly(libs.google.services.gradlePlugin)
    compileOnly(libs.hilt.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.androidx.room.gradlePlugin)
    compileOnly(libs.secrets.gradlePlugin)
    compileOnly(libs.spotless.gradlePlugin)
    compileOnly(libs.truth)
}

tasks {
    validatePlugins {
        enableStricterValidation = true
        failOnWarning = true
    }
}


gradlePlugin {
    plugins {
        register("androidApplication") {
            id = libs.plugins.meshtastic.android.application.asProvider().get().pluginId
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidFlavors") {
            id = libs.plugins.meshtastic.android.application.flavors.get().pluginId
            implementationClass = "AndroidApplicationFlavorsConventionPlugin"
        }
        register("androidLibrary") {
            id = libs.plugins.meshtastic.android.library.asProvider().get().pluginId
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidLint") {
            id = libs.plugins.meshtastic.android.lint.get().pluginId
            implementationClass = "AndroidLintConventionPlugin"
        }
        register("androidLibraryCompose") {
            id = libs.plugins.meshtastic.android.library.compose.get().pluginId
            implementationClass = "AndroidLibraryComposeConventionPlugin"
        }
        register("androidApplicationCompose") {
            id = libs.plugins.meshtastic.android.application.compose.get().pluginId
            implementationClass = "AndroidApplicationComposeConventionPlugin"
        }
        register("kotlinXSerialization") {
            id = libs.plugins.meshtastic.kotlinx.serialization.get().pluginId
            implementationClass = "KotlinXSerializationConventionPlugin"
        }
        register("meshtasticHilt") {
            id = libs.plugins.meshtastic.hilt.get().pluginId
            implementationClass = "HiltConventionPlugin"
        }
        register("meshtasticDetekt") {
            id = libs.plugins.meshtastic.detekt.get().pluginId
            implementationClass = "DetektConventionPlugin"
        }
        register("androidRoom") {
            id = libs.plugins.meshtastic.android.room.get().pluginId
            implementationClass = "AndroidRoomConventionPlugin"
        }

        register("meshtasticSpotless") {
            id = libs.plugins.meshtastic.spotless.get().pluginId
            implementationClass = "SpotlessConventionPlugin"
        }

        register("kmpLibrary") {
            id = libs.plugins.meshtastic.kmp.library.get().pluginId
            implementationClass = "KmpLibraryConventionPlugin"
        }

    }
}
