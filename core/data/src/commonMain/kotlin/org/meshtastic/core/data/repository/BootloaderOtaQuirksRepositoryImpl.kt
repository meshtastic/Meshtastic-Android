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
import org.meshtastic.core.data.datasource.BootloaderOtaQuirksLocalDataSource
import org.meshtastic.core.data.datasource.BundledAssetReader
import org.meshtastic.core.data.datasource.decode
import org.meshtastic.core.database.entity.asEntity
import org.meshtastic.core.database.entity.asExternalModel
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.BootloaderOtaQuirksResponse
import org.meshtastic.core.network.BootloaderOtaQuirksRemoteDataSource
import org.meshtastic.core.repository.BootloaderOtaQuirksRepository

/**
 * Caches the nRF52 bootloader/OTA quirk catalog from the Meshtastic API (`/resource/bootloaderOtaQuirks`), seeded from
 * the bundled `device_bootloader_ota_quirks.json` snapshot so it is never empty offline. Unlike
 * [DeviceHardwareRepositoryImpl] (which this mirrors for seeding), [reconcile] carries no TTL of its own —
 * [DeviceHardwareRepositoryImpl]'s own refresher calls it on the same cadence as its catalog refresh, so the two caches
 * age together instead of drifting on separate schedules.
 */
@Single
class BootloaderOtaQuirksRepositoryImpl(
    private val remoteDataSource: BootloaderOtaQuirksRemoteDataSource,
    private val localDataSource: BootloaderOtaQuirksLocalDataSource,
    private val assetReader: BundledAssetReader,
    private val json: Json,
    private val dispatchers: CoroutineDispatchers,
) : BootloaderOtaQuirksRepository {

    /** Serializes seeding and network writes so concurrent callers don't duplicate/interleave them. */
    private val writeMutex = Mutex()

    override suspend fun getSnapshot(): BootloaderOtaQuirksResponse {
        ensureSeeded()
        return localDataSource.get()?.asExternalModel() ?: BootloaderOtaQuirksResponse()
    }

    override suspend fun reconcile() {
        safeCatching { remoteDataSource.getBootloaderOtaQuirks() }
            .onSuccess { response -> writeMutex.withLock { store(response) } }
            .onFailure { e -> Logger.w(e) { "BootloaderOtaQuirksRepository: network refresh failed" } }
    }

    /** Seeds the cache from the bundled snapshot if empty (fresh install, data clear). */
    private suspend fun ensureSeeded() {
        if (localDataSource.count() > 0) return
        writeMutex.withLock {
            if (localDataSource.count() == 0) {
                safeCatching {
                    val asset =
                        assetReader.decode<BootloaderOtaQuirksResponse>("device_bootloader_ota_quirks.json", json)
                    asset?.let { store(it) }
                }
                    .onFailure { e ->
                        Logger.w(e) { "BootloaderOtaQuirksRepository: failed to seed from bundled JSON" }
                    }
            }
        }
    }

    /**
     * Not locked itself — every caller already holds [writeMutex] for the duration of a write. An entirely empty
     * response (no devices AND no softDeviceVariants) is ignored rather than stored, so a bad or transient response can
     * never wipe an existing seed/cache back to nothing.
     */
    private suspend fun store(response: BootloaderOtaQuirksResponse) {
        if (response.devices.isEmpty() && response.softDeviceVariants.isEmpty()) {
            Logger.w { "BootloaderOtaQuirksRepository: empty response; leaving cache untouched" }
            return
        }
        localDataSource.upsert(response.asEntity())
    }
}
