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

plugins {
    alias(libs.plugins.meshtastic.kmp.library)
    alias(libs.plugins.meshtastic.kmp.library.compose)
    alias(libs.plugins.meshtastic.kotlinx.serialization)
    alias(libs.plugins.meshtastic.koin)
}

kotlin {
    jvm()

    android {
        namespace = "org.meshtastic.feature.settings"
        androidResources.enable = false
        withHostTest { isIncludeAndroidResources = true }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(projects.core.common)
            implementation(projects.core.data)
            implementation(projects.core.database)
            implementation(projects.core.datastore)
            implementation(projects.core.domain)
            implementation(projects.core.model)
            implementation(projects.core.navigation)
            implementation(projects.core.proto)
            implementation(projects.core.repository)
            implementation(projects.core.service)
            implementation(projects.core.resources)
            implementation(projects.core.ui)
            implementation(projects.core.di)

            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.kermit)
            implementation(libs.kotlinx.collections.immutable)
            implementation(libs.aboutlibraries.compose.m3)
        }

        androidMain.dependencies {
            implementation(projects.core.barcode)
            implementation(projects.core.nfc)
            implementation(project.dependencies.platform(libs.androidx.compose.bom))
            implementation(libs.accompanist.permissions)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.appcompat)
            implementation(libs.androidx.compose.material.iconsExtended)
            implementation(libs.androidx.compose.material3)
            implementation(libs.androidx.compose.ui.text)
            implementation(libs.androidx.compose.ui.tooling.preview)

            implementation(libs.coil)
            implementation(libs.markdown.renderer.android)
            implementation(libs.markdown.renderer.m3)
            implementation(libs.markdown.renderer)
            implementation(libs.nordic.common.core)
            implementation(libs.nordic.common.permissions.ble)
        }

        commonTest.dependencies { implementation(projects.core.testing) }

        androidUnitTest.dependencies {
            implementation(libs.junit)
            implementation(libs.mockk)
            implementation(libs.robolectric)
            implementation(libs.turbine)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.androidx.compose.ui.test.junit4)
            implementation(libs.androidx.test.ext.junit)
        }
    }
}

val marketplaceAttr = Attribute.of("com.android.build.api.attributes.ProductFlavor:marketplace", String::class.java)

configurations.all {
    if (isCanBeResolved && !isCanBeConsumed) {
        if (name.contains("android", ignoreCase = true)) {
            attributes.attribute(marketplaceAttr, "google")
        }
    }
}
