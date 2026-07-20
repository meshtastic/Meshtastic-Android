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
@file:Suppress("MagicNumber") // The bounded region is the approved Burning Man 2026 policy boundary.

package org.meshtastic.feature.map.offline

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Clock
import kotlin.time.Instant
import java.io.File

data class BurningManPackRecord(
    val packId: String,
    val sourceBuild: String,
    val replicationTimestamp: String,
    val installedAt: Instant,
    val userSuppressed: Boolean,
)

data class SelectedBurningManPack(val file: File, val record: BurningManPackRecord)

fun interface BurningManPackDownloader {
    suspend fun download(bounds: GeoBounds, destination: File): DownloadedPack
}

interface BurningManPackStore {
    fun load(): BurningManPackRecord?

    fun save(record: BurningManPackRecord?)
}

class SharedPreferencesBurningManPackStore(context: Context) : BurningManPackStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun load(): BurningManPackRecord? = preferences.getString(KEY_PACK_ID, null)?.let { packId ->
            preferences.getString(KEY_INSTALLED_AT, null)?.let(Instant::parse)?.let { installedAt ->
                BurningManPackRecord(
                    packId = packId,
                    sourceBuild = preferences.getString(KEY_SOURCE_BUILD, "") ?: "",
                    replicationTimestamp = preferences.getString(KEY_REPLICATION_TIMESTAMP, "") ?: "",
                    installedAt = installedAt,
                    userSuppressed = preferences.getBoolean(KEY_USER_SUPPRESSED, false),
                )
            }
        }

    override fun save(record: BurningManPackRecord?) {
        preferences.edit().apply {
            if (record == null) {
                clear()
            } else {
                putString(KEY_PACK_ID, record.packId)
                putString(KEY_SOURCE_BUILD, record.sourceBuild)
                putString(KEY_REPLICATION_TIMESTAMP, record.replicationTimestamp)
                putString(KEY_INSTALLED_AT, record.installedAt.toString())
                putBoolean(KEY_USER_SUPPRESSED, record.userSuppressed)
            }
        }.apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "burning_man_pack"
        const val KEY_PACK_ID = "pack_id"
        const val KEY_SOURCE_BUILD = "source_build"
        const val KEY_REPLICATION_TIMESTAMP = "replication_timestamp"
        const val KEY_INSTALLED_AT = "installed_at"
        const val KEY_USER_SUPPRESSED = "user_suppressed"
    }
}

class BurningManPackCoordinator(
    private val filesDirectory: File,
    private val store: BurningManPackStore,
    private val downloader: BurningManPackDownloader =
        BurningManPackDownloader { bounds, destination -> ProtomapsRegionDownloader().download(bounds, destination) },
) {
    private val _selectedPack = MutableStateFlow<SelectedBurningManPack?>(null)
    val selectedPack: StateFlow<SelectedBurningManPack?> = _selectedPack.asStateFlow()

    suspend fun reconcile(now: Instant, lastAuthorizedLocation: PackLocation?): SelectedBurningManPack? {
        var record = store.load()
        var selected = record?.let(::validatedSelection)
        if (record != null && selected == null && !record.userSuppressed) {
            store.save(null)
            record = null
        }
        if (record?.userSuppressed == true) {
            _selectedPack.value = null
            return null
        }

        return when (BurningManPackPolicy(record?.asManifest()).reconcile(now, lastAuthorizedLocation)) {
            PackAction.Install -> install(now)
            PackAction.Retain -> selected.also { _selectedPack.value = it }
            PackAction.Remove -> removeAutomatically()
        }
    }

    fun removeByUser() {
        destination.delete()
        val record =
            store.load() ?: BurningManPackRecord(
                packId = PACK_ID,
                sourceBuild = "",
                replicationTimestamp = "",
                installedAt = Clock.System.now(),
                userSuppressed = true,
            )
        store.save(record.copy(userSuppressed = true))
        _selectedPack.value = null
    }

    private suspend fun install(now: Instant): SelectedBurningManPack? = runCatching {
            val downloadedPack = downloader.download(BOUNDS, destination)
            val reader = PmtilesV3Reader(destination)
            require(reader.header.tileType == PmtilesTileType.Mvt) { "Burning Man pack is not vector MVT" }
            val replicationTimestamp = requireNotNull(reader.metadata[REPLICATION_TIME_KEY]) {
                "Burning Man pack has no replication timestamp"
            }
            BurningManPackRecord(
                packId = PACK_ID,
                sourceBuild = downloadedPack.sourceBuild,
                replicationTimestamp = replicationTimestamp,
                installedAt = now,
                userSuppressed = false,
            )
        }.fold(
            onSuccess = { record ->
                val selected = SelectedBurningManPack(destination, record)
                store.save(record)
                _selectedPack.value = selected
                selected
            },
            onFailure = {
                destination.delete()
                _selectedPack.value = null
                null
            },
        )

    private fun removeAutomatically(): SelectedBurningManPack? {
        destination.delete()
        store.save(null)
        _selectedPack.value = null
        return null
    }

    private fun validatedSelection(record: BurningManPackRecord): SelectedBurningManPack? = runCatching {
            require(destination.isFile) { "Burning Man pack is missing" }
            val reader = PmtilesV3Reader(destination)
            require(reader.header.tileType == PmtilesTileType.Mvt) { "Burning Man pack is not vector MVT" }
            require(reader.metadata[REPLICATION_TIME_KEY] == record.replicationTimestamp) {
                "Burning Man pack replication timestamp does not match"
            }
            SelectedBurningManPack(destination, record)
        }.getOrNull()

    private fun BurningManPackRecord.asManifest(): BurningManPackManifest = BurningManPackManifest(
            packId = packId,
            sourceBuild = sourceBuild,
            installedAt = installedAt,
            userSuppressed = userSuppressed,
        )

    private val destination: File
        get() = File(filesDirectory, "offline/$PACK_ID.pmtiles")

    private companion object {
        const val PACK_ID = "burning-man-2026"
        val BOUNDS =
            GeoBounds(
                minLon = -119.287957,
                minLat = 40.722536,
                maxLon = -119.128520,
                maxLat = 40.843420,
            )
    }
}
