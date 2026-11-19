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
package org.meshtastic.feature.connections.data

import kotlinx.coroutines.flow.Flow
import org.meshtastic.core.datastore.model.RecentAddress

/**
 * Feature-local seam over `core:datastore`'s `RecentAddressesDataSource`, which is Preferences-backed and so has no
 * wasmJs variant. The adapter lives in nonWebMain; web gets a no-op.
 */
interface RecentAddressesSource {
    val recentAddresses: Flow<List<RecentAddress>>

    suspend fun add(address: RecentAddress)

    suspend fun remove(address: String)
}
