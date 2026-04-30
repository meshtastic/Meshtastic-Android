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
@file:Suppress("Wrapping", "UnusedImports", "SpacingAroundColon", "TooGenericExceptionCaught")

package org.meshtastic.core.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.UiText
import org.meshtastic.core.resources.unknown_error
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
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

// ---------------------------------------------------------------------------
// UiState: shared Loading / Content / Error wrapper
// ---------------------------------------------------------------------------

/**
 * Lightweight tri-state wrapper for UI data. Prefer this over bare nullable initial values when the UI needs to
 * distinguish "still loading" from "genuinely empty."
 */
sealed interface UiState<out T> {
    /** Data has not yet arrived. */
    data object Loading : UiState<Nothing>

    /** Data is available. */
    data class Content<T>(val data: T) : UiState<T>

    /** An error occurred while loading. */
    data class Error(val message: UiText) : UiState<Nothing>
}

/** Returns the [Content] data, or `null` if this state is [Loading] or [Error]. */
fun <T> UiState<T>.dataOrNull(): T? = (this as? UiState.Content)?.data

/**
 * Wraps this [Flow] into a `StateFlow<UiState<T>>`, emitting [UiState.Loading] until the first value, then
 * [UiState.Content] for each emission. Upstream errors are caught and mapped to [UiState.Error].
 */
context(viewModel: ViewModel)
fun <T> Flow<T>.asUiState(stopTimeout: Duration = 5.seconds): StateFlow<UiState<T>> =
    this.map<T, UiState<T>> { UiState.Content(it) }
        .onStart { emit(UiState.Loading) }
        .catch { e ->
            val message = e.message?.let { UiText.DynamicString(it) } ?: UiText.Resource(Res.string.unknown_error)
            emit(UiState.Error(message))
        }
        .stateInWhileSubscribed(initialValue = UiState.Loading, stopTimeout = stopTimeout)

// ---------------------------------------------------------------------------
// safeLaunch: CancellationException-safe coroutine launcher with error routing
// ---------------------------------------------------------------------------

/**
 * Launches a coroutine in [viewModelScope] that catches all exceptions except [CancellationException]. Non-cancellation
 * errors are logged and emitted to [errorEvents] (if provided) for one-shot UI consumption (e.g. snackbar / toast).
 *
 * @param context optional [CoroutineContext] element (typically a dispatcher) merged into the launch. Defaults to
 *   [EmptyCoroutineContext], inheriting [viewModelScope]'s dispatcher.
 *
 * ```
 * // In a ViewModel:
 * safeLaunch(errorEvents = _errors) {
 *     repository.saveData(data)
 * }
 * ```
 */
context(viewModel: ViewModel)
fun safeLaunch(
    context: CoroutineContext = EmptyCoroutineContext,
    errorEvents: MutableSharedFlow<UiText>? = null,
    tag: String? = null,
    block: suspend CoroutineScope.() -> Unit,
): Job = viewModel.viewModelScope.launch(context) {
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        val label = tag ?: "safeLaunch"
        Logger.e(e) { "[$label] Unhandled exception" }
        val message = e.message?.let { UiText.DynamicString(it) } ?: UiText.Resource(Res.string.unknown_error)
        errorEvents?.tryEmit(message)
    }
}

/**
 * Creates and returns a [MutableSharedFlow] intended for one-shot error events. Expose as `SharedFlow` via
 * [asSharedFlow] in the ViewModel, and collect in the UI to show snackbars or toasts.
 */
fun errorEventFlow(): MutableSharedFlow<UiText> = MutableSharedFlow(extraBufferCapacity = 1)
