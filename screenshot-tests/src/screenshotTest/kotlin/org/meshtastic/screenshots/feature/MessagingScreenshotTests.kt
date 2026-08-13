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
package org.meshtastic.screenshots.feature

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewWrapper
import com.android.tools.screenshot.PreviewTest
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.error
import org.meshtastic.core.resources.message_routing_error_max_retransmit
import org.meshtastic.core.resources.message_routing_error_max_retransmit_detail
import org.meshtastic.core.ui.theme.AppThemePreviewWrapper
import org.meshtastic.feature.messaging.DeliveryInfo
import org.meshtastic.feature.messaging.EditQuickChatDialogPreview
import org.meshtastic.feature.messaging.MessageInputPreview
import org.meshtastic.feature.messaging.QuickChatItemPreview
import org.meshtastic.feature.messaging.component.MessageActionsContentPreview
import org.meshtastic.feature.messaging.component.MessageItemGroupedRunPreview
import org.meshtastic.feature.messaging.component.MessageItemMarkdownPreview
import org.meshtastic.feature.messaging.component.MessageItemSignedPreview
import org.meshtastic.feature.messaging.component.MessageItemStatusStatesPreview
import org.meshtastic.feature.messaging.component.MessageSearchBarPreview
import org.meshtastic.feature.messaging.component.ReactionItemPreview

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotQuickChatItem() {
    QuickChatItemPreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotEditQuickChatDialog() {
    EditQuickChatDialogPreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotMessageInput() {
    MessageInputPreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotReactionItem() {
    ReactionItemPreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotMessageSearchBar() {
    MessageSearchBarPreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotMessageItemSigned() {
    MessageItemSignedPreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotMessageItemStatusStates() {
    MessageItemStatusStatesPreview()
}

@PreviewTest
@PreviewLightDark
@PreviewWrapper(AppThemePreviewWrapper::class)
@Composable
fun ScreenshotMessageStatusFailureDetails() {
    DeliveryInfo(
        title = Res.string.error,
        text = Res.string.message_routing_error_max_retransmit,
        detail = Res.string.message_routing_error_max_retransmit_detail,
        resendOption = true,
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotMessageItemMarkdown() {
    MessageItemMarkdownPreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotMessageItemGroupedRun() {
    MessageItemGroupedRunPreview()
}

@PreviewTest
@Preview(name = "MessageActions - narrow sheet", widthDp = 320)
@Composable
fun ScreenshotMessageActionsNarrow() {
    MessageActionsContentPreview()
}

// Accessibility: largest Android font scale must not clip chips, reactions, or the composer.

@PreviewTest
@Preview(name = "MessageItemSigned - font scale 2x", widthDp = 411, fontScale = 2.0f)
@Composable
fun ScreenshotMessageItemSignedLargeFont() {
    MessageItemSignedPreview()
}

@PreviewTest
@Preview(name = "MessageItemGroupedRun - font scale 2x", widthDp = 411, fontScale = 2.0f)
@Composable
fun ScreenshotMessageItemGroupedRunLargeFont() {
    MessageItemGroupedRunPreview()
}

@PreviewTest
@Preview(name = "ReactionItem - font scale 2x", widthDp = 411, fontScale = 2.0f)
@Composable
fun ScreenshotReactionItemLargeFont() {
    ReactionItemPreview()
}

@PreviewTest
@Preview(name = "MessageInput - font scale 2x", widthDp = 411, fontScale = 2.0f)
@Composable
fun ScreenshotMessageInputLargeFont() {
    MessageInputPreview()
}
