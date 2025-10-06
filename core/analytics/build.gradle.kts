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
    implementation(project(":core:prefs"))
    implementation(project(":core:model"))
    implementation(libs.timber)
    implementation(libs.appcompat)
    implementation(libs.hilt.lifecycle.viewmodel.compose)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.process)
    googleImplementation(platform(libs.firebase.bom))
    googleImplementation(libs.bundles.firebase) {
        /*
        Exclusion of protobuf / protolite dependencies is necessary as the
        datastore-proto brings in protobuf dependencies. These are the source of truth
        for Now in Android.
        That's why the duplicate classes from below dependencies are excluded.
         */
        exclude(group = "com.google.protobuf", module = "protobuf-java")
        exclude(group = "com.google.protobuf", module = "protobuf-kotlin")
        exclude(group = "com.google.protobuf", module = "protobuf-javalite")
        exclude(group = "com.google.firebase", module = "protolite-well-known-types")
    }
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
