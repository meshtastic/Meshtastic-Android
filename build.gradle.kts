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
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.kover)
    alias(libs.plugins.spotless) apply false
    alias(libs.plugins.dokka)
    alias(libs.plugins.meshtastic.root)
    alias(libs.plugins.meshtastic.docs)
}

plugins.withId("org.meshtastic.flatpak.sources") {
    extensions.configure<org.meshtastic.flatpak.sources.FlatpakSourcesExtension> {
        outputFile.set(layout.buildDirectory.file("flatpak-sources.json"))
        mustRunAfterTasks.set(listOf(":desktopApp:assemble", ":desktopApp:packageUberJarForCurrentOS"))
        // Force-resolve platform-specific native artifacts not resolved on the generation host (the
        // manifest is generated on an x86_64 runner, but the offline build also needs to run on arm64).
        //
        // KEEP desktop-jvm's version IN SYNC with the compose-multiplatform entry in
        // gradle/libs.versions.toml — that's exactly the maintenance trap that caused this to break once
        // already: this block was pinned to 1.11.1 and never updated when the catalog moved to
        // 1.12.0-rc01, so the arm64 offline flatpak build kept resolving (and shipping) URLs for a version
        // nothing in the project used anymore. (A `libs.versions.composeMultiplatform` reference here
        // would auto-track it, but that accessor isn't resolvable in this project's script — tried and
        // confirmed via a direct `./gradlew help` failure — so it has to stay a literal that a human/agent
        // updates by hand alongside any compose-multiplatform bump.)
        //
        // skiko isn't in this catalog at all — its version must track whatever desktop-jvm-<platform>'s
        // own POM declares for org.jetbrains.skiko:skiko-awt-runtime-<platform> (0.150.1 for
        // compose-multiplatform 1.12.0-rc01); check that POM again if this version is bumped.
        targetPlatforms.set(setOf("linux-x64", "linux-arm64"))
        platformDependencies.set(setOf(
            "org.jetbrains.skiko:skiko-awt-runtime-{platform}:0.150.1",
            "org.jetbrains.compose.desktop:desktop-jvm-{platform}:1.12.0-rc01",
        ))
    }
}

dependencies {
    dokkaPlugin(libs.dokka.android.documentation.plugin)
}
