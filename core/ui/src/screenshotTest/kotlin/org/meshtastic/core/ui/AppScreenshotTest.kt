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
package org.meshtastic.core.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import org.meshtastic.core.model.Channel
import org.meshtastic.core.ui.component.AdaptiveTwoPane
import org.meshtastic.core.ui.component.MainAppBar
import org.meshtastic.core.ui.component.preview.previewNode
import org.meshtastic.core.ui.qr.ScannedQrCodeDialog
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.proto.ChannelSet

class AppScreenshotTest {

    @PreviewTest
    @Preview(showBackground = true)
    @Composable
    fun MainAppBarTest() {
        AppTheme {
            MainAppBar(
                title = "Meshtastic",
                subtitle = "Connected to Node",
                ourNode = previewNode,
                showNodeChip = true,
                canNavigateUp = false,
                onNavigateUp = {},
                actions = {},
                onClickChip = {},
            )
        }
    }

    @PreviewTest
    @Preview(showBackground = true)
    @Composable
    fun ScannedQrCodeDialogTest() {
        AppTheme {
            ScannedQrCodeDialog(
                channels =
                ChannelSet(settings = listOf(Channel.default.settings), lora_config = Channel.default.loraConfig),
                incoming =
                ChannelSet(settings = listOf(Channel.default.settings), lora_config = Channel.default.loraConfig),
                onDismiss = {},
                onConfirm = {},
            )
        }
    }

    @PreviewTest
    @Preview(showBackground = true, widthDp = 800)
    @Composable
    fun AdaptiveTwoPaneExpandedTest() {
        AppTheme { AdaptiveTwoPane(first = { Text("Left Pane") }, second = { Text("Right Pane") }) }
    }

    @PreviewTest
    @Preview(showBackground = true, widthDp = 400)
    @Composable
    fun AdaptiveTwoPaneCompactTest() {
        AppTheme { AdaptiveTwoPane(first = { Text("Top Pane") }, second = { Text("Bottom Pane") }) }
    }
}
