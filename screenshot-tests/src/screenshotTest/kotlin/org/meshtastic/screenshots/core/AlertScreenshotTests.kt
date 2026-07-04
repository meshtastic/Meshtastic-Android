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
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import org.meshtastic.core.ui.util.PreviewComposableAlert
import org.meshtastic.core.ui.util.PreviewHtmlAlert
import org.meshtastic.core.ui.util.PreviewIconAlert
import org.meshtastic.core.ui.util.PreviewMultipleChoiceAlert
import org.meshtastic.core.ui.util.PreviewTextAlert

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotTextAlert() {
    PreviewTextAlert()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotIconAlert() {
    PreviewIconAlert()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotHtmlAlert() {
    PreviewHtmlAlert()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotMultipleChoiceAlert() {
    PreviewMultipleChoiceAlert()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotComposableAlert() {
    PreviewComposableAlert()
}
