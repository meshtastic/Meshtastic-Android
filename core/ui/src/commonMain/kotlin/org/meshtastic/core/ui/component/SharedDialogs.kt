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
package org.meshtastic.core.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.ui.qr.ScannedQrCodeDialog
import org.meshtastic.core.ui.share.SharedContactDialog
import org.meshtastic.core.ui.viewmodel.UIViewModel

/**
 * Shared composable that conditionally renders [SharedContactDialog] and [ScannedQrCodeDialog] when the device is
 * connected and requests are pending.
 *
 * This eliminates identical boilerplate from Android `MainScreen` and Desktop `DesktopMainScreen`.
 */
@Composable
fun SharedDialogs(uiViewModel: UIViewModel) {
    val connectionState by uiViewModel.connectionState.collectAsStateWithLifecycle()
    val sharedContactRequested by uiViewModel.sharedContactRequested.collectAsStateWithLifecycle()
    val requestChannelSet by uiViewModel.requestChannelSet.collectAsStateWithLifecycle()

    if (connectionState == ConnectionState.Connected) {
        sharedContactRequested?.let {
            SharedContactDialog(sharedContact = it, onDismiss = { uiViewModel.clearSharedContactRequested() })
        }

        requestChannelSet?.let { newChannelSet ->
            ScannedQrCodeDialog(newChannelSet, onDismiss = { uiViewModel.clearRequestChannelUrl() })
        }
    }
}
