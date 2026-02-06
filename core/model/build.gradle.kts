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
import com.android.build.api.dsl.LibraryExtension

plugins {
    alias(libs.plugins.meshtastic.android.library)
    alias(libs.plugins.meshtastic.kotlinx.serialization)
    alias(libs.plugins.kotlin.parcelize)
    `maven-publish`
}

apply(from = rootProject.file("gradle/publishing.gradle.kts"))

configure<LibraryExtension> {
    namespace = "org.meshtastic.core.model"
    buildFeatures {
        buildConfig = true
        aidl = true
    }

    defaultConfig {
        // Lowering minSdk to 21 for better compatibility with ATAK and other plugins
        minSdk = 21
    }

    testOptions { unitTests { isIncludeAndroidResources = true } }

    publishing { singleVariant("release") { withSourcesJar() } }
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                artifactId = "core-model"
            }
        }
    }
}

dependencies {
    api(projects.core.proto)

    api(libs.androidx.annotation)
    api(libs.kotlinx.serialization.json)
    implementation(libs.kermit)
    implementation(libs.zxing.core)

    testImplementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
