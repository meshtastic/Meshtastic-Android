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
import com.android.build.api.dsl.LibraryExtension

plugins {
    alias(libs.plugins.meshtastic.android.library)
    alias(libs.plugins.meshtastic.android.library.flavors)
    alias(libs.plugins.meshtastic.android.library.compose)
    alias(libs.plugins.meshtastic.hilt)
    alias(libs.plugins.secrets)
    alias(libs.plugins.kover)
}

dependencies {
    implementation(projects.core.prefs)

    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.runtime)
    implementation(libs.kermit)

    googleApi(libs.dd.sdk.android.compose)
    googleApi(libs.dd.sdk.android.logs)
    googleApi(libs.dd.sdk.android.rum)
    googleApi(libs.dd.sdk.android.session.replay)
    googleApi(libs.dd.sdk.android.session.replay.compose)
    googleApi(libs.dd.sdk.android.timber)
    googleApi(libs.dd.sdk.android.trace)
    googleApi(libs.dd.sdk.android.trace.otel)
    googleApi(platform(libs.firebase.bom))
    googleApi(libs.firebase.analytics)
    googleApi(libs.firebase.crashlytics)
}

configure<LibraryExtension> {
    buildFeatures { buildConfig = true }
    namespace = "org.meshtastic.core.analytics"
}

secrets {
    defaultPropertiesFileName = "secrets.defaults.properties"
    propertiesFileName = "secrets.properties"
}
