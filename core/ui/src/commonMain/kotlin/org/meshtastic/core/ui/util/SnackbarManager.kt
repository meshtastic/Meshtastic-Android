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

import androidx.compose.material3.SnackbarDuration
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import org.koin.core.annotation.Single

/**
 * A global manager for displaying snackbars across the application. This allows ViewModels to trigger transient
 * feedback messages without direct dependencies on UI components or `SnackbarHostState`.
 *
 * Events are buffered in a [Channel] and consumed exactly once by the host composable via `MeshtasticSnackbarHost`.
 *
 * @see AlertManager for the modal dialog equivalent.
 */
@Single
open class SnackbarManager {
    data class SnackbarEvent(
        val message: String,
        val actionLabel: String? = null,
        val withDismissAction: Boolean = false,
        val duration: SnackbarDuration = SnackbarDuration.Short,
        val onAction: (() -> Unit)? = null,
    )

    private val _events = Channel<SnackbarEvent>(Channel.BUFFERED)
    open val events: Flow<SnackbarEvent> = _events.receiveAsFlow()

    open fun showSnackbar(
        message: String,
        actionLabel: String? = null,
        withDismissAction: Boolean = false,
        duration: SnackbarDuration = if (actionLabel != null) SnackbarDuration.Indefinite else SnackbarDuration.Short,
        onAction: (() -> Unit)? = null,
    ) {
        _events.trySend(
            SnackbarEvent(
                message = message,
                actionLabel = actionLabel,
                withDismissAction = withDismissAction,
                duration = duration,
                onAction = onAction,
            ),
        )
    }
}
