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
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.meshtastic.buildlogic.maplibreDesktopRuntime

// Marketing (store listing) screenshots — GENERATE-ONLY, intentionally NOT run in CI.
//
// Unlike :screenshot-tests (a visual-regression gate) and :docs-screenshots (doc-framed compositions), this module
// is a plain JVM program that draws the Play Store / F-Droid listing screenshots: the app's own commonMain screens
// rendered offscreen with Compose Desktop's ImageComposeScene over one sample mesh, and the real MapLibre map captured
// through maplibre-compose's MapSnapshotter. Every shot is the raw screen, as Play requires ("do not position the
// screenshots within device frames"), at one size per form factor: phone 1080x1920, 7-inch 1080x1920, 10-inch
// 2560x1440, Chromebook 1920x1080 and Android XR 1920x1200. The same screens adapt themselves - the app's navigation
// suite, list-detail and two-pane layouts do the work - so a form factor is data, not a copy of the loop. It has no
// tests, so `./gradlew test` / `allTests` never touch it; the one command is
//
//     ./gradlew :marketing-screenshots:updateMarketingScreenshots
//
// which writes en-US straight into fastlane/metadata/android/en-US/images/<type>Screenshots/ (phone, sevenInch,
// tenInch, chromebook, xr). Any other locale in -PmarketingLocales (comma list, default "en-US") renders into
// build/marketing-screenshots/<locale>/images/ in the same layout, for a later `fastlane supply` run: only en-US is
// committed because everything under fastlane/ is read straight from git by F-Droid and IzzyOnDroid. The sample
// prose lives in src/main/composeResources/values/strings.xml, which Crowdin's first rule already picks up.
// -PmarketingFramed=true also writes the framed 1242x2484 phone variants (bezel, caption banner) under
// build/marketing-screenshots/framed/<locale>/ for website and social use. The map needs a Vulkan loader the JVM can
// find: on a stock Ubuntu nothing, on a Nix host, whose shell replaces LD_LIBRARY_PATH with its own, pass
// -PmarketingLibraryPath=/usr/lib/x86_64-linux-gnu.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.meshtastic.detekt)
    alias(libs.plugins.meshtastic.spotless)
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
        vendor.set(JvmVendorSpec.JETBRAINS)
    }
    compilerOptions { jvmTarget.set(JvmTarget.JVM_25) }
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(libs.compose.multiplatform.foundation)
    implementation(libs.compose.multiplatform.material3)
    implementation(libs.compose.multiplatform.runtime)
    implementation(libs.compose.multiplatform.ui)
    implementation(libs.compose.multiplatform.resources)
    implementation(libs.jetbrains.compose.material3.adaptive)
    implementation(libs.jetbrains.compose.material3.adaptive.layout)
    implementation(libs.jetbrains.compose.material3.adaptive.navigation.suite)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.maplibre.compose)
    maplibreDesktopRuntime()

    implementation(projects.core.common)
    implementation(projects.core.database)
    implementation(projects.core.model)
    implementation(projects.core.navigation)
    implementation(projects.core.resources)
    implementation(projects.core.ui)
    implementation(projects.feature.map)
    implementation(projects.feature.mapMaplibre)
    implementation(projects.feature.messaging)
    implementation(projects.feature.node)
    implementation(libs.meshtastic.protobufs)
}

compose.resources {
    publicResClass = false
    packageOfResClass = "org.meshtastic.screenshot.marketing.resources"
}

// Script-level vals are re-bound to locals inside the task block: a lambda that reads them directly captures the
// script object, which the configuration cache refuses to serialize.
val fastlaneMetadata = isolated.rootProject.projectDirectory.dir("fastlane/metadata/android")
val marketingOutput = layout.buildDirectory.dir("marketing-screenshots")
val marketingLocales = providers.gradleProperty("marketingLocales").orElse("en-US")
val marketingFramed = providers.gradleProperty("marketingFramed").orElse("false")
// The property wins because a Nix dev shell rewrites LD_LIBRARY_PATH on entry, so the exported one never arrives.
val hostLibraryPath =
    providers.gradleProperty("marketingLibraryPath").orElse(providers.environmentVariable("LD_LIBRARY_PATH"))

tasks.register<JavaExec>("updateMarketingScreenshots") {
    description =
        "Renders the store-listing screenshots into fastlane/metadata/android/en-US/images/ (other locales into " +
        "build/marketing-screenshots/)."
    group = "store-screenshots"
    val output = fastlaneMetadata
    val buildOutput = marketingOutput
    val locales = marketingLocales
    val framed = marketingFramed
    val libraryPath = hostLibraryPath
    mainClass.set("org.meshtastic.screenshot.marketing.MainKt")
    classpath = sourceSets.main.get().runtimeClasspath
    // maplibre-compose reaches maplibre-native through the FFM API, which refuses to load without this.
    jvmArgs("--enable-native-access=ALL-UNNAMED", "-Djava.awt.headless=true", "-Xmx2G")
    argumentProviders.add(
        CommandLineArgumentProvider {
            listOf(output.asFile.absolutePath, buildOutput.get().asFile.absolutePath, locales.get(), framed.get())
        },
    )
    // The generator must provably run without a display, whatever the daemon inherited; the Vulkan loader search
    // path, on the other hand, is the caller's to set.
    environment.remove("DISPLAY")
    environment.remove("WAYLAND_DISPLAY")
    libraryPath.orNull?.let { environment("LD_LIBRARY_PATH", it) }
    outputs.upToDateWhen { false }
}
