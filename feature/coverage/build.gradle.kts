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

// Site Planner coverage computed on the device with org.meshtastic:kp1812 (ITU-R P.1812) over
// Mapterhorn terrain, returned as GeoJSON for the map's layer list.
kotlin {
    jvm()

    // kp1812 has no Android target; Android resolves its jvm artifact.
    @Suppress("UnstableApiUsage")
    android {
        namespace = "org.meshtastic.feature.coverage"
        androidResources.enable = false
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kp1812)
            implementation(libs.kotlinx.coroutines.core)
            implementation(projects.feature.map)
            implementation(projects.feature.mapTerrain)
            implementation(projects.core.common)
            implementation(projects.core.di)
            implementation(libs.okio)
            implementation(libs.ktor.client.core)
        }

        jvmMain.dependencies { implementation(libs.ktor.client.java) }
        androidMain.dependencies { implementation(libs.ktor.client.okhttp) }

        commonTest.dependencies { implementation(libs.kotlinx.coroutines.test) }
        jvmTest.dependencies { implementation(libs.kotlinx.coroutines.test) }
    }
}

// Runs a real prediction against Mapterhorn terrain and writes a PNG and the GeoJSON.
//   ./gradlew :feature:coverage:coverageDemo -PuseMavenLocal
tasks.register<JavaExec>("coverageDemo") {
    group = "verification"
    description = "Compute real coverage from Mapterhorn terrain via kp1812 and render it."
    val jvmMain = kotlin.targets.getByName("jvm").compilations.getByName("main")
    classpath = jvmMain.output.allOutputs + jvmMain.runtimeDependencyFiles!!
    mainClass.set("org.meshtastic.feature.coverage.CoverageDemo")
    args = (providers.gradleProperty("demoArgs").orNull ?: "").split(" ").filter { it.isNotBlank() }
}
