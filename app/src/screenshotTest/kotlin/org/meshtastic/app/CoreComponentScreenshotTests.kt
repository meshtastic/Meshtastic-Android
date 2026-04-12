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

import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import org.meshtastic.core.ui.preview.AlertDialogPreview
import org.meshtastic.core.ui.preview.ButtonVariantsPreview
import org.meshtastic.core.ui.preview.CardVariantsPreview
import org.meshtastic.core.ui.preview.CheckboxAndTogglePreview
import org.meshtastic.core.ui.preview.ChipVariantsPreview
import org.meshtastic.core.ui.preview.IconsPreview
import org.meshtastic.core.ui.preview.InputFieldVariantsPreview
import org.meshtastic.core.ui.preview.TextVariantsPreview

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
    @Preview(showBackground = true)
    fun buttonVariantsScreenshot() {
        ButtonVariantsPreview()
    }

    @PreviewTest
    @Preview(showBackground = true)
    fun textVariantsScreenshot() {
        TextVariantsPreview()
    }

    @PreviewTest
    @Preview(showBackground = true)
    fun iconsScreenshot() {
        IconsPreview()
    }

    @PreviewTest
    @Preview(showBackground = true)
    fun cardVariantsScreenshot() {
        CardVariantsPreview()
    }

    @PreviewTest
    @Preview(showBackground = true)
    fun inputFieldVariantsScreenshot() {
        InputFieldVariantsPreview()
    }

    @PreviewTest
    @Preview(showBackground = true)
    fun checkboxAndToggleScreenshot() {
        CheckboxAndTogglePreview()
    }

    @PreviewTest
    @Preview(showBackground = true)
    fun alertDialogScreenshot() {
        AlertDialogPreview()
    }

    @PreviewTest
    @Preview(showBackground = true)
    fun chipVariantsScreenshot() {
        ChipVariantsPreview()
    }
}
