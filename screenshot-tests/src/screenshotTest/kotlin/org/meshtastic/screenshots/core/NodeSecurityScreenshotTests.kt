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
package org.meshtastic.screenshots.core

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import org.meshtastic.core.ui.component.NodeSecurityGlyphsPreview
import org.meshtastic.core.ui.component.SecurityLegendPreview

/**
 * The node security indicators (design#149).
 *
 * The two verification glyphs are composites drawn at render time rather than single assets, so a golden is the only
 * thing that catches the badge losing legibility at the sizes the node rows use.
 */
@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotNodeSecurityGlyphs() {
    NodeSecurityGlyphsPreview()
}

@PreviewTest
@Preview
@Composable
fun ScreenshotNodeSecurityLegend() {
    SecurityLegendPreview()
}
