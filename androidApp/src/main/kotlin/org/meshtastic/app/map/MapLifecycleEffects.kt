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
package org.meshtastic.app.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.LifecycleStartEffect

/**
 * Runs [effect] only while the current lifecycle is at least STARTED and [enabled] is true.
 *
 * The cleanup returned by [effect] runs synchronously on ON_STOP, disable, or composition disposal. This is important
 * for hardware work: a coroutine launched from an ON_STOP state change may not run after the host recomposer pauses.
 */
@Composable
internal fun ActiveWhileStarted(enabled: Boolean, effect: () -> () -> Unit) {
    val currentEffect by rememberUpdatedState(effect)

    LifecycleStartEffect(enabled) {
        val cleanup = if (enabled) currentEffect() else ({})
        onStopOrDispose { cleanup() }
    }
}
