/*
 * NTsocial MeshLink original work and modifications:
 * Copyright (c) 2026 LiberaNt LLC
 *
 * Meshtastic Android-derived portions, where present:
 * Copyright (c) 2026 Meshtastic LLC
 *
 * Developed and/or modified for NTsocial MeshLink in 2026.
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
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
package com.ntsocial.meshlink.ios.runtime

import com.ntsocial.meshlink.core.data.manager.SessionMessageQueue
import com.ntsocial.meshlink.core.di.CoroutineDispatchers
import com.ntsocial.meshlink.core.model.RadioController
import com.ntsocial.meshlink.core.repository.CommandSender
import com.ntsocial.meshlink.core.repository.GatewayIngressSessionGate
import com.ntsocial.meshlink.core.repository.MessageQueue
import com.ntsocial.meshlink.core.repository.PacketRepository
import com.ntsocial.meshlink.core.repository.RadioConfigRepository
import com.ntsocial.meshlink.core.repository.RadioInterfaceService
import kotlinx.coroutines.CoroutineScope

internal class IosDurableMessageQueue(
    packetRepository: PacketRepository,
    radioController: RadioController,
    commandSender: CommandSender,
    radioConfigRepository: RadioConfigRepository,
    radioInterfaceService: RadioInterfaceService,
    gatewayIngressSessionGate: GatewayIngressSessionGate,
    dispatchers: CoroutineDispatchers,
) : MessageQueue {
    private val queue =
        SessionMessageQueue(
            packetRepository,
            lazy { radioController },
            commandSender,
            radioConfigRepository,
            radioInterfaceService,
            gatewayIngressSessionGate,
            CoroutineScope(dispatchers.io),
        )

    fun start() = queue.start()

    override suspend fun enqueue(packetId: Int) = queue.enqueue(packetId)

    internal suspend fun drain() = queue.drain()

    fun close() = queue.close()
}
