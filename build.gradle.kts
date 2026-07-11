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
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.datadog) apply false
    alias(libs.plugins.devtools.ksp) apply false
    alias(libs.plugins.koin.compiler) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.aboutlibraries) apply false
    alias(libs.plugins.secrets) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.kover)
    alias(libs.plugins.spotless) apply false
    alias(libs.plugins.dokka)
    alias(libs.plugins.test.retry) apply false
    alias(libs.plugins.meshtastic.root)
    alias(libs.plugins.meshtastic.docs)
}

plugins.withId("org.meshtastic.flatpak.sources") {
    extensions.configure<org.meshtastic.flatpak.sources.FlatpakSourcesExtension> {
        outputFile.set(layout.buildDirectory.file("flatpak-sources.json"))
        mustRunAfterTasks.set(listOf(":desktopApp:assemble", ":desktopApp:packageUberJarForCurrentOS"))
        // Force-resolve platform-specific native artifacts not resolved on the generation host.
        // The compose-desktop version MUST track the compose-multiplatform catalog version, else the
        // arm64 offline flatpak build fails to resolve desktop-jvm-linux-arm64 (skiko version per its POM).
        targetPlatforms.set(setOf("linux-x64", "linux-arm64"))
        platformDependencies.set(setOf(
            "org.jetbrains.skiko:skiko-awt-runtime-{platform}:0.144.6",
            "org.jetbrains.compose.desktop:desktop-jvm-{platform}:1.11.1",
        ))
    }
}

dependencies {
    dokkaPlugin(libs.dokka.android.documentation.plugin)
}
