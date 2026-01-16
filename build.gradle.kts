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



plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.datadog) apply false
    alias(libs.plugins.devtools.ksp) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
    alias(libs.plugins.firebase.perf) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.protobuf) apply false
    alias(libs.plugins.secrets) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.kover)
    alias(libs.plugins.spotless) apply false
    alias(libs.plugins.dokka)
    alias(libs.plugins.meshtastic.root)
}



dependencies {
    dokkaPlugin(libs.dokka.android.documentation.plugin)
}

val debugTests = listOf(
    "testDebugUnitTest",
    "testFdroidDebugUnitTest",
    "testGoogleDebugUnitTest"
)

tasks.register("runAllDebugTests") {
    group = "verification"
    description = "Runs all unit tests for debug variants and flavors"
    dependsOn(subprojects.map { subproject ->
        subproject.tasks.matching { task ->
            task.name in debugTests
        }
    })
}

val connectedTests = listOf(
    "connectedDebugAndroidTest",
    "connectedFdroidDebugAndroidTest",
    "connectedGoogleDebugAndroidTest"
)

tasks.register("runAllConnectedDebugTests") {
    group = "verification"
    description = "Runs all connected tests for debug variants and flavors"
    dependsOn(subprojects.map { subproject ->
        subproject.tasks.matching { task ->
            task.name in connectedTests
        }
    })
}