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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import org.meshtastic.core.ui.component.TransparentModeBanner
import org.meshtastic.core.ui.component.TransparentModeChoiceNotice
import org.meshtastic.core.ui.component.TransparentModeEnableConfirmation
import org.meshtastic.core.ui.component.TransparentModeSetting
import org.meshtastic.core.ui.theme.AppTheme

/** The transparent mode warning (design#181): the connect notice, the Connections banner and the LoRa toggle. */
@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotTransparentModeNotice() {
    AppTheme { Box(Modifier.fillMaxSize()) { TransparentModeChoiceNotice(onTurnOff = {}, onLeaveOn = {}) } }
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotTransparentModeBanner() {
    AppTheme { Surface { TransparentModeBanner(onClick = {}, modifier = Modifier.padding(16.dp)) } }
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotTransparentModeSetting() {
    AppTheme { Surface { TransparentModeSetting(checked = true, enabled = true, onCheckedChange = {}) } }
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotTransparentModeEnableConfirmation() {
    AppTheme { Box(Modifier.fillMaxSize()) { TransparentModeEnableConfirmation(onConfirm = {}, onDismiss = {}) } }
}
