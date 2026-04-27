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
package org.meshtastic.wear.presentation

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.model.WearableMessage
import org.meshtastic.wear.presentation.model.WearableMessageRepository

@KoinViewModel
class MessagesViewModel(private val repository: WearableMessageRepository) : ViewModel() {
    val messages: StateFlow<List<WearableMessage>> = repository.syncedMessages
}
