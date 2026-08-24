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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.LifecycleStartEffect

/**
 * Runs [effect] while the current lifecycle is at least STARTED and [enabled] is true.
 *
 * Changing a [restartKeys] value restarts an active effect. The returned cleanup callback is invoked synchronously on
 * ON_STOP, disable, or composition disposal. If that callback cancels coroutine work, downstream cleanup such as a
 * `callbackFlow` provider's `awaitClose` runs as cancellation resumes.
 */
@Composable
fun ActiveWhileStarted(vararg restartKeys: Any?, enabled: Boolean = true, effect: () -> () -> Unit) {
    val currentEffect by rememberUpdatedState(effect)

    LifecycleStartEffect(enabled, *restartKeys) {
        val cleanup = if (enabled) currentEffect() else ({})
        onStopOrDispose { cleanup() }
    }
}
