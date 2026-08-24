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
import com.android.build.api.dsl.LibraryExtension

// Documentation screenshots — GENERATE-ONLY, intentionally NOT gated in CI.
//
// Unlike :screenshot-tests (a visual-regression gate run via :screenshot-tests:validateDebugScreenshotTest), this
// module holds doc-framed composition previews whose framing is tuned for the documentation site. Its reference
// images are regenerated on demand (./gradlew :docs-screenshots:updateDebugScreenshotTest) and consumed by
// :screenshot-tests:copyDocsScreenshots; CI does NOT run validateDebugScreenshotTest here, so reframing a doc image
// never churns the regression gate. Keep regression checks in :screenshot-tests, doc-only compositions here.
plugins {
    alias(libs.plugins.meshtastic.android.library)
    alias(libs.plugins.meshtastic.android.library.compose)
    alias(libs.plugins.compose.screenshot)
    alias(libs.plugins.meshtastic.android.screenshot)
}

configure<LibraryExtension> {
    namespace = "org.meshtastic.screenshot.docs"

    testOptions { screenshotTests { imageDifferenceThreshold = 0.0005f } }
}

dependencies {
    implementation(projects.core.ui)
    implementation(projects.core.resources)
    implementation(projects.core.model)
    implementation(projects.core.common)
    implementation(projects.feature.connections)
    implementation(projects.feature.firmware)
}
