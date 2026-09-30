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

// Captures the store-listing screenshots from the real debug app on a device or emulator:
//   ./gradlew :store-screenshots:connectedGoogleDebugAndroidTest   (Play)
//   ./gradlew :store-screenshots:connectedFdroidDebugAndroidTest   (F-Droid, IzzyOnDroid)
// PNGs are left on the device in /data/local/tmp/store-screenshots/<flavor>, laid out like fastlane's images/; pull
// them with
//   adb pull /data/local/tmp/store-screenshots/<flavor>/. <dir>
// .github/workflows/store-screenshots.yml runs each flavor on its own emulator through capture-android.sh.
plugins {
    alias(libs.plugins.meshtastic.android.test)
    alias(libs.plugins.meshtastic.detekt)
    alias(libs.plugins.meshtastic.spotless)
}

android {
    namespace = "org.meshtastic.storescreenshots"

    defaultConfig { minSdk = 28 }

    targetProjectPath = ":androidApp"
    // Its own process, so relaunching the app between form factors does not end the run.
    experimentalProperties["android.experimental.self-instrumenting"] = true

    flavorDimensions += "marketplace"
    productFlavors {
        create("google") {
            dimension = "marketplace"
            testInstrumentationRunnerArguments["targetAppId"] = "com.geeksville.mesh.google.debug"
            testInstrumentationRunnerArguments["flavor"] = "google"
        }
        create("fdroid") {
            dimension = "marketplace"
            testInstrumentationRunnerArguments["targetAppId"] = "com.geeksville.mesh.fdroid.debug"
            testInstrumentationRunnerArguments["flavor"] = "fdroid"
        }
    }
}

// Both flavors can capture on one device in one invocation; each run resizes that device's display.
tasks
    .named { it == "connectedFdroidDebugAndroidTest" }
    .configureEach { mustRunAfter("connectedGoogleDebugAndroidTest") }

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.uiautomator)
}
