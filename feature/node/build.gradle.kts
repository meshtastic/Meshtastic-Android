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
    alias(libs.plugins.meshtastic.android.library.flavors)
    alias(libs.plugins.meshtastic.android.library.compose)
    alias(libs.plugins.meshtastic.hilt)
}

configure<LibraryExtension> {
    namespace = "org.meshtastic.feature.node"

    defaultConfig { manifestPlaceholders["MAPS_API_KEY"] = "DEBUG_KEY" }
}

dependencies {
    implementation(projects.core.data)
    implementation(projects.core.database)
    implementation(projects.core.datastore)
    implementation(projects.core.di)
    implementation(projects.core.model)
    implementation(projects.core.proto)
    implementation(projects.core.service)
    implementation(projects.core.strings)
    implementation(projects.core.ui)
    implementation(projects.core.navigation)
    implementation(projects.feature.map)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material.iconsExtended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.text)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.navigation.common)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kermit)
    implementation(libs.coil)
    implementation(libs.markdown.renderer.android)
    implementation(libs.markdown.renderer.m3)
    implementation(libs.markdown.renderer)
    implementation(libs.vico.compose)
    implementation(libs.vico.compose.m2)
    implementation(libs.vico.compose.m3)

    googleImplementation(libs.location.services)
    googleImplementation(libs.maps.compose)
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.robolectric)
}
