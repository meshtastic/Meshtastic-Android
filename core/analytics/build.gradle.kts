/*
 * Copyright (c) 2025 Meshtastic LLC
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
    alias(libs.plugins.meshtastic.hilt)
    alias(libs.plugins.secrets)
    alias(libs.plugins.kover)
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.prefs)

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.timber)

    googleImplementation(platform(libs.firebase.bom))
    googleImplementation(libs.firebase.analytics)
    googleImplementation(libs.firebase.crashlytics)
    googleImplementation(libs.bundles.datadog)
}

val googleServiceKeywords = listOf("crashlytics", "google", "datadog")

tasks.configureEach {
    if (
        googleServiceKeywords.any { name.contains(it, ignoreCase = true) } && name.contains("fdroid", ignoreCase = true)
    ) {
        project.logger.lifecycle("Disabling task for F-Droid: $name")
        enabled = false
    }
}

android {
    buildFeatures { buildConfig = true }
    namespace = "org.meshtastic.core.analytics"
}

secrets {
    defaultPropertiesFileName = "secrets.defaults.properties"
    propertiesFileName = "secrets.properties"
}
