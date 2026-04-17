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

plugins { alias(libs.plugins.meshtastic.kmp.library) }

kotlin {
    @Suppress("UnstableApiUsage")
    android {
        namespace = "org.meshtastic.core.testing"
        androidResources.enable = false
        withHostTest {}
    }

    sourceSets {
        commonMain.dependencies {
            // Core KMP models and contracts for creating test fakes
            // NOTE: Only api() core:model and core:repository to keep dependency graph clean.
            // Heavy modules (database, data, domain) should depend on core:testing, not vice versa.
            api(projects.core.model)
            api(projects.core.repository)
            implementation(projects.core.database)
            implementation(projects.core.ble)
            implementation(projects.core.datastore)
            implementation(libs.androidx.room.runtime)
            api(libs.kermit)

            // Testing libraries - these are public API for all test consumers
            api(kotlin("test"))
            api(libs.kotlinx.coroutines.test)
            api(libs.turbine)
            api(libs.junit)
        }
        androidMain.dependencies {
            api(libs.androidx.test.core)
            api(libs.robolectric)
        }
    }
}
