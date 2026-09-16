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
plugins { alias(libs.plugins.meshtastic.kmp.feature) }

// SPIKE: local RF coverage, replacing the headless-WebView hand-off to the hosted Site Planner.
// Pure computation over org.meshtastic:kp1812 (ITU-R P.1812) and an ElevationSource — no Compose
// UI, no rendering, no network. The app supplies elevation from feature/map-terrain's Mapterhorn
// tiles; tests supply a lambda.
// SPIKE SCOPE: jvm() only. kp1812 publishes no androidTarget (Android is meant to consume its
// jvm artifact, the same choice kzstd makes), and proving that resolution path is a separate
// question from proving the model works. Desktop is also where the current experience is worst:
// it cannot run the WebView at all, so today it opens a browser and asks the user to export and
// re-import a file by hand.
kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kp1812)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies { implementation(libs.kotlinx.coroutines.test) }
    }
}
