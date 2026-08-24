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
package org.meshtastic.core.data.repository

import co.touchlab.kermit.Logger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.safeCatching
import org.meshtastic.core.data.datasource.BundledAssetReader
import org.meshtastic.core.data.datasource.MaintenanceUf2LocalDataSource
import org.meshtastic.core.data.datasource.decode
import org.meshtastic.core.database.entity.asEntity
import org.meshtastic.core.database.entity.asExternalModel
import org.meshtastic.core.model.MaintenanceUf2Manifest
import org.meshtastic.core.network.MaintenanceUf2RemoteDataSource
import org.meshtastic.core.repository.MaintenanceUf2Repository

/**
 * Caches the maintenance-UF2 manifest from the Meshtastic API (`/resource/maintenanceUf2`), seeded from the bundled
 * `maintenance_uf2.json` snapshot so it is never empty offline — mirrors [BootloaderOtaQuirksRepositoryImpl]'s
 * seed-then-refresh shape exactly, including its "reconcile carries no TTL of its own" contract:
 * [DeviceHardwareRepositoryImpl]'s refresher calls this on the same cadence as its catalog refresh.
 */
@Single
class MaintenanceUf2RepositoryImpl(
    private val remoteDataSource: MaintenanceUf2RemoteDataSource,
    private val localDataSource: MaintenanceUf2LocalDataSource,
    private val assetReader: BundledAssetReader,
    private val json: Json,
) : MaintenanceUf2Repository {

    /** Serializes seeding and network writes so concurrent callers don't duplicate/interleave them. */
    private val writeMutex = Mutex()

    override suspend fun getSnapshot(): MaintenanceUf2Manifest {
        ensureSeeded()
        return localDataSource.get()?.asExternalModel() ?: MaintenanceUf2Manifest()
    }

    override suspend fun reconcile() {
        safeCatching { remoteDataSource.getManifest() }
            .onSuccess { manifest -> writeMutex.withLock { store(manifest) } }
            .onFailure { e -> Logger.w(e) { "MaintenanceUf2Repository: network refresh failed" } }
    }

    /** Seeds the cache from the bundled snapshot if empty (fresh install, data clear). */
    private suspend fun ensureSeeded() {
        if (localDataSource.count() > 0) return
        writeMutex.withLock {
            if (localDataSource.count() == 0) {
                safeCatching {
                    val asset = assetReader.decode<MaintenanceUf2Manifest>("maintenance_uf2.json", json)
                    asset?.let { store(it) }
                }
                    .onFailure { e -> Logger.w(e) { "MaintenanceUf2Repository: failed to seed from bundled JSON" } }
            }
        }
    }

    /**
     * Not locked itself — every caller already holds [writeMutex] for the duration of a write.
     *
     * A manifest with neither erase images nor an OTAFIX board map is ignored rather than stored, so a fully empty
     * response can never wipe an existing seed/cache back to nothing. A *partial* response still replaces the cached
     * manifest wholesale — the server is authoritative for which images exist, so a dropped section means that section
     * was withdrawn, and the resolvers fail closed on the sections it no longer carries.
     */
    private suspend fun store(manifest: MaintenanceUf2Manifest) {
        if (manifest.erase == null && manifest.otafixByBoardId.isEmpty()) {
            Logger.w { "MaintenanceUf2Repository: empty response; leaving cache untouched" }
            return
        }
        localDataSource.upsert(manifest.asEntity())
    }
}
