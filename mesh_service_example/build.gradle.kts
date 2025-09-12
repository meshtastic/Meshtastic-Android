import org.gradle.kotlin.dsl.androidTestImplementation

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
    alias(libs.plugins.meshtastic.android.application)
    alias(libs.plugins.meshtastic.android.application.compose)
    alias(libs.plugins.meshtastic.android.lint)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.protobuf)
    alias(libs.plugins.spotless)
    alias(libs.plugins.kover)
}

android {
    namespace = "com.meshtastic.android.meshserviceexample"
    buildFeatures {
        aidl = true
    }
}

kotlin {
    jvmToolchain(21)
}

// per protobuf-gradle-plugin docs, this is recommended for android
protobuf {
    protoc {
        protoc { artifact = "com.google.protobuf:protoc:4.32.0" }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                create("java")
                create("kotlin")
            }
        }
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    implementation(libs.bundles.androidx)
    implementation(libs.bundles.protobuf)

    implementation(libs.kotlinx.serialization.json)

    // OSM
    implementation(libs.bundles.osm)
    implementation(libs.osmdroid.geopackage) {
        exclude(group = "com.j256.ormlite")
    }
}
