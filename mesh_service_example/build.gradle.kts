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
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose)
    alias(libs.plugins.protobuf)
    alias(libs.plugins.detekt)
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
    implementation(libs.findLibrary("appcompat").get())
    implementation(libs.findLibrary("material").get())
    implementation(libs.findLibrary("activity").get())
    implementation(libs.findLibrary("constraintlayout").get())
    testImplementation(libs.findLibrary("junit").get())
    androidTestImplementation(libs.findLibrary("ext.junit").get())
    androidTestImplementation(libs.findLibrary("espresso.core").get())

    implementation(libs.findBundle("androidx").get())
    implementation(libs.findBundle("protobuf").get())

    implementation(libs.findLibrary("kotlinx.serialization.json").get())

    // OSM
    implementation(libs.findBundle("osm").get() )
    implementation(libs.findLibrary("osmdroid.geopackage").get()) {
        exclude(group = "com.j256.ormlite")
    }
    detektPlugins(libs.findLibrary("detekt.formatting").get())
}

detekt {
    config.setFrom("../config/detekt/detekt.yml")
    baseline = file("../config/detekt/detekt-baseline-meshserviceexample.xml")
}
