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
//    compileOnly(libs.android.gradleApiPlugin)
//    compileOnly(libs.android.tools.common)
//    compileOnly(libs.compose.gradlePlugin)
//    compileOnly(libs.firebase.crashlytics.gradlePlugin)
//    compileOnly(libs.firebase.performance.gradlePlugin)
//    compileOnly(libs.kotlin.gradlePlugin)
//    compileOnly(libs.ksp.gradlePlugin)
//    compileOnly(libs.room.gradlePlugin)
//    implementation(libs.truth)
//    lintChecks(libs.androidx.lint.gradle)
}

//tasks {
//    validatePlugins {
//        enableStricterValidation = true
//        failOnWarning = true
//    }
//}

gradlePlugin {
    plugins {
//        register("androidApplicationCompose") {
//            id = libs.plugins.nowinandroid.android.application.compose.get().pluginId
//            implementationClass = "AndroidApplicationComposeConventionPlugin"
//        }
//        register("androidApplication") {
//            id = libs.plugins.nowinandroid.android.application.asProvider().get().pluginId
//            implementationClass = "AndroidApplicationConventionPlugin"
//        }
//        register("androidApplicationJacoco") {
//            id = libs.plugins.nowinandroid.android.application.jacoco.get().pluginId
//            implementationClass = "AndroidApplicationJacocoConventionPlugin"
//        }
//        register("androidLibraryCompose") {
//            id = libs.plugins.nowinandroid.android.library.compose.get().pluginId
//            implementationClass = "AndroidLibraryComposeConventionPlugin"
//        }
//        register("androidLibrary") {
//            id = libs.plugins.nowinandroid.android.library.asProvider().get().pluginId
//            implementationClass = "AndroidLibraryConventionPlugin"
//        }
//        register("androidFeature") {
//            id = libs.plugins.nowinandroid.android.feature.get().pluginId
//            implementationClass = "AndroidFeatureConventionPlugin"
//        }
//        register("androidLibraryJacoco") {
//            id = libs.plugins.nowinandroid.android.library.jacoco.get().pluginId
//            implementationClass = "AndroidLibraryJacocoConventionPlugin"
//        }
//        register("androidTest") {
//            id = libs.plugins.nowinandroid.android.test.get().pluginId
//            implementationClass = "AndroidTestConventionPlugin"
//        }
//        register("hilt") {
//            id = libs.plugins.nowinandroid.hilt.get().pluginId
//            implementationClass = "HiltConventionPlugin"
//        }
//        register("androidRoom") {
//            id = libs.plugins.nowinandroid.android.room.get().pluginId
//            implementationClass = "AndroidRoomConventionPlugin"
//        }
//        register("androidFirebase") {
//            id = libs.plugins.nowinandroid.android.application.firebase.get().pluginId
//            implementationClass = "AndroidApplicationFirebaseConventionPlugin"
//        }
//        register("androidFlavors") {
//            id = libs.plugins.nowinandroid.android.application.flavors.get().pluginId
//            implementationClass = "AndroidApplicationFlavorsConventionPlugin"
//        }
//        register("androidLint") {
//            id = libs.plugins.nowinandroid.android.lint.get().pluginId
//            implementationClass = "AndroidLintConventionPlugin"
//        }
//        register("jvmLibrary") {
//            id = libs.plugins.nowinandroid.jvm.library.get().pluginId
//            implementationClass = "JvmLibraryConventionPlugin"
//        }
    }
}