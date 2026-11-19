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
package org.meshtastic.core.service

import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.meshtastic.core.repository.AppIconBadge
import org.meshtastic.core.repository.PacketRepository

/** Keeps [badge] on the unread message count the navigation bar shows, for as long as [run] is collecting. */
class UnreadBadgeSync(private val packetRepository: PacketRepository, private val badge: AppIconBadge) {
    suspend fun run() {
        packetRepository.getUnreadCountTotal().map { it.coerceAtLeast(0) }.distinctUntilChanged().collect(badge::show)
    }
}
