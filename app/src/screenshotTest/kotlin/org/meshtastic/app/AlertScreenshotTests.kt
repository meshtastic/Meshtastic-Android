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
import org.meshtastic.app.preview.ComposableAlertPreview
import org.meshtastic.app.preview.HtmlAlertPreview
import org.meshtastic.app.preview.IconAlertPreview
import org.meshtastic.app.preview.MultiPreview
import org.meshtastic.app.preview.MultipleChoiceAlertPreview
import org.meshtastic.app.preview.TextAlertPreview

/** Screenshot tests for alert dialog components from core:ui. */
class AlertScreenshotTests {
    @PreviewTest
    @MultiPreview
    @Composable
    fun textAlertScreenshot() {
        TextAlertPreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun iconAlertScreenshot() {
        IconAlertPreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun htmlAlertScreenshot() {
        HtmlAlertPreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun multipleChoiceAlertScreenshot() {
        MultipleChoiceAlertPreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun composableAlertScreenshot() {
        ComposableAlertPreview()
    }
}
