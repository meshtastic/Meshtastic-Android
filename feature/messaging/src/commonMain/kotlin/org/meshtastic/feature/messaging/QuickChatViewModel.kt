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
package org.meshtastic.feature.messaging

import androidx.lifecycle.ViewModel
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.common.util.ioDispatcher
import org.meshtastic.core.database.entity.QuickChatAction
import org.meshtastic.core.repository.QuickChatActionRepository
import org.meshtastic.core.ui.viewmodel.safeLaunch
import org.meshtastic.core.ui.viewmodel.stateInWhileSubscribed

@KoinViewModel
class QuickChatViewModel(private val quickChatActionRepository: QuickChatActionRepository) : ViewModel() {
    val quickChatActions
        get() = quickChatActionRepository.getAllActions().stateInWhileSubscribed(initialValue = emptyList())

    fun updateActionPositions(actions: List<QuickChatAction>) {
        safeLaunch(context = ioDispatcher, tag = "updateActionPositions") {
            for (position in actions.indices) {
                quickChatActionRepository.setItemPosition(actions[position].uuid, position)
            }
        }
    }

    fun addQuickChatAction(action: QuickChatAction) =
        safeLaunch(context = ioDispatcher, tag = "addQuickChatAction") { quickChatActionRepository.upsert(action) }

    fun deleteQuickChatAction(action: QuickChatAction) =
        safeLaunch(context = ioDispatcher, tag = "deleteQuickChatAction") { quickChatActionRepository.delete(action) }
}
