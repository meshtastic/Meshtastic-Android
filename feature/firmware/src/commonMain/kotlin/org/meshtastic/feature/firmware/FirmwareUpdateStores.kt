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

import kotlinx.coroutines.flow.Flow
import org.meshtastic.core.datastore.model.PendingFirmwareRecovery

/**
 * Feature-local seam over core:datastore's `BootloaderWarningDataSource`, which is Preferences-backed and has no wasmJs
 * variant: the radios whose bootloader warning the user dismissed.
 */
interface BootloaderWarningSource {
    suspend fun isDismissed(address: String): Boolean

    suspend fun dismiss(address: String)
}

/**
 * Feature-local seam over core:datastore's `FirmwareRecoveryDataSource`, likewise Preferences-backed: the record of an
 * interrupted BLE update that may have stranded a radio in its bootloader.
 */
interface FirmwareRecoverySource {
    val pending: Flow<PendingFirmwareRecovery?>

    suspend fun set(recovery: PendingFirmwareRecovery)

    suspend fun clear()
}
