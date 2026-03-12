/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
    alias(libs.plugins.meshtastic.kmp.library)
    alias(libs.plugins.meshtastic.kotlinx.serialization)
    alias(libs.plugins.kotlin.parcelize)
    `maven-publish`
}

apply(from = rootProject.file("gradle/publishing.gradle.kts"))

kotlin {
    jvm()

    @Suppress("UnstableApiUsage")
    android {
        androidResources.enable = false
        withHostTest { isIncludeAndroidResources = true }
        withDeviceTest { instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
    }

    sourceSets {
        // Intermediate source set for code shared between Android and JVM targets
        // (both are JVM-based). Use this for implementations that need java.* APIs
        // but are identical across both targets.
        val jvmAndroidMain by creating { dependsOn(commonMain.get()) }
        androidMain.get().dependsOn(jvmAndroidMain)
        jvmMain.get().dependsOn(jvmAndroidMain)

        commonMain.dependencies {
            api(projects.core.proto)
            api(projects.core.common)
            api(projects.core.resources)

            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.serialization.json)
            api(libs.kotlinx.datetime)
            implementation(libs.kermit)
            api(libs.okio)
            api(libs.compose.multiplatform.resources)
        }
        androidMain.dependencies {
            api(libs.androidx.annotation)
            api(libs.androidx.core.ktx)
            implementation(libs.zxing.core)
        }
        commonTest.dependencies { implementation(kotlin("test")) }
        val androidHostTest by getting {
            dependencies {
                implementation(libs.junit)
                implementation(libs.robolectric)
                implementation(libs.mockk)
                implementation(libs.androidx.test.ext.junit)
                implementation(kotlin("test"))
            }
        }
        val androidDeviceTest by getting {
            dependencies {
                implementation(libs.androidx.test.ext.junit)
                implementation(libs.androidx.test.runner)
            }
        }
    }
}

// Modern KMP publication uses the project name as the artifactId by default.
// We rename the publications to include the 'core-' prefix for consistency.
publishing {
    publications.withType<MavenPublication>().configureEach {
        val baseId = artifactId
        if (baseId == "model") {
            artifactId = "meshtastic-android-model"
        } else if (baseId.startsWith("model-")) {
            artifactId = baseId.replace("model-", "meshtastic-android-model-")
        }
    }
}
