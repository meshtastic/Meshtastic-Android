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
package org.meshtastic.core.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalClipboard
import kotlinx.coroutines.launch
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.copied_to_clipboard

/**
 * Share where there is no share sheet: copy the text and say it was copied, so the user knows where it went.
 *
 * The clip is marked sensitive because what the app shares (channel and contact URLs) carries keys.
 */
@Composable
internal fun rememberClipboardShare(): (text: String) -> Unit {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val showToast = rememberSnackbarToastResource()
    return remember(clipboard, scope, showToast) {
        { text ->
            scope.launch {
                clipboard.setClipEntry(createClipEntry(text, sensitive = true))
                showToast(Res.string.copied_to_clipboard)
            }
        }
    }
}
