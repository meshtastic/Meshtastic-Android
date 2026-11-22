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
package org.meshtastic.core.data.manager

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import org.koin.core.annotation.Single
import org.meshtastic.core.repository.AdminRequestRegistry
import org.meshtastic.core.repository.OutstandingAdminRequest

@Single
class AdminRequestRegistryImpl : AdminRequestRegistry {
    // Oldest first. Bounded so a request the radio never answers cannot grow the set forever.
    private val outstanding = atomic<List<OutstandingAdminRequest>>(emptyList())

    override fun register(requestId: Int, destNum: Int, adminChannelIndex: Int?) {
        if (requestId == 0) return
        val request = OutstandingAdminRequest(requestId, destNum, adminChannelIndex)
        outstanding.update { requests ->
            (requests.filterNot { it.requestId == requestId } + request).takeLast(MAX_OUTSTANDING)
        }
    }

    override fun find(requestId: Int): OutstandingAdminRequest? =
        if (requestId == 0) null else outstanding.value.firstOrNull { it.requestId == requestId }

    override fun consume(requestId: Int): Boolean {
        if (requestId == 0) return false
        var found = false
        outstanding.update { requests ->
            found = requests.any { it.requestId == requestId }
            if (found) requests.filterNot { it.requestId == requestId } else requests
        }
        return found
    }

    override fun clearAll() {
        outstanding.value = emptyList()
    }

    private companion object {
        const val MAX_OUTSTANDING = 128
    }
}
