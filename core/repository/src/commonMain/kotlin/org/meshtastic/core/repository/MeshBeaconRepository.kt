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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.meshtastic.core.model.MeshBeaconOffer

/**
 * Holds Mesh Beacon invitations received from other meshes. Beacons are advisory, zero-hop advertisements — not
 * messages or contacts — so they live in the Discovery surface, which presents them for the user to Discover / Join /
 * Dismiss. The list is capped and deduped in memory (the source of truth) and written through to [MeshBeaconPrefs] so
 * invitations survive an app restart until the user explicitly dismisses them (Apple `014-mesh-beacons` FR-015).
 *
 * ponytail: persisted as opaque delimited records in a Preferences DataStore, not a Room table — the set is tiny (≤
 * [MAX_OFFERS]) and never queried. Promote to a Room entity alongside the discovery tables if querying/joins appear.
 */
class MeshBeaconRepository(private val prefs: MeshBeaconPrefs, scope: CoroutineScope) {
    private val _offers = MutableStateFlow<List<MeshBeaconOffer>>(emptyList())
    val offers: StateFlow<List<MeshBeaconOffer>> = _offers.asStateFlow()

    init {
        // Hydrate once from disk. Memory is authoritative afterwards, so a live beacon that arrives before the async
        // DataStore load wins; the guard also makes our own write-through re-emissions no-ops.
        scope.launch {
            prefs.storedBeacons.collect { records ->
                if (_offers.value.isEmpty() && records.isNotEmpty()) {
                    _offers.value = records.mapNotNull(MeshBeaconOffer::decode).take(MAX_OFFERS)
                }
            }
        }
    }

    /**
     * Records a received [offer], replacing any prior offer with the same [key][MeshBeaconOffer.key] (a re-broadcast of
     * a standing invitation). Returns true when this is a newly-seen invitation, so the caller can notify only once
     * rather than on every periodic re-broadcast.
     */
    fun add(offer: MeshBeaconOffer): Boolean {
        val isNew = _offers.value.none { it.key == offer.key }
        _offers.update { current -> (listOf(offer) + current.filterNot { it.key == offer.key }).take(MAX_OFFERS) }
        persist()
        return isNew
    }

    fun dismiss(key: String) {
        _offers.update { current -> current.filterNot { it.key == key } }
        persist()
    }

    private fun persist() = prefs.setStoredBeacons(_offers.value.map { it.encode() })

    private companion object {
        const val MAX_OFFERS = 20
    }
}
