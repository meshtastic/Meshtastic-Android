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

import kotlinx.coroutines.flow.Flow
import org.meshtastic.core.database.entity.QuickChatAction
import org.meshtastic.core.repository.QuickChatActionRepository

/**
 * A test double for [QuickChatActionRepository] that keeps actions in an in-memory list (sorted by `position`).
 *
 * The in-memory list is exposed reactively through [getAllActions].
 */
class FakeQuickChatActionRepository :
    BaseFake(),
    QuickChatActionRepository {

    private val actionsFlow = mutableStateFlow<List<QuickChatAction>>(emptyList())

    override fun getAllActions(): Flow<List<QuickChatAction>> = actionsFlow

    override suspend fun upsert(action: QuickChatAction) {
        val existingIndex = actionsFlow.value.indexOfFirst { it.uuid == action.uuid }
        actionsFlow.value =
            if (existingIndex >= 0) {
                actionsFlow.value.toMutableList().also { it[existingIndex] = action }
            } else {
                actionsFlow.value + action
            }
                .sortedBy { it.position }
    }

    override suspend fun deleteAll() {
        actionsFlow.value = emptyList()
    }

    override suspend fun delete(action: QuickChatAction) {
        actionsFlow.value =
            actionsFlow.value
                .filterNot { it.uuid == action.uuid }
                .map { if (it.position > action.position) it.copy(position = it.position - 1) else it }
    }

    override suspend fun setItemPosition(uuid: Long, newPos: Int) {
        actionsFlow.value =
            actionsFlow.value.map { if (it.uuid == uuid) it.copy(position = newPos) else it }.sortedBy { it.position }
    }

    /** Seeds the current list of actions (useful for test setup). */
    fun setActions(actions: List<QuickChatAction>) {
        actionsFlow.value = actions.sortedBy { it.position }
    }

    /** Returns the current in-memory snapshot. */
    val currentActions: List<QuickChatAction>
        get() = actionsFlow.value
}
