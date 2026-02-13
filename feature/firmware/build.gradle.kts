/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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

plugins {
    alias(libs.plugins.meshtastic.android.library)
    alias(libs.plugins.meshtastic.android.library.compose)
    alias(libs.plugins.meshtastic.hilt)
    alias(libs.plugins.kover)
}

configure<LibraryExtension> { namespace = "org.meshtastic.feature.firmware" }

dependencies {
    implementation(projects.core.ble)
    implementation(projects.core.common)
    implementation(projects.core.data)
    implementation(projects.core.database)
    implementation(projects.core.datastore)
    implementation(projects.core.model)
    implementation(projects.core.navigation)
    implementation(projects.core.prefs)
    implementation(projects.core.proto)
    implementation(projects.core.service)
    implementation(projects.core.strings)
    implementation(projects.core.ui)

    implementation(libs.accompanist.permissions)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.compose.material.iconsExtended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.text)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.kermit)

    implementation(libs.nordic.client.android)
    implementation(libs.nordic.dfu)
    implementation(libs.coil)
    implementation(libs.coil.network.okhttp)
    implementation(libs.markdown.renderer)
    implementation(libs.markdown.renderer.m3)

    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.nordic.client.android.mock)
    testImplementation(libs.nordic.client.mock)
    testImplementation(libs.nordic.core.android.mock)
    testImplementation(libs.nordic.core.mock)
    testImplementation(libs.mockk)
}
