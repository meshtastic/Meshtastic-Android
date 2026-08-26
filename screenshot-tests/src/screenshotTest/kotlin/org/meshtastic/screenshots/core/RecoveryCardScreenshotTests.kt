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
import org.meshtastic.core.ui.component.PermissionRecoveryCardBlockedPreview
import org.meshtastic.core.ui.component.PermissionRecoveryCardDeniedPreview
import org.meshtastic.core.ui.component.PermissionRecoveryCardNotRequestedPreview
import org.meshtastic.core.ui.component.RecoveryCardPreview

/**
 * Visual coverage for the recovery cards.
 *
 * These exist for one specific regression. `PermissionRecoveryCard` encodes a tone rule — an `errorContainer` wash only
 * for a permanent denial, neutral for a permission that was never asked about or declined once — and that rule is pure
 * appearance. Unit tests cannot see it, and the card is shared by the Connections screen, the compass, the barcode
 * scanner and both map flavors, so a change to it repaints five surfaces whose own code never moved.
 *
 * The three permission states are captured separately, differing by exactly one input, so a diff points at the tone or
 * the action label rather than at incidental layout drift. `GRANTED` is absent on purpose: the card renders nothing.
 */
@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotRecoveryCard() {
    RecoveryCardPreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotPermissionRecoveryCardNotRequested() {
    PermissionRecoveryCardNotRequestedPreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotPermissionRecoveryCardDenied() {
    PermissionRecoveryCardDeniedPreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotPermissionRecoveryCardBlocked() {
    PermissionRecoveryCardBlockedPreview()
}
