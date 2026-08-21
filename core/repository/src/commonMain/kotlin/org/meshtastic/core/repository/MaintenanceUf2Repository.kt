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

import org.meshtastic.core.model.MaintenanceUf2Manifest

/**
 * The pinned nRF52/RP2040 maintenance-UF2 manifest (factory-erase and OTAFIX bootloader self-update images), seeded
 * from a bundled asset and refreshed from `resource/maintenanceUf2`. Each image's own `sha256` is still checked against
 * its downloaded bytes before any write — see `MaintenanceUf2.kt` — this repository only caches the manifest that names
 * those images.
 */
interface MaintenanceUf2Repository {
    /** Current best-known manifest: bundled seed until a fetch has landed, then that fetch. */
    suspend fun getSnapshot(): MaintenanceUf2Manifest

    /** Best-effort network refresh. Never throws; a failed or empty fetch leaves the cache untouched. */
    suspend fun reconcile()
}
