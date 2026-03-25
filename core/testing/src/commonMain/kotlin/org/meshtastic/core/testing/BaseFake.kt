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
package org.meshtastic.core.testing

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/** Base class for fakes that provides common utilities for state management and reset capabilities. */
abstract class BaseFake {
    private val resetActions = mutableListOf<() -> Unit>()

    /** Creates a [MutableStateFlow] and registers it for automatic reset. */
    protected fun <T> mutableStateFlow(initialValue: T): MutableStateFlow<T> {
        val flow = MutableStateFlow(initialValue)
        resetActions.add { flow.value = initialValue }
        return flow
    }

    /** Creates a [MutableSharedFlow] and registers it for automatic reset (replay cache cleared). */
    protected fun <T> mutableSharedFlow(replay: Int = 0): MutableSharedFlow<T> {
        val flow = MutableSharedFlow<T>(replay = replay)
        resetActions.add { flow.resetReplayCache() }
        return flow
    }

    /** Registers a custom reset action (e.g. clearing a list of recorded calls). */
    protected fun registerResetAction(action: () -> Unit) {
        resetActions.add(action)
    }

    /** Resets all registered state flows and custom actions to their initial state. */
    open fun reset() {
        resetActions.forEach { it() }
    }
}
