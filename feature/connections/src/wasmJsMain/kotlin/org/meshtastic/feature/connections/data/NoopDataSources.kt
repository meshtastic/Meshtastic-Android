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
import kotlinx.coroutines.flow.flowOf
import org.koin.core.annotation.Single
import org.meshtastic.core.datastore.model.PendingFirmwareRecovery
import org.meshtastic.core.datastore.model.RecentAddress

/**
 * wasmJs binding: no recent-TCP-address persistence on web (the real DataSource is Preferences-backed with no wasmJs
 * target — see [RecentAddressesSource]). [DEFERRED]: a real implementation could be written against `localStorage`,
 * mirroring core:datastore's own `LocalStorageStore`, once a webApp module exists to wire it in.
 */
@Single
class NoopRecentAddressesSource : RecentAddressesSource {
    override val recentAddresses: Flow<List<RecentAddress>> = flowOf(emptyList())

    override suspend fun add(address: RecentAddress) = Unit

    override suspend fun remove(address: String) = Unit
}

/** wasmJs binding: no firmware-recovery banner on web — see [NoopRecentAddressesSource]. */
@Single
class NoopPendingFirmwareRecoverySource : PendingFirmwareRecoverySource {
    override val pending: Flow<PendingFirmwareRecovery?> = flowOf(null)

    override suspend fun clear() = Unit
}
