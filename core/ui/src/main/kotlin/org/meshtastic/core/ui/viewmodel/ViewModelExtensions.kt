/*
 * Copyright (c) 2025 Meshtastic LLC
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

@file:Suppress("Wrapping", "UnusedImports", "SpacingAroundColon")

package org.meshtastic.core.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Extension for converting a [Flow] to a [StateFlow] in a [ViewModel] context.
 *
 * @param initialValue the initial value of the state flow
 * @param stopTimeout configures a delay between the disappearance of the last subscriber and the stopping of the
 *   sharing coroutine.
 */
context(viewModel: ViewModel)
fun <T> Flow<T>.stateInWhileSubscribed(initialValue: T, stopTimeout: Duration = 5.seconds): StateFlow<T> = stateIn(
    scope = viewModel.viewModelScope,
    started = SharingStarted.WhileSubscribed(stopTimeoutMillis = stopTimeout.inWholeMilliseconds),
    initialValue = initialValue,
)
