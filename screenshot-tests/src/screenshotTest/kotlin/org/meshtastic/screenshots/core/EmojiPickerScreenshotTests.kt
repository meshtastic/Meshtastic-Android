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
import org.meshtastic.core.ui.emoji.EmojiPickerContentLargeFontPreview
import org.meshtastic.core.ui.emoji.EmojiPickerContentPreview
import org.meshtastic.core.ui.emoji.SkinTonePopupLargeFontPreview
import org.meshtastic.core.ui.emoji.SkinTonePopupPreview

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotEmojiPickerContent() {
    EmojiPickerContentPreview()
}

@PreviewTest
@Preview(fontScale = 2.0f)
@Composable
fun ScreenshotEmojiPickerContentLargeFont() {
    EmojiPickerContentLargeFontPreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotSkinTonePopup() {
    SkinTonePopupPreview()
}

@PreviewTest
@Preview(fontScale = 2.0f)
@Composable
fun ScreenshotSkinTonePopupLargeFont() {
    SkinTonePopupLargeFontPreview()
}
