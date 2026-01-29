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

import com.android.build.api.dsl.ApplicationExtension
import org.meshtastic.buildlogic.FlavorDimension
import org.meshtastic.buildlogic.MeshtasticFlavor
import org.meshtastic.buildlogic.configProperties

plugins {
    alias(libs.plugins.meshtastic.android.application)
    alias(libs.plugins.meshtastic.android.application.compose)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.serialization)
}

val meshtasticVersion = configProperties.getProperty("VERSION_NAME_BASE")

configure<ApplicationExtension> {
    namespace = "com.meshtastic.android.meshserviceexample"
    defaultConfig {
        // Force this app to use the Google variant of any modules it's using that apply AndroidLibraryConventionPlugin
        missingDimensionStrategy(FlavorDimension.marketplace.name, MeshtasticFlavor.google.name)
    }

    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    implementation("org.meshtastic:core-api:$meshtasticVersion")
    implementation("org.meshtastic:core-model:$meshtasticVersion")
    implementation("org.meshtastic:core-proto:$meshtasticVersion")

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.iconsExtended)
    implementation(libs.material)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
