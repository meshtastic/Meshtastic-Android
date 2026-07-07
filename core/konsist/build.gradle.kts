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

// Architecture-guard module. Holds no production code — only Konsist tests that
// scan every module's source from disk and assert the repo's KMP boundary rules.
// Konsist is JVM-only, so its tests live in jvmTest (it cannot go in commonTest).
// Runs under the existing `allTests` baseline gate via :core:konsist:allTests.
plugins {
    alias(libs.plugins.meshtastic.kmp.library)
    id("meshtastic.kmp.jvm.android")
}

kotlin {
    android { withHostTest {} }

    sourceSets {
        getByName("jvmTest") {
            dependencies {
                implementation(libs.konsist)
                implementation(libs.junit)
            }
        }
    }
}
