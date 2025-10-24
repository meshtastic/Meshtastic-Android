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

package org.meshtastic.feature.messaging

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.meshtastic.core.data.repository.QuickChatActionRepository
import org.meshtastic.core.database.entity.QuickChatAction
import org.meshtastic.core.ui.viewmodel.stateInWhileSubscribed
import javax.inject.Inject

@HiltViewModel
class QuickChatViewModel @Inject constructor(private val quickChatActionRepository: QuickChatActionRepository) :
    ViewModel() {
    val quickChatActions
        get() = quickChatActionRepository.getAllActions().stateInWhileSubscribed(initialValue = emptyList())

    init {
        // Initialize default location action if database is empty
        viewModelScope.launch(Dispatchers.IO) {
            val actions = quickChatActionRepository.getAllActions()
            var isEmpty = true
            actions.collect { list ->
                if (isEmpty && list.isEmpty()) {
                    quickChatActionRepository.upsert(
                        QuickChatAction(
                            name = "📍",
                            message = "https://maps.google.com/?q=%GPS",
                            mode = QuickChatAction.Mode.Append,
                            position = 0,
                        )
                    )
                }
                isEmpty = false
            }
        }
    }

    fun updateActionPositions(actions: List<QuickChatAction>) {
        viewModelScope.launch(Dispatchers.IO) {
            for (position in actions.indices) {
                quickChatActionRepository.setItemPosition(actions[position].uuid, position)
            }
        }
    }

    fun addQuickChatAction(action: QuickChatAction) =
        viewModelScope.launch(Dispatchers.IO) { quickChatActionRepository.upsert(action) }

    fun deleteQuickChatAction(action: QuickChatAction) =
        viewModelScope.launch(Dispatchers.IO) { quickChatActionRepository.delete(action) }
}
