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
    // On the root classpath (never applied here) so AndroidScreenshotConventionPlugin can
    // reference PreviewScreenshotValidationTask — build-logic's compileOnly is not enough.
    alias(libs.plugins.compose.screenshot) apply false
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
    // The generated `libs` accessor isn't resolvable in this script, but the runtime catalog API is —
    // reading the version here (instead of a hand-maintained literal) closes the trap that already bit
    // once: the block was pinned to 1.11.1 long after the catalog moved on, so the arm64 offline flatpak
    // build kept resolving (and shipping) URLs for a version nothing in the project used anymore.
    val composeMultiplatformVersion =
        extensions.getByType<org.gradle.api.artifacts.VersionCatalogsExtension>()
            .named("libs")
            .findVersion("compose-multiplatform")
            .get()
            .requiredVersion
    extensions.configure<org.meshtastic.flatpak.sources.FlatpakSourcesExtension> {
        outputFile.set(layout.buildDirectory.file("flatpak-sources.json"))
        mustRunAfterTasks.set(listOf(":desktopApp:assemble", ":desktopApp:packageUberJarForCurrentOS"))
        // Force-resolve platform-specific native artifacts not resolved on the generation host (the
        // manifest is generated on an x86_64 runner, but the offline build also needs to run on arm64).
        //
        // Since plugin 0.2.0 each coordinate resolves TRANSITIVELY, so only direct dependencies belong
        // here — desktop-jvm-{platform} brings org.jetbrains.skiko:skiko-awt-runtime-{platform} along
        // by itself (its version previously had to be tracked by hand against desktop-jvm's POM).
        targetPlatforms.set(setOf("linux-x64", "linux-arm64"))
        //
        // maplibre-compose's desktop renderer is the same shape of problem: desktopApp picks exactly one
        // native runtime, by build-host arch, so an x86_64 generation host never resolves the arm64 blob
        // and the arm64 offline build had no URL to fetch. KEEP this version in sync with
        // `maplibre-compose` in gradle/libs.versions.toml, for the same reason the two above say so.
        //
        // Its LWJGL natives need naming too, and cannot use {platform}: force-resolution here is
        // non-transitive (see FlatpakSourcesPlugin), and LWJGL classifies x64 as plain `natives-linux`
        // rather than `natives-linux-x64`, so the token would expand to an artifact that does not exist.
        // Two literals instead; a template with no token resolves to itself once per platform. Track
        // whatever the maplibre-compose-runtime-vulkan-linux-* POMs declare if maplibre is bumped.
        platformDependencies.set(setOf(
            "org.jetbrains.compose.desktop:desktop-jvm-{platform}:$composeMultiplatformVersion",
            "org.maplibre.compose:maplibre-compose-runtime-vulkan-{platform}:0.15.0",
        ))
    }
}

dependencies {
    dokkaPlugin(libs.dokka.android.documentation.plugin)
}
