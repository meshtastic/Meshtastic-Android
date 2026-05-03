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

plugins { alias(libs.plugins.meshtastic.kmp.feature) }

kotlin {
    android {
        namespace = "org.meshtastic.feature.messaging"
        androidResources.enable = false
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.multiplatform.foundation)
            implementation(projects.core.common)
            implementation(projects.core.data)
            implementation(projects.core.database)
            implementation(projects.core.domain)
            implementation(projects.core.model)
            implementation(projects.core.navigation)
            implementation(projects.core.prefs)
            implementation(projects.core.proto)
            implementation(projects.core.resources)
            implementation(projects.core.service)
            implementation(projects.core.ui)

            implementation(libs.jetbrains.navigation3.ui)
            implementation(libs.androidx.paging.common)
            implementation(libs.androidx.paging.compose)

            // JetBrains Material 3 Adaptive (multiplatform ListDetailPaneScaffold)
            implementation(libs.jetbrains.compose.material3.adaptive)
            implementation(libs.jetbrains.compose.material3.adaptive.layout)
            implementation(libs.jetbrains.compose.material3.adaptive.navigation)
            implementation(libs.jetbrains.compose.material3.adaptive.navigation3)
        }

        androidMain.dependencies { implementation(libs.androidx.work.runtime.ktx) }

        commonTest.dependencies { implementation(libs.compose.multiplatform.ui.test) }

        jvmTest.dependencies { implementation(compose.desktop.currentOs) }
    }
}
