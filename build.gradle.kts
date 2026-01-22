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
        classpath("com.android.tools.build:gradle:8.1.2")
        classpath("com.google.dagger:hilt-android-gradle-plugin:2.58")
    }
}

plugins {
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.datadog) apply false
    alias(libs.plugins.devtools.ksp) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
    alias(libs.plugins.firebase.perf) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.protobuf) apply false
    alias(libs.plugins.secrets) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.kover)
    alias(libs.plugins.spotless) apply false
    alias(libs.plugins.dokka)
    // Meshtastic root plugin is a convention plugin from build-logic
    id("meshtastic.root")
}



dependencies {
    dokkaPlugin(libs.dokka.android.documentation.plugin)
}