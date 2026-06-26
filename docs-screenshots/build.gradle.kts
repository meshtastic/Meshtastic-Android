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
import org.gradle.testretry.TestRetryTaskExtension

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
}

configure<LibraryExtension> {
    namespace = "org.meshtastic.screenshot.docs"

    experimentalProperties["android.experimental.enableScreenshotTest"] = true

    testOptions { screenshotTests { imageDifferenceThreshold = 0.0005f } }
}

// CST screenshot tests use a custom runner incompatible with test-retry
tasks.withType<Test>().configureEach {
    if (name.contains("ScreenshotTest", ignoreCase = true)) {
        extensions.configure<TestRetryTaskExtension> { maxRetries.set(0) }
    }
}

dependencies {
    implementation(project(":core:ui"))
    implementation(project(":core:resources"))
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":feature:connections"))
    implementation(project(":feature:firmware"))

    implementation(libs.compose.multiplatform.foundation)
    implementation(libs.compose.multiplatform.material3)
    implementation(libs.compose.multiplatform.runtime)
    implementation(libs.compose.multiplatform.ui)

    screenshotTestImplementation(libs.screenshot.validation.api)
}
