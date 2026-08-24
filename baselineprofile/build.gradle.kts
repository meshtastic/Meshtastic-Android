/*
 * Copyright (c) 2026 Meshtastic LLC
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
    alias(libs.plugins.android.test)
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "org.meshtastic.baselineprofile"
    compileSdk = 37

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    defaultConfig {
        // Macrobenchmark / BaselineProfileRule require API 28+ on the test (device) side.
        // The generated profile is still installed on the app's real minSdk (26) via profileinstaller.
        minSdk = 28
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // App module whose startup we profile/benchmark.
    targetProjectPath = ":androidApp"

    // The app declares a `marketplace` flavor dimension (google / fdroid). A test module must
    // match it. We pin to `google` — the variant the vast majority of users run (and the one with
    // Maps). f-droid can reuse the same profile; wire a second flavor here if it ever diverges.
    flavorDimensions += "marketplace"
    productFlavors { create("google") { dimension = "marketplace" } }
}

kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_21) } }

baselineProfile {
    // Generate on an attached device/emulator. For hermetic CI, replace with a Gradle Managed
    // Device (see README.md) and set managedDevices + useConnectedDevices = false.
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.espresso.core)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
