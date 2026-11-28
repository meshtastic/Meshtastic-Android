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
package org.meshtastic.core.ui.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.repository.TransparentModeController
import org.meshtastic.core.repository.TransparentModeState

/** Presents the app-wide transparent mode state; the notice host and the Connections banner each hold one. */
@KoinViewModel
class TransparentModeViewModel(private val controller: TransparentModeController) : ViewModel() {

    val state: StateFlow<TransparentModeState> =
        controller.state.stateInWhileSubscribed(initialValue = TransparentModeState())

    fun turnOff() = controller.turnOff()

    fun closeNotice() = controller.closeNotice()

    fun reopenNotice() = controller.reopenNotice()

    suspend fun confirmTurnedOnByUser(): Boolean? = controller.confirmTurnedOnByUser()
}
