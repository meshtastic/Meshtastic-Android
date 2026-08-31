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

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.meshtastic.kmp.library)
    alias(libs.plugins.meshtastic.kmp.library.compose)
    alias(libs.plugins.meshtastic.kotlinx.serialization)
}

kotlin {
    // Library module: bare wasmJs(), no browser() — see core:prefs/build.gradle.kts's comment.
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs()

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.common)
            implementation(projects.core.resources)
            implementation(libs.kotlinx.serialization.core)
            implementation(libs.jetbrains.navigation3.ui)
            implementation(libs.kermit)
        }

        // core:testing has no wasmJs target — nothing here actually used it beyond kotlin("test") (confirmed via
        // grep, zero org.meshtastic.core.testing references in this module's commonTest), so depend on that
        // directly instead of moving files, matching core:database's identical fix for the same situation.
        commonTest.dependencies { implementation(kotlin("test")) }
    }
}
