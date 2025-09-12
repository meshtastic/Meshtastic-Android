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
    alias(libs.plugins.meshtastic.android.library)
//    alias(libs.plugins.hilt)
    alias(libs.plugins.devtools.ksp)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.dokka)
    alias(libs.plugins.kover)
    alias(libs.plugins.protobuf)
}

android {
    buildFeatures {
        buildConfig = true
    }
    compileSdk = 36
    defaultConfig {
        minSdk = 26
    }

    namespace = "com.geeksville.mesh.network"
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.findBundle("hilt").get())
    implementation(libs.findBundle("retrofit").get())
    implementation(libs.findBundle("coil").get())
    implementation(libs.findBundle("protobuf").get())
    "googleImplementation"(libs.findBundle("datadog").get())
    ksp(libs.findLibrary("hilt.compiler").get())
    implementation(libs.findLibrary("kotlinx.serialization.json").get())
    detektPlugins(libs.findLibrary("detekt.formatting").get())
}

detekt {
    config.setFrom("../config/detekt/detekt.yml")
    baseline = file("../config/detekt/detekt-baseline-network.xml")
    source.setFrom(
        files(
            "src/main/java",
            "google/main/java",
            "fdroid/main/java",
        )
    )
}
