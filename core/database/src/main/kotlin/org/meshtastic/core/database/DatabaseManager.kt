/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package org.meshtastic.core.database

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import androidx.room.RoomDatabase
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.meshtastic.core.di.CoroutineDispatchers
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** Manages per-device Room database instances for node data, with LRU eviction. */
@Singleton
class DatabaseManager @Inject constructor(private val app: Application, private val dispatchers: CoroutineDispatchers) {
    val prefs: SharedPreferences = app.getSharedPreferences("db-manager-prefs", Context.MODE_PRIVATE)
    private val managerScope = CoroutineScope(SupervisorJob() + dispatchers.default)

    private val mutex = Mutex()

    // Expose the DB cache limit as a reactive stream so UI can observe changes.
    private val _cacheLimit = MutableStateFlow(getCacheLimit())
    val cacheLimit: StateFlow<Int> = _cacheLimit

    // Keep cache-limit StateFlow in sync if some other component updates SharedPreferences.
    private val prefsListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == DatabaseConstants.CACHE_LIMIT_KEY) {
                _cacheLimit.value = getCacheLimit()
            }
        }

    init {
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
    }

    private val _currentDb = MutableStateFlow<MeshtasticDatabase?>(null)
    val currentDb: StateFlow<MeshtasticDatabase> =
        _currentDb.filterNotNull().stateIn(managerScope, SharingStarted.Eagerly, buildRoomDb(app, defaultDbName()))

    private val _currentAddress = MutableStateFlow<String?>(null)
    val currentAddress: StateFlow<String?> = _currentAddress

    private val dbCache = mutableMapOf<String, MeshtasticDatabase>() // key = dbName

    /** Initialize the active database for [address]. */
    suspend fun init(address: String?) {
        switchActiveDatabase(address)
    }

    /** Switch active database to the one associated with [address]. Serialized via mutex. */
    suspend fun switchActiveDatabase(address: String?) = mutex.withLock {
        val dbName = buildDbName(address)

        // Remember the previously active DB name (any) so we can record its last-used time as well.
        val previousDbName = _currentDb.value?.openHelper?.databaseName

        // Fast path: no-op if already on this address
        if (_currentAddress.value == address && _currentDb.value != null) {
            markLastUsed(dbName)
            return@withLock
        }

        // Build/open Room DB off the main thread
        val db =
            dbCache[dbName]
                ?: withContext(dispatchers.io) { buildRoomDb(app, dbName) }.also { dbCache[dbName] = it }

        _currentDb.value = db
        _currentAddress.value = address
        markLastUsed(dbName)
        // Also mark the previous DB as used "just now" so LRU has an accurate, recent timestamp
        // even on first run after upgrade where no timestamp might exist yet.
        previousDbName?.let { markLastUsed(it) }

        // Defer LRU eviction so switch is not blocked by filesystem work
        managerScope.launch(dispatchers.io) { enforceCacheLimit(activeDbName = dbName) }

        // One-time cleanup: remove legacy DB if present and not active
        managerScope.launch(dispatchers.io) { cleanupLegacyDbIfNeeded(activeDbName = dbName) }

        Logger.i { "Switched active DB to ${anonymizeDbName(dbName)} for address ${anonymizeAddress(address)}" }
    }

    /** Execute [block] with the current DB instance. */
    inline fun <T> withDb(block: (MeshtasticDatabase) -> T): T = block(currentDb.value)

    private fun markLastUsed(dbName: String) {
        prefs.edit().putLong(lastUsedKey(dbName), System.currentTimeMillis()).apply()
    }

    private fun lastUsed(dbName: String): Long {
        val k = lastUsedKey(dbName)
        val v = prefs.getLong(k, 0L)
        return if (v == 0L) getDbFile(app, dbName)?.lastModified() ?: 0L else v
    }

    private fun listExistingDbNames(): List<String> {
        val base = app.getDatabasePath(DatabaseConstants.LEGACY_DB_NAME)
        val dir = base.parentFile ?: return emptyList()
        val names = dir.listFiles()?.mapNotNull { f -> f.name } ?: emptyList()
        return names
            .filter { it.startsWith(DatabaseConstants.DB_PREFIX) }
            .filterNot { it.endsWith("-wal") || it.endsWith("-shm") }
            .distinct()
    }

    private suspend fun enforceCacheLimit(activeDbName: String) = mutex.withLock {
        val limit = getCacheLimit()
        val all = listExistingDbNames()
        // Only enforce the limit over device-specific DBs; exclude legacy and default DBs
        val deviceDbs =
            all.filterNot { it == DatabaseConstants.LEGACY_DB_NAME || it == DatabaseConstants.DEFAULT_DB_NAME }
        Logger.d {
            "LRU check: limit=$limit, active=${anonymizeDbName(
                activeDbName,
            )}, deviceDbs=${deviceDbs.joinToString(", ") {
                anonymizeDbName(it)
            }}"
        }
        if (deviceDbs.size <= limit) return@withLock
        val usageSnapshot = deviceDbs.associateWith { lastUsed(it) }
        Logger.d {
            "LRU lastUsed(ms): ${usageSnapshot.entries.joinToString(", ") { (name, ts) ->
                "${anonymizeDbName(name)}=$ts"
            }}"
        }
        val victims = selectEvictionVictims(deviceDbs, activeDbName, limit, usageSnapshot)
        Logger.i { "LRU victims: ${victims.joinToString(", ") { anonymizeDbName(it) }}" }
        victims.forEach { name ->
            runCatching { dbCache.remove(name)?.close() }
                .onFailure { Logger.w(it) { "Failed to close database $name" } }
            app.deleteDatabase(name)
            prefs.edit().remove(lastUsedKey(name)).apply()
            Logger.i { "Evicted cached DB ${anonymizeDbName(name)}" }
        }
    }

    fun getCacheLimit(): Int = prefs
        .getInt(DatabaseConstants.CACHE_LIMIT_KEY, DatabaseConstants.DEFAULT_CACHE_LIMIT)
        .coerceIn(DatabaseConstants.MIN_CACHE_LIMIT, DatabaseConstants.MAX_CACHE_LIMIT)

    fun setCacheLimit(limit: Int) {
        val clamped = limit.coerceIn(DatabaseConstants.MIN_CACHE_LIMIT, DatabaseConstants.MAX_CACHE_LIMIT)
        if (clamped == getCacheLimit()) return
        prefs.edit().putInt(DatabaseConstants.CACHE_LIMIT_KEY, clamped).apply()
        _cacheLimit.value = clamped
        // Enforce asynchronously with current active DB protected
        val active = _currentDb.value?.openHelper?.databaseName ?: defaultDbName()
        managerScope.launch(dispatchers.io) { enforceCacheLimit(activeDbName = active) }
    }

    private suspend fun cleanupLegacyDbIfNeeded(activeDbName: String) = mutex.withLock {
        if (prefs.getBoolean(DatabaseConstants.LEGACY_DB_CLEANED_KEY, false)) return@withLock
        val legacy = DatabaseConstants.LEGACY_DB_NAME
        if (legacy == activeDbName) {
            // Never delete the active DB; mark as cleaned to avoid repeated checks
            prefs.edit().putBoolean(DatabaseConstants.LEGACY_DB_CLEANED_KEY, true).apply()
            return@withLock
        }
        val legacyFile = getDbFile(app, legacy)
        if (legacyFile != null) {
            runCatching { dbCache.remove(legacy)?.close() }
                .onFailure { Logger.w(it) { "Failed to close legacy database $legacy before deletion" } }
            val deleted = app.deleteDatabase(legacy)
            if (deleted) {
                Logger.i { "Deleted legacy DB ${anonymizeDbName(legacy)}" }
            } else {
                Logger.w { "Attempted to delete legacy DB $legacy but deleteDatabase returned false" }
            }
        }
        prefs.edit().putBoolean(DatabaseConstants.LEGACY_DB_CLEANED_KEY, true).apply()
    }
}

object DatabaseConstants {
    const val DB_PREFIX: String = "meshtastic_database"
    const val LEGACY_DB_NAME: String = DB_PREFIX
    const val DEFAULT_DB_NAME: String = "${DB_PREFIX}_default"

    const val CACHE_LIMIT_KEY: String = "node_db_cache_limit"
    const val DEFAULT_CACHE_LIMIT: Int = 3
    const val MIN_CACHE_LIMIT: Int = 1
    const val MAX_CACHE_LIMIT: Int = 10

    const val LEGACY_DB_CLEANED_KEY: String = "legacy_db_cleaned"

    // Display/truncation and hash sizing for DB names
    const val DB_NAME_HASH_LEN: Int = 10
    const val DB_NAME_SEPARATOR_LEN: Int = 1
    const val DB_NAME_SUFFIX_LEN: Int = 3

    // Address anonymization sizing
    const val ADDRESS_ANON_SHORT_LEN: Int = 4
    const val ADDRESS_ANON_EDGE_LEN: Int = 2
}

// File-private helpers (kept outside the class to reduce class function count)
private fun defaultDbName(): String = DatabaseConstants.DEFAULT_DB_NAME

private fun normalizeAddress(addr: String?): String {
    val u = addr?.trim()?.uppercase()
    val normalized =
        when {
            u.isNullOrBlank() -> "DEFAULT"
            u == "N" || u == "NULL" -> "DEFAULT"
            else -> u.replace(":", "")
        }
    return normalized
}

private fun shortSha1(s: String): String = MessageDigest.getInstance("SHA-1")
    .digest(s.toByteArray())
    .joinToString("") { "%02x".format(it) }
    .take(DatabaseConstants.DB_NAME_HASH_LEN)

private fun buildDbName(address: String?): String = if (address.isNullOrBlank()) {
    defaultDbName()
} else {
    "${DatabaseConstants.DB_PREFIX}_${shortSha1(normalizeAddress(address))}"
}

private fun lastUsedKey(dbName: String) = "db_last_used:$dbName"

private fun anonymizeAddress(address: String?): String = when {
    address == null -> "null"
    address.length <= DatabaseConstants.ADDRESS_ANON_SHORT_LEN -> address
    else ->
        address.take(DatabaseConstants.ADDRESS_ANON_EDGE_LEN) +
            "…" +
            address.takeLast(DatabaseConstants.ADDRESS_ANON_EDGE_LEN)
}

private fun anonymizeDbName(name: String): String =
    if (name == DatabaseConstants.LEGACY_DB_NAME || name == DatabaseConstants.DEFAULT_DB_NAME) {
        name
    } else {
        name.take(
            DatabaseConstants.DB_PREFIX.length +
                DatabaseConstants.DB_NAME_SEPARATOR_LEN +
                DatabaseConstants.DB_NAME_SUFFIX_LEN,
        ) + "…"
    }

private fun buildRoomDb(app: Application, dbName: String): MeshtasticDatabase =
    Room.databaseBuilder(app.applicationContext, MeshtasticDatabase::class.java, dbName)
        .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
        .fallbackToDestructiveMigration(false)
        .build()

private fun getDbFile(app: Application, dbName: String): File? = app.getDatabasePath(dbName).takeIf { it.exists() }

/**
 * Compute which DBs to evict using LRU policy.
 *
 * Rules:
 * - Only consider device-specific DBs (exclude legacy and default)
 * - Never evict the active DB
 * - If number of device DBs is within the limit, evict none
 * - Otherwise evict the (size - limit) least-recently-used DBs
 *
 * Pass a precomputed [lastUsedMsByDb] snapshot to avoid redundant IO/lookups.
 */
internal fun selectEvictionVictims(
    dbNames: List<String>,
    activeDbName: String,
    limit: Int,
    lastUsedMsByDb: Map<String, Long>,
): List<String> {
    val deviceDbNames =
        dbNames.filterNot { it == DatabaseConstants.LEGACY_DB_NAME || it == DatabaseConstants.DEFAULT_DB_NAME }
    val victims =
        if (limit < 1 || deviceDbNames.size <= limit) {
            emptyList()
        } else {
            val candidates = deviceDbNames.filter { it != activeDbName }
            if (candidates.isEmpty()) {
                emptyList()
            } else {
                val toEvict = deviceDbNames.size - limit
                candidates.sortedBy { lastUsedMsByDb[it] ?: 0L }.take(toEvict)
            }
        }
    return victims
}
