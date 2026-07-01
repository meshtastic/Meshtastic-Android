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
package org.meshtastic.core.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.meshtastic.core.model.MeshBeaconOffer

/**
 * Holds Mesh Beacon invitations received during this app session. Beacons are advisory, ephemeral, zero-hop
 * advertisements from other meshes — not messages or contacts — so they're kept in memory only (capped, no persistence)
 * and naturally age out on app restart. Consumed by the Discovery surface, which presents them for the user to Discover
 * / Join / Dismiss. Room persistence can be added later if users want invitations to survive restarts.
 */
class MeshBeaconRepository {
    private val _offers = MutableStateFlow<List<MeshBeaconOffer>>(emptyList())
    val offers: StateFlow<List<MeshBeaconOffer>> = _offers.asStateFlow()

    /**
     * Records a received [offer], replacing any prior offer with the same [key][MeshBeaconOffer.key] (a re-broadcast of
     * a standing invitation). Returns true when this is a newly-seen invitation, so the caller can notify only once
     * rather than on every periodic re-broadcast.
     */
    fun add(offer: MeshBeaconOffer): Boolean {
        val isNew = _offers.value.none { it.key == offer.key }
        _offers.update { current -> (listOf(offer) + current.filterNot { it.key == offer.key }).take(MAX_OFFERS) }
        return isNew
    }

    fun dismiss(key: String) {
        _offers.update { current -> current.filterNot { it.key == key } }
    }

    private companion object {
        const val MAX_OFFERS = 20
    }
}
