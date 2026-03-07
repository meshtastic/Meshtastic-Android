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
package org.meshtastic.app.messaging

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import org.meshtastic.core.data.repository.QuickChatActionRepository
import org.meshtastic.core.repository.CustomEmojiPrefs
import org.meshtastic.core.repository.HomoglyphPrefs
import org.meshtastic.core.repository.MeshServiceNotifications
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.repository.UiPrefs
import org.meshtastic.core.repository.usecase.SendMessageUseCase
import org.meshtastic.feature.messaging.MessageViewModel
import javax.inject.Inject

@Suppress("LongParameterList")
@HiltViewModel
class AndroidMessageViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    nodeRepository: NodeRepository,
    radioConfigRepository: RadioConfigRepository,
    quickChatActionRepository: QuickChatActionRepository,
    serviceRepository: ServiceRepository,
    packetRepository: PacketRepository,
    uiPrefs: UiPrefs,
    customEmojiPrefs: CustomEmojiPrefs,
    homoglyphEncodingPrefs: HomoglyphPrefs,
    meshServiceNotifications: MeshServiceNotifications,
    sendMessageUseCase: SendMessageUseCase,
) : MessageViewModel(
    savedStateHandle,
    nodeRepository,
    radioConfigRepository,
    quickChatActionRepository,
    serviceRepository,
    packetRepository,
    uiPrefs,
    customEmojiPrefs,
    homoglyphEncodingPrefs,
    meshServiceNotifications,
    sendMessageUseCase,
)
