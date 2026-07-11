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
    alias(libs.plugins.meshtastic.android.library.flavors)
    alias(libs.plugins.meshtastic.koin)
    // Version-less on purpose: mokkery is embedded in the convention-plugin jar (build-logic
    // `implementation`), so a versioned alias(libs.plugins.mokkery) request is rejected by Gradle.
    id("dev.mokkery")
}

android {
    namespace = "org.meshtastic.feature.car"

    buildFeatures { buildConfig = true }

    defaultConfig {
        minSdk = 23
        consumerProguardFiles("proguard-rules.pro")
        // The CarAppService is disabled unless -PenableCarTemplates=true. feature:car resolves the
        // placeholder in its OWN manifest, so it must read the property directly (the app's value does
        // not override a library's own placeholder). Default false → production ships it disabled.
        manifestPlaceholders["carTemplatesEnabled"] =
            providers.gradleProperty("enableCarTemplates").map { it.toBoolean() }.getOrElse(false).toString()
    }

    // Robolectric provides the Android context that androidx.car.app TestCarContext/ScreenController need.
    testOptions { unitTests { isIncludeAndroidResources = true } }
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.data)
    implementation(projects.core.database)
    implementation(projects.core.domain)
    implementation(projects.core.model)
    implementation(projects.core.repository)

    implementation(libs.androidx.car.app)
    implementation(libs.androidx.car.app.projected)

    implementation(libs.koin.android)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)
    implementation(libs.kermit)

    testImplementation(libs.androidx.car.app.testing)
    testImplementation(libs.koin.test)
    testImplementation(libs.robolectric)
    testImplementation(kotlin("test-junit"))
    testRuntimeOnly(libs.junit.vintage.engine)
}
