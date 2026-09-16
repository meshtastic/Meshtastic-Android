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
kotlin {
    jvm()

    // kp1812 publishes no androidTarget - Android resolves its jvm artifact, the same way this
    // repo already consumes takpacket-sdk-jvm.
    @Suppress("UnstableApiUsage")
    android {
        namespace = "org.meshtastic.feature.coverage"
        androidResources.enable = false
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kp1812)
            implementation(libs.kotlinx.coroutines.core)
            // Elevation comes from the same Mapterhorn archives the map already uses for hillshade
            // and contours. Over flat synthetic ground a coverage plot is a bullseye and proves
            // nothing; against real terrain it has to show ridges shadowing valleys.
            implementation(projects.feature.mapTerrain)
        }

        commonTest.dependencies { implementation(libs.kotlinx.coroutines.test) }
        jvmTest.dependencies { implementation(libs.kotlinx.coroutines.test) }
    }
}

// SPIKE: run a real prediction and write a PNG + GeoJSON, so the replacement can be *seen*.
//   ./gradlew :feature:coverage:coverageDemo -PuseMavenLocal
tasks.register<JavaExec>("coverageDemo") {
    group = "verification"
    description = "Compute real coverage from Mapterhorn terrain via kp1812 and render it."
    val jvmMain = kotlin.targets.getByName("jvm").compilations.getByName("main")
    classpath = jvmMain.output.allOutputs + jvmMain.runtimeDependencyFiles!!
    mainClass.set("org.meshtastic.feature.coverage.CoverageDemo")
    args = (providers.gradleProperty("demoArgs").orNull ?: "").split(" ").filter { it.isNotBlank() }
}
