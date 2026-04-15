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
import org.meshtastic.app.preview.BitwisePreferencePreview
import org.meshtastic.app.preview.ChannelItemPreview
import org.meshtastic.app.preview.EditBase64PreferencePreview
import org.meshtastic.app.preview.EditIPv4PreferencePreview
import org.meshtastic.app.preview.EditPasswordPreferencePreview
import org.meshtastic.app.preview.EmptyDetailPlaceholderPreview
import org.meshtastic.app.preview.MultiPreview
import org.meshtastic.app.preview.PositionPrecisionPreferencePreview
import org.meshtastic.app.preview.PreferenceDividerPreview

/** Screenshot tests for additional core UI components (channel, placeholders, advanced preferences). */
class AdditionalCoreUiScreenshotTests {
    @PreviewTest
    @MultiPreview
    @Composable
    fun channelItemScreenshot() {
        ChannelItemPreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun emptyDetailPlaceholderScreenshot() {
        EmptyDetailPlaceholderPreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun preferenceDividerScreenshot() {
        PreferenceDividerPreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun editPasswordPreferenceScreenshot() {
        EditPasswordPreferencePreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun editIPv4PreferenceScreenshot() {
        EditIPv4PreferencePreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun editBase64PreferenceScreenshot() {
        EditBase64PreferencePreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun positionPrecisionPreferenceScreenshot() {
        PositionPrecisionPreferencePreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun bitwisePreferenceScreenshot() {
        BitwisePreferencePreview()
    }
}
