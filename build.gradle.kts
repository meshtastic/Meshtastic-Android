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

buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath(libs.agp)
        classpath(libs.kotlin.gradle.plugin)
        classpath(libs.kotlin.serialization)
        classpath(libs.google.services)
        classpath(libs.firebase.crashlytics.gradle)
        classpath(libs.protobuf.gradle.plugin)
        classpath(libs.hilt.android.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.devtools.ksp) apply false
    alias(libs.plugins.compose) apply false
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

tasks.register<Delete>("clean") {
    delete(layout.buildDirectory)
}
