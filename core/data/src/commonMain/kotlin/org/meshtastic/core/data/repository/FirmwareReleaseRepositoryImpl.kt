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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.common.util.safeCatching
import org.meshtastic.core.data.datasource.BundledAssetReader
import org.meshtastic.core.data.datasource.FirmwareReleaseLocalDataSource
import org.meshtastic.core.data.datasource.decode
import org.meshtastic.core.data.util.staleWhileRevalidateFlow
import org.meshtastic.core.database.entity.FirmwareRelease
import org.meshtastic.core.database.entity.FirmwareReleaseEntity
import org.meshtastic.core.database.entity.FirmwareReleaseType
import org.meshtastic.core.database.entity.asDeviceVersion
import org.meshtastic.core.database.entity.asEntity
import org.meshtastic.core.database.entity.asExternalModel
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.NetworkFirmwareRelease
import org.meshtastic.core.model.NetworkFirmwareReleases
import org.meshtastic.core.model.util.TimeConstants
import org.meshtastic.core.network.FirmwareReleaseRemoteDataSource
import org.meshtastic.core.repository.FirmwareReleaseRepository
import kotlin.concurrent.Volatile

@Single
open class FirmwareReleaseRepositoryImpl(
    private val remoteDataSource: FirmwareReleaseRemoteDataSource,
    private val localDataSource: FirmwareReleaseLocalDataSource,
    private val assetReader: BundledAssetReader,
    private val json: Json,
    private val dispatchers: CoroutineDispatchers,
) : FirmwareReleaseRepository {

    /** Single-flight guard so concurrent collectors share one network refresh. */
    private val refreshMutex = Mutex()

    /**
     * Guards [bundledSnapshot] decode so concurrent collectors decode the bundled JSON at most once per process. The
     * apply/skip decision itself is re-evaluated every time against the CURRENT active DB — the active Room database
     * switches per selected device, so a one-shot seed gate would miss a freshly activated DB whose `firmware_release`
     * rows are empty.
     */
    private val seedMutex = Mutex()

    /** Decoded bundled snapshot cached for the process lifetime; the asset file never changes between launches. */
    @Volatile private var bundledSnapshot: NetworkFirmwareReleases? = null

    /** Set when the bundled asset is missing or un-decodable, so we don't retry on every collection. */
    @Volatile private var bundleDecodeFailed = false

    override val stableRelease: Flow<FirmwareRelease?> = getLatestFirmware(FirmwareReleaseType.STABLE)

    override val alphaRelease: Flow<FirmwareRelease?> = getLatestFirmware(FirmwareReleaseType.ALPHA)

    private fun getLatestFirmware(releaseType: FirmwareReleaseType): Flow<FirmwareRelease?> = staleWhileRevalidateFlow(
        loadFromCache = {
            ensureSeeded()
            localDataSource.getLatestRelease(releaseType)?.asExternalModel()
        },
        shouldFetch = { cached ->
            cached == null || localDataSource.getLatestRelease(releaseType)?.isStale() != false
        },
        fetch = { singleFlightRefresh() },
        context = dispatchers.default,
        // No collector blocks on the fetch (cache is emitted first), so let the HttpClient's own
        // timeout/retry policy bound it — api.meshtastic.org routinely takes 20-60s to serve this list,
        // and a short deadline here cancels every refresh, pinning users to the bundled seed data.
        networkTimeoutMs = null,
        tag = "FirmwareReleaseRepository",
    )

    override suspend fun invalidateCache() {
        localDataSource.deleteAllFirmwareReleases()
    }

    /**
     * Applies the bundled snapshot per release type whenever it is newer than what is cached for that type — not just
     * when the cache is empty. The bundle is refreshed weekly in CI, so an app update carries fresh data even for users
     * whose network path to api.meshtastic.org chronically fails. The decoded snapshot is cached for the process; the
     * apply/skip decision is re-evaluated every call against the CURRENT active DB, because that DB switches per
     * selected device and a one-shot seed gate would skip a freshly activated DB whose `firmware_release` rows are
     * empty. A cache that is already newer (from a successful network refresh) is never regressed, and a type the
     * bundle doesn't ship is left untouched.
     */
    private suspend fun ensureSeeded() {
        if (bundleDecodeFailed) return // don't retry the bundled asset on every collection once it has failed
        seedMutex.withLock {
            // Decode the bundled JSON once per process; the asset never changes between launches.
            if (bundledSnapshot == null && !bundleDecodeFailed) {
                safeCatching { assetReader.decode<NetworkFirmwareReleases>("firmware_releases.json", json) }
                    .onSuccess { snapshot -> bundledSnapshot = snapshot }
                    .onFailure { e ->
                        Logger.w(e) { "FirmwareReleaseRepository: failed to decode bundled JSON" }
                        bundleDecodeFailed = true
                    }
                // Decode returning null (asset missing) also stops further retries.
                if (bundledSnapshot == null && !bundleDecodeFailed) {
                    Logger.w { "FirmwareReleaseRepository: no bundled releases available to seed from" }
                    bundleDecodeFailed = true
                }
            }

            val bundled = bundledSnapshot?.releases ?: return

            // Re-evaluate against the current active DB on every call — it may have switched devices.
            val toApply =
                listOf(FirmwareReleaseType.STABLE to bundled.stable, FirmwareReleaseType.ALPHA to bundled.alpha)
                    .filter { (type, releases) -> isBundleNewerFor(type, releases) }
                    .toMap()
            if (toApply.isNotEmpty()) {
                Logger.i { "FirmwareReleaseRepository: applying bundled snapshot for ${toApply.keys}" }
                localDataSource.replaceFirmwareReleases(toApply)
            }
        }
    }

    /** True when [bundled] contains a release newer than anything cached for [type]. */
    private suspend fun isBundleNewerFor(type: FirmwareReleaseType, bundled: List<NetworkFirmwareRelease>): Boolean {
        val bundledNewest = bundled.maxOfOrNull { it.asEntity(type).asDeviceVersion() } ?: return false
        val cachedNewest = localDataSource.getLatestRelease(type)?.asDeviceVersion()
        return cachedNewest == null || bundledNewest > cachedNewest
    }

    private suspend fun singleFlightRefresh() {
        refreshMutex.withLock {
            Logger.d { "FirmwareReleaseRepository: fetching from remote API" }
            val releases = remoteDataSource.getFirmwareReleases().releases
            if (releases.stable.isEmpty() && releases.alpha.isEmpty()) {
                Logger.w { "FirmwareReleaseRepository: remote returned no releases; leaving cache untouched" }
            } else {
                // Replace rather than upsert so releases pulled or reclassified upstream don't linger as "latest".
                localDataSource.replaceFirmwareReleases(
                    mapOf(FirmwareReleaseType.STABLE to releases.stable, FirmwareReleaseType.ALPHA to releases.alpha),
                )
            }
        }
    }

    private fun FirmwareReleaseEntity.isStale(): Boolean = (nowMillis - this.lastUpdated) > CACHE_EXPIRATION_TIME_MS

    companion object {
        private val CACHE_EXPIRATION_TIME_MS = TimeConstants.ONE_HOUR.inWholeMilliseconds
    }
}
