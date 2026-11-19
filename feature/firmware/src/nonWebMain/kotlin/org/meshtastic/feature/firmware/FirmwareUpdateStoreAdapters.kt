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
package org.meshtastic.feature.firmware

import org.koin.core.annotation.Single
import org.meshtastic.core.datastore.BootloaderWarningDataSource
import org.meshtastic.core.datastore.FirmwareRecoveryDataSource
import org.meshtastic.core.datastore.model.PendingFirmwareRecovery

/** Delegates to the Preferences-backed `BootloaderWarningDataSource`. */
@Single
class BootloaderWarningSourceAdapter(private val delegate: BootloaderWarningDataSource) : BootloaderWarningSource {
    override suspend fun isDismissed(address: String): Boolean = delegate.isDismissed(address)

    override suspend fun dismiss(address: String) = delegate.dismiss(address)
}

/** Delegates to the Preferences-backed `FirmwareRecoveryDataSource`. */
@Single
class FirmwareRecoverySourceAdapter(private val delegate: FirmwareRecoveryDataSource) : FirmwareRecoverySource {
    override val pending = delegate.pending

    override suspend fun set(recovery: PendingFirmwareRecovery) = delegate.set(recovery)

    override suspend fun clear() = delegate.clear()
}
