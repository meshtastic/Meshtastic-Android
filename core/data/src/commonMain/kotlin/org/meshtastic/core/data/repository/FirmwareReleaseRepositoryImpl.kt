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
import org.meshtastic.core.model.asFirmwareRelease
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

    /** Serializes target-manifest downloads and preserves successful results for the selected release URL. */
    private val manifestMutex = Mutex()
    private val manifestTargetsByUrl = mutableMapOf<String, Set<String>>()

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

    override val nightlyRelease: Flow<FirmwareRelease?> = getLatestFirmware(FirmwareReleaseType.NIGHTLY)

    override suspend fun getManifestTargets(release: FirmwareRelease): Set<String>? {
        val manifestUrl = release.zipUrl.takeIf { it.isNotBlank() } ?: return null
        return manifestMutex.withLock {
            manifestTargetsByUrl[manifestUrl]
                ?: safeCatching { remoteDataSource.getFirmwareReleaseManifest(manifestUrl) }
                    .onFailure { error -> Logger.w(error) { "FirmwareReleaseRepository: manifest fetch failed" } }
                    .getOrNull()
                    ?.targets
                    ?.map { target -> target.board.trim() }
                    ?.filter(String::isNotBlank)
                    ?.toSet()
                    ?.also { targets -> manifestTargetsByUrl[manifestUrl] = targets }
        }
    }

    private fun getLatestFirmware(releaseType: FirmwareReleaseType): Flow<FirmwareRelease?> = staleWhileRevalidateFlow(
        loadFromCache = {
            ensureSeeded()
            val latest = localDataSource.getLatestRelease(releaseType)?.asExternalModel()
            // NIGHTLY is exempt from the below-stable guard: it is an explicit opt-in preview channel.
            if (releaseType == FirmwareReleaseType.ALPHA) latest.notBelowStable() else latest
        },
        shouldFetch = { cached ->
            cached == null || localDataSource.getLatestRelease(releaseType)?.isStale() != false
        },
        // Nightly lives on meshtastic.github.io, not in the API's release list, so it refreshes on its own
        // path — regular (locked) users never hit the nightly URL because only unlocked UI collects that flow.
        fetch = { if (releaseType == FirmwareReleaseType.NIGHTLY) refreshNightly() else singleFlightRefresh() },
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
     * After a stable promotion the alpha channel lags behind stable — never offer a downgrade. Reads the cached stable
     * row (every refresh writes both types together, so it is as fresh as the alpha row) rather than combining with
     * [stableRelease], which would run a second revalidate pipeline — and potentially a duplicate network refresh — for
     * every alpha collector.
     */
    private suspend fun FirmwareRelease?.notBelowStable(): FirmwareRelease? {
        val stable = localDataSource.getLatestRelease(FirmwareReleaseType.STABLE)?.asExternalModel()
        return if (this != null && stable != null && asDeviceVersion() < stable.asDeviceVersion()) stable else this
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

        // seedMutex guards only the decode + snapshot cache; the DB apply runs outside it (so concurrent
        // collectors don't block on a Room write or on refreshMutex) and under refreshMutex (so it can't
        // race singleFlightRefresh and overwrite fresher data that just arrived from the API).
        val bundled =
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
                bundledSnapshot?.releases
            } ?: return

        safeCatching {
            refreshMutex.withLock {
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
            .onFailure { e -> Logger.w(e) { "FirmwareReleaseRepository: failed to apply bundled snapshot" } }
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

    private suspend fun refreshNightly() {
        refreshMutex.withLock {
            Logger.d { "FirmwareReleaseRepository: fetching nightly index" }
            // A 404 (nothing currently published) returns null and clears any stale nightly row; transport and
            // server errors throw before the write and leave the cache untouched.
            val nightly = remoteDataSource.getNightlyFirmware()?.asFirmwareRelease()
            localDataSource.replaceFirmwareReleases(mapOf(FirmwareReleaseType.NIGHTLY to listOfNotNull(nightly)))
        }
    }

    private fun FirmwareReleaseEntity.isStale(): Boolean = (nowMillis - this.lastUpdated) > CACHE_EXPIRATION_TIME_MS

    companion object {
        private val CACHE_EXPIRATION_TIME_MS = TimeConstants.ONE_HOUR.inWholeMilliseconds
    }
}
