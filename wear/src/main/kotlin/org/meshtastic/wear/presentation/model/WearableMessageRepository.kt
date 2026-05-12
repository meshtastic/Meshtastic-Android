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
package org.meshtastic.wear.presentation.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.core.annotation.Single
import org.meshtastic.core.model.WearableMessage

@Single
class WearableMessageRepository {
    private val _syncedMessages = MutableStateFlow<List<WearableMessage>>(emptyList())
    val syncedMessages: StateFlow<List<WearableMessage>> = _syncedMessages.asStateFlow()

    fun updateMessages(messages: List<WearableMessage>) {
        _syncedMessages.value = messages
    }

    fun addLocalMessage(message: WearableMessage) {
        _syncedMessages.value = (_syncedMessages.value + message).sortedByDescending { it.timestamp }
    }
}
