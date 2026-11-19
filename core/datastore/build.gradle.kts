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
    alias(libs.plugins.meshtastic.kmp.library)
    alias(libs.plugins.meshtastic.kmp.web)
    alias(libs.plugins.meshtastic.kotlinx.serialization)
    alias(libs.plugins.meshtastic.koin)
}

kotlin {
    android { withHostTest {} }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.common)
            implementation(projects.core.model)
            implementation(libs.meshtastic.protobufs)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kermit)
            // Exported directly now that androidx.datastore, which used to export it, is nonWeb only.
            api(libs.okio)
        }

        // No wasmJs variant: the serializers and Preferences-backed data sources live in nonWebMain, and the proto data
        // sources use the Store abstraction. api, as before: androidApp's google flavor imports androidx.datastore
        // through this module.
        getByName("nonWebMain").dependencies {
            api(libs.androidx.datastore)
            api(libs.androidx.datastore.preferences)
        }

        meshtasticWeb.wasmJsMain { dependencies { implementation(libs.kotlinx.browser) } }
    }
}
