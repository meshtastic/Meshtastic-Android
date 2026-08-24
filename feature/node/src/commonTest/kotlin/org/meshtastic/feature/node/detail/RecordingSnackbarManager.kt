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
package org.meshtastic.feature.node.detail

import androidx.compose.material3.SnackbarDuration
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.UiText
import org.meshtastic.core.resources.connect_radio_for_remote_admin
import org.meshtastic.core.resources.node_request_send_failed
import org.meshtastic.core.resources.remote_admin_unreachable
import org.meshtastic.core.ui.util.SnackbarManager

internal class RecordingSnackbarManager : SnackbarManager() {
    val messages = mutableListOf<String>()

    override fun showSnackbar(
        message: String,
        actionLabel: String?,
        withDismissAction: Boolean,
        duration: SnackbarDuration,
        onAction: (() -> Unit)?,
    ) {
        messages += message
    }
}

internal const val NODE_REQUEST_SEND_FAILED_TEXT = "Couldn't send request. Try again."

internal val resolveNodeRequestFailureUiText: suspend (UiText) -> String = { text ->
    when (text) {
        is UiText.DynamicString -> text.value

        is UiText.Resource ->
            if (text.res == Res.string.node_request_send_failed) {
                NODE_REQUEST_SEND_FAILED_TEXT
            } else {
                error("Unexpected UiText resource in test: ${text.res}")
            }
    }
}

internal val resolveNodeDetailUiTextForTest: suspend (UiText) -> String = { text ->
    when (text) {
        is UiText.DynamicString -> text.value

        is UiText.Resource ->
            when (text.res) {
                Res.string.connect_radio_for_remote_admin -> "Connect to a radio to administer remote nodes."
                Res.string.remote_admin_unreachable -> "Could not reach node — try again or move closer."
                Res.string.node_request_send_failed -> NODE_REQUEST_SEND_FAILED_TEXT
                else -> error("Unexpected UiText resource in test: ${text.res}")
            }
    }
}
