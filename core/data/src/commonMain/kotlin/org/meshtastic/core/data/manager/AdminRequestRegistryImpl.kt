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

@Single
class AdminRequestRegistryImpl : AdminRequestRegistry {
    // Oldest first. Bounded so a request the radio never answers cannot grow the set forever.
    private val outstanding = atomic<List<Int>>(emptyList())

    override fun register(requestId: Int) {
        if (requestId == 0) return
        outstanding.update { ids -> (ids - requestId + requestId).takeLast(MAX_OUTSTANDING) }
    }

    override fun consume(requestId: Int): Boolean {
        if (requestId == 0) return false
        var found = false
        outstanding.update { ids ->
            found = requestId in ids
            if (found) ids - requestId else ids
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
