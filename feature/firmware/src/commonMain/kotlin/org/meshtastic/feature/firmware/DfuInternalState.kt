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
package org.meshtastic.feature.firmware

sealed interface DfuInternalState {
    val address: String

    data class Connecting(override val address: String) : DfuInternalState

    data class Connected(override val address: String) : DfuInternalState

    data class Starting(override val address: String) : DfuInternalState

    data class EnablingDfuMode(override val address: String) : DfuInternalState

    data class Progress(
        override val address: String,
        val percent: Int,
        val speed: Float,
        val avgSpeed: Float,
        val currentPart: Int,
        val partsTotal: Int,
    ) : DfuInternalState

    data class Validating(override val address: String) : DfuInternalState

    data class Disconnecting(override val address: String) : DfuInternalState

    data class Disconnected(override val address: String) : DfuInternalState

    data class Completed(override val address: String) : DfuInternalState

    data class Aborted(override val address: String) : DfuInternalState

    data class Error(override val address: String, val message: String?) : DfuInternalState
}
