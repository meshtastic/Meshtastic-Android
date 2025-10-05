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

package org.meshtastic.feature.settings.radio

import org.meshtastic.feature.settings.util.UiText

/** Generic sealed class defines each possible state of a response. */
sealed class ResponseState<out T> {
    data object Empty : ResponseState<Nothing>()

    data class Loading(var total: Int = 1, var completed: Int = 0) : ResponseState<Nothing>()

    data class Success<T>(val result: T) : ResponseState<T>()

    data class Error(val error: UiText) : ResponseState<Nothing>()

    fun isWaiting() = this !is Empty
}
