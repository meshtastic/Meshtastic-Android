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
import androidx.compose.runtime.staticCompositionLocalOf
import co.touchlab.kermit.Logger
import org.jetbrains.compose.resources.StringResource
import org.meshtastic.core.resources.getStringSuspend

/**
 * The app-wide [SnackbarManager], provided by `MeshtasticSnackbarProvider`. Null outside it, as in previews and
 * isolated UI tests.
 */
@Suppress("CompositionLocalAllowlist") // lets platform toasts reach the shared host without threading it through
val LocalSnackbarManager = staticCompositionLocalOf<SnackbarManager?> { null }

/** A toast for platforms without a system toast: a short snackbar on the app's shared host. */
@Composable
internal fun rememberSnackbarToast(): suspend (String) -> Unit {
    val snackbarManager = LocalSnackbarManager.current
    return remember(snackbarManager) {
        { message ->
            if (snackbarManager != null) {
                snackbarManager.showSnackbar(message)
            } else {
                Logger.i { "Toast with no snackbar host: $message" }
            }
        }
    }
}

/** [rememberSnackbarToast] for a string resource. */
@Composable
internal fun rememberSnackbarToastResource(): suspend (StringResource) -> Unit {
    val showToast = rememberSnackbarToast()
    return remember(showToast) { { resource -> showToast(getStringSuspend(resource)) } }
}
