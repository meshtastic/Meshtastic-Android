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

    @Suppress("UnstableApiUsage")
    android {
        namespace = "org.meshtastic.feature.map"
        androidResources.enable = false
        withHostTest { isIncludeAndroidResources = true }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.common)
            implementation(projects.core.data)
            implementation(projects.core.database)
            implementation(projects.core.datastore)
            implementation(projects.core.model)
            implementation(projects.core.navigation)
            implementation(projects.core.prefs)
            implementation(projects.core.proto)
            implementation(projects.core.service)
            implementation(projects.core.resources)
            implementation(projects.core.ui)
            implementation(projects.core.di)

            implementation(libs.jetbrains.lifecycle.viewmodel.compose)
            implementation(libs.koin.compose.viewmodel)
        }

        androidMain.dependencies {
            implementation(project.dependencies.platform(libs.androidx.compose.bom))
            implementation(libs.androidx.datastore)
            implementation(libs.androidx.datastore.preferences)
            implementation(libs.accompanist.permissions)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.annotation)
            implementation(libs.androidx.compose.material.iconsExtended)
            implementation(libs.androidx.compose.material3)
            implementation(libs.androidx.compose.ui.text)
            implementation(libs.androidx.compose.ui.tooling.preview)
            implementation(libs.jetbrains.lifecycle.runtime.compose)
            implementation(libs.androidx.lifecycle.runtime.ktx)
            implementation(libs.androidx.lifecycle.viewmodel.ktx)
            implementation(libs.androidx.navigation.common)
            implementation(libs.androidx.savedstate.compose)
            implementation(libs.androidx.savedstate.ktx)
            implementation(libs.material)
            implementation(libs.kermit)
        }

        commonTest.dependencies { implementation(projects.core.testing) }

        androidUnitTest.dependencies {
            implementation(libs.junit)
            implementation(libs.mockk)
            implementation(libs.robolectric)
            implementation(project.dependencies.platform(libs.androidx.compose.bom))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.androidx.test.core)
        }
    }
}
