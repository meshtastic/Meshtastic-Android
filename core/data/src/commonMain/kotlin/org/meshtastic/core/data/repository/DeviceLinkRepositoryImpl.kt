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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.common.util.safeCatching
import org.meshtastic.core.data.datasource.BundledAssetReader
import org.meshtastic.core.data.datasource.DeviceLinkLocalDataSource
import org.meshtastic.core.data.datasource.decode
import org.meshtastic.core.data.util.SingleFlightRefresher
import org.meshtastic.core.database.entity.asEntity
import org.meshtastic.core.database.entity.asExternalModel
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.DeviceLink
import org.meshtastic.core.model.NetworkDeviceLink
import org.meshtastic.core.model.NetworkDeviceLinksResponse
import org.meshtastic.core.model.toDeviceLink
import org.meshtastic.core.model.util.TimeConstants
import org.meshtastic.core.network.DeviceLinksRemoteDataSource
import org.meshtastic.core.repository.DeviceLinkRepository
import kotlin.concurrent.Volatile

/**
 * Caches the resolved device-links catalog from the Meshtastic API (`/resource/deviceLinks`). The server does all the
 * classification (type/targets/regions), so the client just seeds from a bundled snapshot, refreshes from the network,
 * and filters the cache. Mirrors [DeviceHardwareRepositoryImpl]'s seed → single-flight refresh pattern.
 */
@Single
class DeviceLinkRepositoryImpl(
    private val remoteDataSource: DeviceLinksRemoteDataSource,
    private val assetReader: BundledAssetReader,
    private val json: Json,
    private val localDataSource: DeviceLinkLocalDataSource,
    private val dispatchers: CoroutineDispatchers,
) : DeviceLinkRepository {

    /** Serializes seeding and network refreshes so concurrent collectors don't duplicate writes. */
    private val writeMutex = Mutex()

    @Volatile private var lastRefreshMillis = 0L

    /** Single-flights stale-triggered refreshes; the TTL is re-checked inside so late joiners don't re-fetch. */
    private val staleRefresher =
        SingleFlightRefresher(dispatchers.io, "DeviceLinkRepository") {
            if (nowMillis - lastRefreshMillis > CACHE_EXPIRATION_TIME_MS) reconcile()
        }

    override suspend fun ensureImported() {
        ensureSeeded()
    }

    override suspend fun reconcile() {
        safeCatching {
            // The network call is bounded by the HttpClient's own timeout/retry policy — api.meshtastic.org has
            // been measured taking 20-60s, so a short local deadline would cancel every refresh. It runs outside
            // writeMutex so seeding (and therefore first emission) is never blocked behind a slow fetch. The DB
            // write is NonCancellable so a cancelled caller can't leave the cache half-pruned.
            val remoteLinks = remoteDataSource.getDeviceLinks()
            writeMutex.withLock {
                withContext(NonCancellable + dispatchers.io) {
                    // Only an applied payload counts as fresh — an ignored empty response must not suppress
                    // retries for a whole expiration window while the cache stays stale.
                    if (store(remoteLinks)) {
                        lastRefreshMillis = nowMillis
                    }
                }
            }
        }
            .onFailure { e -> Logger.w(e) { "DeviceLinkRepository: network refresh failed" } }
    }

    override suspend fun getLinksForTarget(platformioTarget: String, regionCode: String): List<DeviceLink> =
        withContext(dispatchers.io) {
            if (platformioTarget.isBlank()) return@withContext emptyList()
            ensureSeeded()
            // Deliberately non-blocking: the node-detail flow must stay snappy. Freshness arrives via reconcile(),
            // triggered by the device-hardware refresh that resolves this device's hardware in the first place.
            localDataSource
                .getAll()
                .map { it.asExternalModel() }
                .filter { link -> platformioTarget in link.targets.orEmpty() }
                .filter { link ->
                    val regions = link.regions
                    regions.isNullOrEmpty() || regionCode in regions
                }
                .sortedByDescending { it.isVendor }
        }

    override fun observeAllLinks(): Flow<List<DeviceLink>> = flow {
        ensureSeeded()
        coroutineScope {
            // Refresh concurrently so a slow API response can't delay the first emission; Room re-emits when the
            // refreshed rows land.
            launch { refreshIfStale() }
            emitAll(localDataSource.observeAll().map { entities -> entities.map { it.asExternalModel() } })
        }
    }

    /** Seeds the table from the bundled snapshot if empty (fresh install, data clear, radio switch). */
    private suspend fun ensureSeeded() {
        if (localDataSource.count() > 0) return
        writeMutex.withLock {
            if (localDataSource.count() == 0) {
                safeCatching {
                    store(
                        assetReader.decode<NetworkDeviceLinksResponse>("device_links.json", json)?.links.orEmpty(),
                    )
                }
                    .onFailure { e -> Logger.w(e) { "DeviceLinkRepository: failed to seed from bundled JSON" } }
            }
        }
    }

    /** Best-effort network refresh, gated by [CACHE_EXPIRATION_TIME_MS] and single-flighted via [staleRefresher]. */
    private suspend fun refreshIfStale() {
        if (nowMillis - lastRefreshMillis <= CACHE_EXPIRATION_TIME_MS) return
        staleRefresher.refresh()
    }

    /**
     * Maps resolved API links to the cached domain model, upserts them, and prunes short codes that no longer exist.
     * Internal links (GitHub, YouTube, …) are dropped — they never belong to a device's purchase section. An empty list
     * is ignored rather than wiping the cache on a bad response. Returns whether anything was stored.
     */
    private suspend fun store(networkLinks: List<NetworkDeviceLink>): Boolean {
        val links = networkLinks.filter { it.type != NetworkDeviceLink.TYPE_INTERNAL }.map { it.toDeviceLink() }
        if (links.isEmpty()) {
            Logger.w { "DeviceLinkRepository: no device links to store; leaving cache untouched" }
            return false
        }
        localDataSource.upsertAll(links.map { it.asEntity() })
        localDataSource.deleteNotIn(links.map { it.shortCode })
        Logger.i { "DeviceLinkRepository: stored ${links.size} device links" }
        return true
    }

    private companion object {
        private val CACHE_EXPIRATION_TIME_MS = TimeConstants.ONE_DAY.inWholeMilliseconds
    }
}
