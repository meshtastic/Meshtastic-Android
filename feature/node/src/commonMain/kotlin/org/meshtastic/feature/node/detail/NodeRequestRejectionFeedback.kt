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

import co.touchlab.kermit.Logger
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.UiText
import org.meshtastic.core.resources.node_request_send_failed
import org.meshtastic.core.ui.util.SnackbarManager

/** Logs one expected node-request availability failure and presents the shared localized failure message. */
internal suspend fun showNodeRequestFailure(
    failure: Throwable,
    operation: String,
    snackbarManager: SnackbarManager,
    resolveUiText: suspend (UiText) -> String,
) {
    Logger.w(failure) { operation }
    snackbarManager.showSnackbar(resolveUiText(UiText.Resource(Res.string.node_request_send_failed)))
}
