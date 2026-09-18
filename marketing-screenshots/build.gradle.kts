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
import com.android.build.api.dsl.LibraryExtension

// SPIKE (throwaway): framed Play Store screenshots via io.github.lucianosantosdev.storescreenshots.
// Plain Android library, no product flavors (the plugin does not support them). Output stays at the
// plugin default build/outputs/store-screenshots so nothing under fastlane/ changes.
plugins {
    alias(libs.plugins.meshtastic.android.library)
    alias(libs.plugins.meshtastic.android.library.compose)
    alias(libs.plugins.store.screenshots)
}

configure<LibraryExtension> { namespace = "org.meshtastic.screenshot.marketing" }

dependencies {
    implementation(projects.core.ui)
    implementation(projects.core.resources)
    implementation(projects.core.model)
    implementation(projects.core.common)
    implementation(libs.compose.multiplatform.foundation)
    implementation(libs.compose.multiplatform.material3)
    implementation(libs.compose.multiplatform.runtime)
    implementation(libs.compose.multiplatform.ui)
    implementation(libs.compose.multiplatform.resources)

    // Catalog's 4.17 wins over the plugin library's transitive 4.16.1.
    testImplementation(libs.robolectric)
    testImplementation(libs.junit)
    // configureTestOptions() turns on useJUnitPlatform(); the plugin's base class is JUnit 4.
    testRuntimeOnly(libs.junit.vintage.engine)
}
