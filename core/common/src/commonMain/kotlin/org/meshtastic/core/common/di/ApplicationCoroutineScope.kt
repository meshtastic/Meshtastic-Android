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
package org.meshtastic.core.common.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.ioDispatcher

/**
 * A process-wide [CoroutineScope] that outlives individual ViewModels and UI components.
 *
 * Use this scope for fire-and-forget cleanup work that must continue after a ViewModel's own scope has been cancelled
 * (for example, deleting temporary files in `onCleared()`). Backed by a [SupervisorJob] so failures in one child do not
 * cancel siblings, and by [ioDispatcher] so work runs off the main thread.
 *
 * Prefer scoping work to a more specific scope (like `viewModelScope`) whenever possible; this scope is an escape hatch
 * and should be used sparingly.
 */
interface ApplicationCoroutineScope : CoroutineScope

@Single(binds = [ApplicationCoroutineScope::class])
internal class ApplicationCoroutineScopeImpl : ApplicationCoroutineScope {
    override val coroutineContext = SupervisorJob() + ioDispatcher
}
