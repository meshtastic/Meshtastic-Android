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
package org.meshtastic.core.repository.di

import org.koin.core.annotation.Module
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import org.meshtastic.core.model.RadioController
import org.meshtastic.core.repository.HomoglyphPrefs
import org.meshtastic.core.repository.MessageQueue
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.usecase.SendMessageUseCase

@Module
class CoreRepositoryModule {
    @Single
    fun provideSendMessageUseCase(
        @Provided nodeRepository: NodeRepository,
        @Provided packetRepository: PacketRepository,
        @Provided radioController: RadioController,
        @Provided homoglyphEncodingPrefs: HomoglyphPrefs,
        @Provided messageQueue: MessageQueue,
    ): SendMessageUseCase =
        SendMessageUseCase(nodeRepository, packetRepository, radioController, homoglyphEncodingPrefs, messageQueue)
}
