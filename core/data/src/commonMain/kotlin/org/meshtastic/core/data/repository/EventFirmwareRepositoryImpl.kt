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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.common.util.safeCatching
import org.meshtastic.core.data.datasource.BundledAssetReader
import org.meshtastic.core.data.datasource.EventFirmwareEditionLocalDataSource
import org.meshtastic.core.data.datasource.decode
import org.meshtastic.core.database.entity.asEntity
import org.meshtastic.core.database.entity.asExternalModel
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.EventFirmwareEdition
import org.meshtastic.core.model.EventFirmwareResponse
import org.meshtastic.core.model.util.TimeConstants
import org.meshtastic.core.network.EventFirmwareRemoteDataSource
import org.meshtastic.core.repository.EventFirmwareRepository
import kotlin.concurrent.Volatile

/**
 * Caches event-firmware display metadata in the DB, seeded from the bundled `event_firmware.json` snapshot and
 * refreshed from `/resource/eventFirmware`. DB-backed (not just in-memory) so the snapshot survives across process
 * restarts without waiting on a fresh network round-trip. Mirrors [DeviceHardwareRepositoryImpl]'s seed → single-flight
 * refresh pattern.
 */
@Single
class EventFirmwareRepositoryImpl(
    private val remoteDataSource: EventFirmwareRemoteDataSource,
    private val assetReader: BundledAssetReader,
    private val json: Json,
    private val localDataSource: EventFirmwareEditionLocalDataSource,
    private val dispatchers: CoroutineDispatchers,
) : EventFirmwareRepository {

    /** Serializes seeding so concurrent callers don't duplicate writes. */
    private val writeMutex = Mutex()

    @Volatile private var lastRefreshMillis = 0L

    /** Guards [inFlightRefresh] so concurrent callers share one network fetch. */
    private val refreshGuard = Mutex()

    private var inFlightRefresh: Deferred<Unit>? = null

    // This @Single lives for the entire app lifetime, so the SupervisorJob is never cancelled. The refresh runs
    // here so a caller that stops waiting can't abort the shared fetch — api.meshtastic.org has been measured
    // taking 20-60s, and the fetch should still land in the DB for the next lookup.
    private val refreshScope = CoroutineScope(dispatchers.io + SupervisorJob())

    override suspend fun getEdition(editionName: String): EventFirmwareEdition? = withContext(dispatchers.io) {
        ensureSeeded()
        if (nowMillis - lastRefreshMillis > CACHE_EXPIRATION_TIME_MS) {
            singleFlightRefresh(maxWaitMs = NETWORK_REFRESH_TIMEOUT_MS)
        }
        localDataSource.getByEdition(editionName)?.asExternalModel()
    }

    /** Seeds the table from the bundled snapshot if empty (fresh install, data clear). */
    private suspend fun ensureSeeded() {
        if (localDataSource.count() > 0) return
        writeMutex.withLock {
            if (localDataSource.count() == 0) {
                safeCatching {
                    val editions = assetReader.decode<EventFirmwareResponse>(ASSET_NAME, json)?.editions.orEmpty()
                    localDataSource.upsertAll(editions.map { it.asEntity() })
                }
                    .onFailure { e -> Logger.w(e) { "EventFirmwareRepository: failed to seed from bundled JSON" } }
            }
        }
    }

    /**
     * Starts (or joins) a single shared refresh running in [refreshScope]. The caller waits at most [maxWaitMs] before
     * falling back to cached data; the refresh itself always runs to completion, bounded only by the HttpClient's own
     * timeout/retry policy.
     */
    private suspend fun singleFlightRefresh(maxWaitMs: Long) {
        val refresh =
            refreshGuard.withLock {
                inFlightRefresh?.takeIf { it.isActive }
                    ?: refreshScope
                        .async {
                            safeCatching {
                                val editions = remoteDataSource.getEventFirmware().editions
                                if (editions.isNotEmpty()) {
                                    localDataSource.upsertAll(editions.map { it.asEntity() })
                                    localDataSource.deleteNotIn(editions.map { it.edition })
                                    lastRefreshMillis = nowMillis
                                }
                            }
                                .onFailure { e -> Logger.w(e) { "EventFirmwareRepository: network refresh failed" } }
                            Unit
                        }
                        .also { inFlightRefresh = it }
            }
        if (withTimeoutOrNull(maxWaitMs) { refresh.join() } == null) {
            Logger.w { "EventFirmwareRepository: refresh still in flight after ${maxWaitMs}ms; using cached data" }
        }
    }

    private companion object {
        private const val ASSET_NAME = "event_firmware.json"
        private val CACHE_EXPIRATION_TIME_MS = TimeConstants.ONE_DAY.inWholeMilliseconds

        /** Maximum time a blocking lookup waits for an in-flight refresh before returning cached/bundled data. */
        private const val NETWORK_REFRESH_TIMEOUT_MS = 5_000L
    }
}
