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
    alias(libs.plugins.meshtastic.koin)
    alias(libs.plugins.meshtastic.kotlinx.serialization)
}

kotlin {
    android { withHostTest { isIncludeAndroidResources = true } }

    // Library module: bare wasmJs(), no browser(). No custom hierarchy group is needed for MAIN — zero
    // expect/actual declarations and zero java.*/android.* imports in commonMain (confirmed via grep),
    // and every commonMain dependency (core:repository/model/common/database/datastore/resources,
    // protobufs, kermit/okio/kotlinx-datetime/kotlinx-serialization-json(-okio)) already publishes a
    // wasmJs variant — same shape as core:repository/core:service, unlike core:ble/core:database.
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs()

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.repository)
            implementation(projects.core.model)
            implementation(libs.meshtastic.protobufs)
            implementation(projects.core.common)
            implementation(projects.core.database)
            implementation(projects.core.datastore)
            implementation(projects.core.resources)

            implementation(libs.kermit)
            implementation(libs.okio)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.serialization.json.okio)
        }

        // TEST only: core:testing has no wasmJs target (same gap every other module this session hit).
        // 7 of 13 commonTest files depend on it (confirmed via grep for the import, not assumed) — moved
        // to a nonWebTest source set; the other 6 stay in commonTest and compile for wasmJs.
        val nonWebTest by creating {
            dependsOn(commonTest.get())
            dependencies { implementation(projects.core.testing) }
        }
        getByName("jvmTest") { dependsOn(nonWebTest) }
        getByName("androidHostTest") { dependsOn(nonWebTest) }
        matching { it.name == "iosArm64Test" || it.name == "iosSimulatorArm64Test" }
            .configureEach { dependsOn(nonWebTest) }
    }
}
