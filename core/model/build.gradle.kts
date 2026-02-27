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
    @Suppress("UnstableApiUsage")
    android { androidResources.enable = false }

    sourceSets {
        commonMain.dependencies {
            api(projects.core.proto)
            api(projects.core.common)

            api(libs.kotlinx.serialization.json)
            api(libs.kotlinx.datetime)
            implementation(libs.kermit)
            api(libs.okio)
        }
        androidMain.dependencies {
            api(libs.androidx.annotation)
            implementation(libs.zxing.core)
        }
        commonTest.dependencies { implementation(kotlin("test")) }
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
