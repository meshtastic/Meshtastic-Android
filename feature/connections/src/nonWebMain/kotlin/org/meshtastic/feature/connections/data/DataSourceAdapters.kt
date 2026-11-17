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

import org.koin.core.annotation.Single
import org.meshtastic.core.datastore.FirmwareRecoveryDataSource
import org.meshtastic.core.datastore.RecentAddressesDataSource
import org.meshtastic.core.datastore.model.RecentAddress

/** Delegates to the Preferences-backed `RecentAddressesDataSource`. */
@Single
class RecentAddressesSourceAdapter(private val delegate: RecentAddressesDataSource) : RecentAddressesSource {
    override val recentAddresses = delegate.recentAddresses

    override suspend fun add(address: RecentAddress) = delegate.add(address)

    override suspend fun remove(address: String) = delegate.remove(address)
}

/** Delegates to the Preferences-backed `FirmwareRecoveryDataSource`. */
@Single
class PendingFirmwareRecoverySourceAdapter(private val delegate: FirmwareRecoveryDataSource) :
    PendingFirmwareRecoverySource {
    override val pending = delegate.pending

    override val lastBleNode = delegate.lastBleNode

    override suspend fun promoteLastBleNode(fullAddress: String) = delegate.promoteLastBleNode(fullAddress)

    override suspend fun dismiss() = delegate.dismiss()

    override suspend fun clear() = delegate.clear()
}
