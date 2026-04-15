/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package org.meshtastic.app

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import org.meshtastic.app.preview.AlertDialogPreview
import org.meshtastic.app.preview.ButtonVariantsPreview
import org.meshtastic.app.preview.CardVariantsPreview
import org.meshtastic.app.preview.CheckboxAndTogglePreview
import org.meshtastic.app.preview.ChipVariantsPreview
import org.meshtastic.app.preview.IconsPreview
import org.meshtastic.app.preview.InputFieldVariantsPreview
import org.meshtastic.app.preview.MultiPreview
import org.meshtastic.app.preview.TextVariantsPreview

/**
 * Screenshot tests for core UI components. These tests validate the visual appearance of composable previews across
 * different device configurations.
 *
 * Run tests: ./gradlew validateGoogleDebugScreenshotTest (or validateFdroidDebugScreenshotTest) ./gradlew
 * validateScreenshotTest (all variants)
 *
 * Update reference images: ./gradlew updateGoogleDebugScreenshotTest (or updateFdroidDebugScreenshotTest) ./gradlew
 * updateScreenshotTest (all variants)
 *
 * View HTML report: app/build/reports/screenshotTest/preview/debug/index.html
 */
class CoreComponentScreenshotTests {
    @PreviewTest
    @MultiPreview
    @Composable
    fun buttonVariantsScreenshot() {
        ButtonVariantsPreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun textVariantsScreenshot() {
        TextVariantsPreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun iconsScreenshot() {
        IconsPreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun cardVariantsScreenshot() {
        CardVariantsPreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun inputFieldVariantsScreenshot() {
        InputFieldVariantsPreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun checkboxAndToggleScreenshot() {
        CheckboxAndTogglePreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun alertDialogScreenshot() {
        AlertDialogPreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun chipVariantsScreenshot() {
        ChipVariantsPreview()
    }
}
