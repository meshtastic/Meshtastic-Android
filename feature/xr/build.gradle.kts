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

plugins {
    alias(libs.plugins.meshtastic.android.library)
    alias(libs.plugins.meshtastic.android.library.compose)
}

android {
    namespace = "org.meshtastic.feature.xr"

    defaultConfig { minSdk = 26 }
}

dependencies {
    implementation(projects.core.repository)
    implementation(projects.core.resources)
    implementation(projects.core.ui)
    implementation(projects.feature.mapTerrain)
    implementation(projects.feature.node)

    implementation(libs.androidx.activity.compose)
    implementation(libs.kermit)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)

    implementation(libs.compose.multiplatform.ui)
    implementation(libs.compose.multiplatform.foundation)
    implementation(libs.compose.multiplatform.material3)
    implementation(libs.compose.multiplatform.resources)

    // Pulls ARCore and a prebuilt native renderer transitively, so only the google flavor depends on this module.
    implementation(libs.androidx.xr.compose)
    implementation(libs.androidx.xr.runtime)
    implementation(libs.androidx.xr.scenecore)

    testImplementation(kotlin("test-junit"))
    // configureTestOptions() turns on useJUnitPlatform(), so the vintage engine is what runs JUnit 4 tests.
    testRuntimeOnly(libs.junit.vintage.engine)
}
