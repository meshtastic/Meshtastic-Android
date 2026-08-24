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
import com.android.tools.screenshot.PreviewTest
import org.meshtastic.core.ui.component.AdaptiveTwoPaneSample

// Guards the AdaptiveTwoPane breakpoint (Meshtastic-Android#5874): the panes must stay stacked at
// compact/medium widths and only split side-by-side once the window is expanded (>= 840dp). A
// regression that splits too early (e.g. at 600dp) squishes content on tablets in portrait and will
// diff these references.

@PreviewTest
@Preview(name = "compact 360dp - stacked", widthDp = 360, heightDp = 640)
@Composable
fun ScreenshotAdaptiveTwoPaneCompact() {
    AdaptiveTwoPaneSample()
}

@PreviewTest
@Preview(name = "medium 720dp - stacked", widthDp = 720, heightDp = 900)
@Composable
fun ScreenshotAdaptiveTwoPaneMedium() {
    AdaptiveTwoPaneSample()
}

@PreviewTest
@Preview(name = "expanded 900dp - split", widthDp = 900, heightDp = 720)
@Composable
fun ScreenshotAdaptiveTwoPaneExpanded() {
    AdaptiveTwoPaneSample()
}
