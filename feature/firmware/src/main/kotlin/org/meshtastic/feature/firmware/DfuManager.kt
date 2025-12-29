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

package org.meshtastic.feature.firmware

import android.content.Context
import co.touchlab.kermit.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import no.nordicsemi.android.dfu.DfuProgressListenerAdapter
import no.nordicsemi.android.dfu.DfuServiceListenerHelper
import javax.inject.Inject
import javax.inject.Singleton

/** Encapsulates Nordic DFU library interactions and internal state monitoring. */
@Singleton
class DfuManager @Inject constructor(@ApplicationContext private val context: Context) {
    /** Observe DFU progress and events. */
    fun progressFlow(): Flow<DfuInternalState> = callbackFlow {
        val listener =
            object : DfuProgressListenerAdapter() {
                override fun onDfuProcessStarting(deviceAddress: String) {
                    trySend(DfuInternalState.Starting(deviceAddress))
                }

                override fun onProgressChanged(
                    deviceAddress: String,
                    percent: Int,
                    speed: Float,
                    avgSpeed: Float,
                    currentPart: Int,
                    partsTotal: Int,
                ) {
                    trySend(DfuInternalState.Progress(deviceAddress, percent, speed, avgSpeed, currentPart, partsTotal))
                }

                override fun onDfuCompleted(deviceAddress: String) {
                    trySend(DfuInternalState.Completed(deviceAddress))
                }

                override fun onDfuAborted(deviceAddress: String) {
                    trySend(DfuInternalState.Aborted(deviceAddress))
                }

                override fun onError(deviceAddress: String, error: Int, errorType: Int, message: String?) {
                    trySend(DfuInternalState.Error(deviceAddress, message))
                }
            }

        DfuServiceListenerHelper.registerProgressListener(context, listener)
        awaitClose {
            runCatching { DfuServiceListenerHelper.unregisterProgressListener(context, listener) }
                .onFailure { Logger.w(it) { "Failed to unregister DFU listener" } }
        }
    }
}

sealed interface DfuInternalState {
    data class Starting(val address: String) : DfuInternalState

    data class Progress(
        val address: String,
        val percent: Int,
        val speed: Float,
        val avgSpeed: Float,
        val currentPart: Int,
        val partsTotal: Int,
    ) : DfuInternalState

    data class Completed(val address: String) : DfuInternalState

    data class Aborted(val address: String) : DfuInternalState

    data class Error(val address: String, val message: String?) : DfuInternalState
}
