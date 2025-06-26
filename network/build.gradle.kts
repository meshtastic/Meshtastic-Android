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
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.devtools.ksp)
    alias(libs.plugins.detekt)
    id("kotlinx-serialization")
}

android {
    buildFeatures {
        buildConfig = true
    }
    compileSdk = Configs.COMPILE_SDK
    defaultConfig {
        minSdk = Configs.MIN_SDK_VERSION
    }

    namespace = "com.geeksville.mesh.network"

    flavorDimensions += "default"
    productFlavors {
        create("fdroid") {
            dimension = "default"
        }
        create("google") {
            dimension = "default"
        }
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.bundles.hilt)
    implementation(libs.bundles.retrofit)
    implementation(libs.bundles.coil)
    ksp(libs.hilt.compiler)
    implementation(libs.kotlinx.serialization.json)
    detektPlugins(libs.detekt.formatting)
}

detekt {
    config.setFrom("../config/detekt/detekt.yml")
    baseline = file("../config/detekt/detekt-baseline-network.xml")
}
