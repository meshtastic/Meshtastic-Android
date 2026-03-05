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
    alias(libs.plugins.meshtastic.kotlinx.serialization)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.devtools.ksp)
}

kotlin {
    android { namespace = "org.meshtastic.core.datastore" }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.proto)
            implementation(libs.androidx.datastore)
            implementation(libs.androidx.datastore.preferences)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kermit)
        }
        androidMain.dependencies {
            implementation(libs.hilt.android)
            implementation(libs.javax.inject)
        }
    }
}

dependencies { "kspAndroid"(libs.hilt.compiler) }
