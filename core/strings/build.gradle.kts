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
import com.android.build.api.dsl.androidLibrary

plugins {
    alias(libs.plugins.meshtastic.kmp.library)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    @Suppress("UnstableApiUsage")
    androidLibrary {
        namespace = "org.meshtastic.core.strings"
        androidResources.enable = true
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            // API because consuming modules will always need this dependency
            api(compose.components.resources)
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "org.meshtastic.core.strings"
}
