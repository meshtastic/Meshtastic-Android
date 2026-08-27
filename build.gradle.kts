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
    // The version catalog, reached through the API rather than the generated `libs` accessor. The
    // accessor genuinely does not resolve in this script — it fails with a receiver-type mismatch,
    // which is why this block used to carry copies of versions the catalog already held: it sat at
    // compose-multiplatform 1.11.1 long after the catalog moved to 1.12.0-rc01, shipping URLs for a
    // version nothing in the project used. The catalog itself is right here, so anything we already
    // own is read rather than duplicated.
    val catalog = extensions.getByType<org.gradle.api.artifacts.VersionCatalogsExtension>().named("libs")
    val composeVersion = catalog.findVersion("compose-multiplatform").get().requiredVersion

    fun catalogCoordinate(alias: String): String =
        catalog.findLibrary(alias).get().get().let {
            "${it.module.group}:${it.module.name}:${it.versionConstraint.requiredVersion}"
        }

    val platforms = setOf("linux-x64", "linux-arm64")

    // desktopApp picks exactly one maplibre native runtime, by build-host arch, so an x86_64 generation
    // host never resolves the arm64 blob and the arm64 offline build had no URL to fetch. These name the
    // same catalog aliases desktopApp itself uses, so the two cannot disagree, and a target platform
    // added below with no matching catalog entry fails here at configure time rather than eleven minutes
    // into an offline build.
    val maplibreRuntimes = platforms.map { catalogCoordinate("maplibre-compose-runtime-vulkan-$it") }

    extensions.configure<org.meshtastic.flatpak.sources.FlatpakSourcesExtension> {
        outputFile.set(layout.buildDirectory.file("flatpak-sources.json"))
        mustRunAfterTasks.set(listOf(":desktopApp:assemble", ":desktopApp:packageUberJarForCurrentOS"))
        targetPlatforms.set(platforms)
        // Force-resolve platform-specific native artifacts not resolved on the generation host (the
        // manifest is generated on an x86_64 runner, but the offline build also needs to run on arm64).
        //
        // Since plugin 0.2.0 each coordinate resolves transitively, so only direct dependencies belong
        // here: desktop-jvm-{platform} brings skiko-awt-runtime-{platform}, and the maplibre runtimes
        // bring the maplibre-native-ffi and LWJGL natives with them. Those three were spelled out here
        // until 0.2.0, versions and classifiers copied by hand out of POMs this project does not own —
        // which went stale silently and cost two arm64 build failures on #6901.
        platformDependencies.set(
            maplibreRuntimes + setOf("org.jetbrains.compose.desktop:desktop-jvm-{platform}:$composeVersion"),
        )
    }
}

dependencies {
    dokkaPlugin(libs.dokka.android.documentation.plugin)
}
