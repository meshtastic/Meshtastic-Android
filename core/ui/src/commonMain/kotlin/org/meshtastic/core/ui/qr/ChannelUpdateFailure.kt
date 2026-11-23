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
package org.meshtastic.core.ui.qr

import org.meshtastic.core.common.util.safeCatching
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.channel_update_failed
import org.meshtastic.core.resources.getStringSuspend
import org.meshtastic.core.resources.retry
import org.meshtastic.core.ui.util.SnackbarManager

/**
 * Runs a channel write and, when it fails, tells the user with a snackbar whose Retry action calls [retry]. The failure
 * is rethrown afterwards so the caller's `safeLaunch` still logs it.
 */
suspend fun SnackbarManager.reportingChannelUpdateFailure(retry: () -> Unit, write: suspend () -> Unit) {
    safeCatching { write() }
        .onFailure { error ->
            showSnackbar(
                message = getStringSuspend(Res.string.channel_update_failed),
                actionLabel = getStringSuspend(Res.string.retry),
                onAction = retry,
            )
            throw error
        }
}
