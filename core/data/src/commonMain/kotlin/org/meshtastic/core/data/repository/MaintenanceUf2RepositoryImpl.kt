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
import okio.ByteString.Companion.toByteString
import okio.buffer
import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.safeCatching
import org.meshtastic.core.data.datasource.BundledAssetReader
import org.meshtastic.core.data.datasource.MaintenanceUf2LocalDataSource
import org.meshtastic.core.database.entity.MaintenanceUf2CacheEntity
import org.meshtastic.core.model.MaintenanceUf2Manifest
import org.meshtastic.core.network.MaintenanceUf2RemoteDataSource
import org.meshtastic.core.repository.MaintenanceUf2Repository

/**
 * Caches the maintenance-UF2 manifest from the Meshtastic API (`/resource/maintenanceUf2`), seeded from the bundled
 * `maintenance_uf2.json` snapshot so it is never empty offline — mirrors [BootloaderOtaQuirksRepositoryImpl]'s
 * seed-then-refresh shape.
 *
 * The one thing that differs, and is load-bearing: this manifest gates an irreversible bootloader/erase write (the
 * wrong OTAFIX image, or the wrong erase image against a device's SoftDevice, bricks the radio — recoverable only via
 * SWD/serial DFU). So unlike the bootloader-quirks catalog, a fetch (and the bundled asset itself) is only ever trusted
 * if its raw bytes hash to [EXPECTED_MANIFEST_SHA256] — a mismatch is treated exactly like a network failure: logged,
 * and the existing cache (or nothing, if this is the first-run seed) is left untouched. This is the same "disagreement
 * refuses, never guesses" discipline `MaintenanceUf2.kt`'s own `resolveNrfEraseImage` already applies to the SoftDevice
 * a mounted drive reports.
 *
 * Stores the manifest's raw bytes verbatim (not a decoded/re-serialized model) so a cached row can be re-verified
 * against the pin on every read too, not just at fetch time.
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
        val bytes = localDataSource.get()?.manifestBytes ?: return MaintenanceUf2Manifest()
        return decodePinned(bytes)
    }

    /**
     * A row that no longer matches the pin (e.g. this build's pin was rolled back) is treated as absent rather than
     * parsed — never resolve maintenance images from bytes we would not have trusted on fetch.
     */
    private fun decodePinned(bytes: ByteArray): MaintenanceUf2Manifest {
        if (!matchesPin(bytes)) {
            Logger.e { "MaintenanceUf2Repository: cached manifest failed digest pin; treating as empty" }
            return MaintenanceUf2Manifest()
        }
        return safeCatching { json.decodeFromString<MaintenanceUf2Manifest>(bytes.decodeToString()) }
            .onFailure { e -> Logger.w(e) { "MaintenanceUf2Repository: cached manifest failed to parse" } }
            .getOrDefault(MaintenanceUf2Manifest())
    }

    override suspend fun reconcile() {
        safeCatching { remoteDataSource.getManifestBytes() }
            .onSuccess { bytes ->
                if (matchesPin(bytes)) {
                    writeMutex.withLock { localDataSource.upsert(MaintenanceUf2CacheEntity(manifestBytes = bytes)) }
                } else {
                    Logger.e { "MaintenanceUf2Repository: fetched manifest failed digest pin; discarding" }
                }
            }
            .onFailure { e -> Logger.w(e) { "MaintenanceUf2Repository: network refresh failed" } }
    }

    /** Seeds the cache from the bundled snapshot if empty (fresh install, data clear). */
    private suspend fun ensureSeeded() {
        if (localDataSource.count() > 0) return
        writeMutex.withLock {
            if (localDataSource.count() == 0) {
                safeCatching {
                    val bytes = readBundledAsset() ?: return@safeCatching
                    if (matchesPin(bytes)) {
                        localDataSource.upsert(MaintenanceUf2CacheEntity(manifestBytes = bytes))
                    } else {
                        // A future edit to the bundled asset without updating EXPECTED_MANIFEST_SHA256 (or vice
                        // versa) must fail loudly rather than silently seed unverified bytes.
                        Logger.e { "MaintenanceUf2Repository: bundled asset failed digest pin; refusing to seed" }
                    }
                }
                    .onFailure { e -> Logger.w(e) { "MaintenanceUf2Repository: failed to seed from bundled JSON" } }
            }
        }
    }

    // okio.Source doesn't extend AutoCloseable in commonMain — close explicitly, matching BundledAssetReader.decode.
    private fun readBundledAsset(): ByteArray? {
        val source = (assetReader.open(BUNDLED_ASSET_NAME) ?: return null).buffer()
        return try {
            source.readByteArray()
        } finally {
            source.close()
        }
    }

    private fun matchesPin(bytes: ByteArray): Boolean =
        bytes.toByteString().sha256().hex().equals(EXPECTED_MANIFEST_SHA256, ignoreCase = true)

    companion object {
        /**
         * SHA-256 of `data/maintenanceUf2.json` in `meshtastic/api` (and of the byte-identical bundled
         * `maintenance_uf2.json` asset here), as of the 2026-08-20 erase{}/otafixBoardSlug naming fixup (same OTAFIX
         * 0.9.2-OTAFIX2.3-BP1.5 release data, restructured). Bump this, and the bundled asset, together — a test
         * asserts they match.
         */
        const val EXPECTED_MANIFEST_SHA256 = "73315bced19dc4ed31029a6c734b24420c96673f1666b9d046f96b55f629dc10"
        private const val BUNDLED_ASSET_NAME = "maintenance_uf2.json"
    }
}
