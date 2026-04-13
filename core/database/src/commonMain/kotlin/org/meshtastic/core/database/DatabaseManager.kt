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

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.common.database.DatabaseManager as SharedDatabaseManager

/** Manages per-device Room database instances for node data, with LRU eviction. */
@Single(binds = [DatabaseProvider::class, SharedDatabaseManager::class])
@Suppress("TooManyFunctions")
@OptIn(ExperimentalCoroutinesApi::class)
open class DatabaseManager(
    @Named("DatabaseDataStore") private val datastore: DataStore<Preferences>,
    private val dispatchers: CoroutineDispatchers,
) : DatabaseProvider,
    SharedDatabaseManager {

    private val managerScope = CoroutineScope(SupervisorJob() + dispatchers.default)
    private val mutex = Mutex()

    private val cacheLimitKey = intPreferencesKey(DatabaseConstants.CACHE_LIMIT_KEY)
    private val legacyCleanedKey = booleanPreferencesKey(DatabaseConstants.LEGACY_DB_CLEANED_KEY)

    private fun lastUsedKey(dbName: String) = longPreferencesKey("db_last_used:$dbName")

    override val cacheLimit: StateFlow<Int> =
        datastore.data
            .map { it[cacheLimitKey] ?: DatabaseConstants.DEFAULT_CACHE_LIMIT }
            .stateIn(managerScope, SharingStarted.Eagerly, DatabaseConstants.DEFAULT_CACHE_LIMIT)

    override fun getCurrentCacheLimit(): Int = cacheLimit.value

    override fun setCacheLimit(limit: Int) {
        val clamped = limit.coerceIn(DatabaseConstants.MIN_CACHE_LIMIT, DatabaseConstants.MAX_CACHE_LIMIT)
        managerScope.launch {
            datastore.edit { it[cacheLimitKey] = clamped }
            // Enforce asynchronously with current active DB protected
            val active =
                _currentDb.value?.let { buildDbName(_currentAddress.value) } ?: DatabaseConstants.DEFAULT_DB_NAME
            enforceCacheLimit(activeDbName = active)
        }
    }

    private val dbCache = mutableMapOf<String, MeshtasticDatabase>()

    private val _currentDb = MutableStateFlow<MeshtasticDatabase?>(null)

    /**
     * The currently active database, built lazily on first access. Room's `onOpen` callback is itself lazy (not invoked
     * until the first query), so construction only allocates the builder and connection pool — actual I/O is deferred.
     */
    override val currentDb: StateFlow<MeshtasticDatabase> =
        _currentDb
            .filterNotNull()
            .stateIn(managerScope, SharingStarted.Eagerly, getOrOpenDatabase(DatabaseConstants.DEFAULT_DB_NAME))

    private val _currentAddress = MutableStateFlow<String?>(null)
    val currentAddress: StateFlow<String?> = _currentAddress

    /** Initialize the active database for [address]. */
    suspend fun init(address: String?) {
        switchActiveDatabase(address)
    }

    /**
     * Returns a cached [MeshtasticDatabase] or builds a new one for [dbName]. The caller must hold [mutex] when
     * modifying [dbCache] concurrently; however, this helper is also used from [currentDb]'s `initialValue` where the
     * mutex is not yet relevant (single-threaded construction).
     */
    private fun getOrOpenDatabase(dbName: String): MeshtasticDatabase =
        dbCache.getOrPut(dbName) { getDatabaseBuilder(dbName).build() }

    /** Switch active database to the one associated with [address]. Serialized via mutex. */
    override suspend fun switchActiveDatabase(address: String?) = mutex.withLock {
        val dbName = buildDbName(address)

        // Remember the previously active DB name (any) so we can record its last-used time as well.
        val previousDbName = _currentDb.value?.let { buildDbName(_currentAddress.value) }

        // Fast path: no-op if already on this address
        if (_currentAddress.value == address && _currentDb.value != null) {
            markLastUsed(dbName)
            return@withLock
        }

        // Build/open Room DB off the main thread
        val db = withContext(dispatchers.io) { getOrOpenDatabase(dbName) }

        // Emit the new DB BEFORE closing the old one. flatMapLatest collectors on
        // currentDb will cancel their in-flight queries on the previous database once
        // the new value is emitted. Closing the old pool first would race with those
        // collectors, causing "Connection pool is closed" crashes.
        _currentDb.value = db
        _currentAddress.value = address
        markLastUsed(dbName)
        // Also mark the previous DB as used "just now" so LRU has an accurate, recent timestamp
        previousDbName?.let { markLastUsed(it) }

        // Do NOT close the previous DB synchronously here. Even though _currentDb has been
        // updated, in-flight `withDb` calls may still hold a reference to the old database
        // (captured before the emission). Closing the connection pool while those queries are
        // executing causes "Connection pool is closed" crashes. Instead, let LRU eviction
        // (enforceCacheLimit) handle cleanup — it only runs on databases that are not the
        // active target and have not been used recently.

        // Defer LRU eviction so switch is not blocked by filesystem work
        managerScope.launch(dispatchers.io) { enforceCacheLimit(activeDbName = dbName) }

        // One-time cleanup: remove legacy DB if present and not active
        managerScope.launch(dispatchers.io) { cleanupLegacyDbIfNeeded(activeDbName = dbName) }

        Logger.i { "Switched active DB to ${anonymizeDbName(dbName)} for address ${anonymizeAddress(address)}" }
    }

    /**
     * Closes and removes a cached database by name. Safe to call even if the database was already closed or not in the
     * cache. Does NOT delete the underlying file — the database can be re-opened on next access.
     *
     * On JVM/Desktop, Room KMP has no auto-close timeout (Android-only API), so idle databases hold open SQLite
     * connections (5 per WAL-mode DB) indefinitely until explicitly closed. This method is the primary mechanism for
     * releasing those connections when a database is no longer the active target.
     */
    private fun closeCachedDatabase(dbName: String) {
        val removed = dbCache.remove(dbName) ?: return
        runCatching { removed.close() }
            .onFailure { Logger.w(it) { "Failed to close cached database ${anonymizeDbName(dbName)}" } }
        Logger.d { "Closed inactive database ${anonymizeDbName(dbName)} to free connections" }
    }

    private val limitedIo = dispatchers.io.limitedParallelism(4)

    /** Execute [block] with the current DB instance. */
    @Suppress("TooGenericExceptionCaught")
    override suspend fun <T> withDb(block: suspend (MeshtasticDatabase) -> T): T? = withContext(limitedIo) {
        val db = _currentDb.value ?: return@withContext null
        val active = buildDbName(_currentAddress.value)
        markLastUsed(active)
        try {
            block(db)
        } catch (e: CancellationException) {
            throw e // Preserve structured concurrency cancellation propagation.
        } catch (e: Exception) {
            // If the connection pool was closed between capturing `db` and executing the query
            // (e.g., during a database switch), retry once with the current DB instance.
            if (e.message?.contains("Connection pool is closed") == true) {
                Logger.w { "withDb: connection pool closed, retrying with current DB" }
                val retryDb = _currentDb.value ?: return@withContext null
                block(retryDb)
            } else {
                throw e
            }
        }
    }

    /**
     * Returns true if a database exists for the given device address. Android Room stores DB files without an
     * extension; JVM/iOS append `.db`. We check both to stay platform-agnostic.
     */
    override fun hasDatabaseFor(address: String?): Boolean {
        if (address.isNullOrBlank() || address == "n") return false
        val dbName = buildDbName(address)
        return dbFileExists(dbName)
    }

    private fun dbFileExists(dbName: String): Boolean {
        val dir = getDatabaseDirectory()
        val fs = getFileSystem()
        return fs.exists(dir.resolve(dbName)) || fs.exists(dir.resolve("$dbName.db"))
    }

    private fun dbFileMetadataMillis(dbName: String): Long? {
        val dir = getDatabaseDirectory()
        val fs = getFileSystem()
        return fs.metadataOrNull(dir.resolve(dbName))?.lastModifiedAtMillis
            ?: fs.metadataOrNull(dir.resolve("$dbName.db"))?.lastModifiedAtMillis
    }

    private fun markLastUsed(dbName: String) {
        managerScope.launch { datastore.edit { it[lastUsedKey(dbName)] = nowMillis } }
    }

    private suspend fun lastUsed(dbName: String): Long {
        val key = lastUsedKey(dbName)
        val v = datastore.data.first()[key] ?: 0L
        return if (v == 0L) {
            dbFileMetadataMillis(dbName) ?: 0L
        } else {
            v
        }
    }

    private fun listExistingDbNames(): List<String> {
        val dir = getDatabaseDirectory()
        val fs = getFileSystem()
        if (!fs.exists(dir)) return emptyList()

        return fs.list(dir)
            .asSequence()
            .map { it.name }
            .filter { it.startsWith(DatabaseConstants.DB_PREFIX) }
            // Skip Room-internal sidecar files (-wal/-shm/-journal) and lock files so each DB appears exactly once.
            .filterNot { it.endsWith("-wal") || it.endsWith("-shm") || it.endsWith("-journal") || it.endsWith(".lck") }
            .map { it.removeSuffix(".db") }
            .distinct()
            .toList()
    }

    private suspend fun enforceCacheLimit(activeDbName: String) = mutex.withLock {
        val limit = getCurrentCacheLimit()
        val all = listExistingDbNames()
        // Only enforce the limit over device-specific DBs; exclude legacy and default DBs
        val deviceDbs =
            all.filterNot { it == DatabaseConstants.LEGACY_DB_NAME || it == DatabaseConstants.DEFAULT_DB_NAME }

        if (deviceDbs.size <= limit) return@withLock
        val usageSnapshot = deviceDbs.associateWith { lastUsed(it) }
        val victims = selectEvictionVictims(deviceDbs, activeDbName, limit, usageSnapshot)

        victims.forEach { name ->
            runCatching {
                // runCatching intentional: best-effort cleanup must not abort on cancellation
                closeCachedDatabase(name)
                deleteDatabase(name)
                datastore.edit { it.remove(lastUsedKey(name)) }
            }
                .onSuccess { Logger.i { "Evicted cached DB ${anonymizeDbName(name)}" } }
                .onFailure { Logger.w(it) { "Failed to evict database ${anonymizeDbName(name)}" } }
        }
    }

    private suspend fun cleanupLegacyDbIfNeeded(activeDbName: String) = mutex.withLock {
        val cleaned = datastore.data.first()[legacyCleanedKey] ?: false
        if (cleaned) return@withLock

        val legacy = DatabaseConstants.LEGACY_DB_NAME
        if (legacy == activeDbName) {
            datastore.edit { it[legacyCleanedKey] = true }
            return@withLock
        }

        if (dbFileExists(legacy)) {
            runCatching {
                // runCatching intentional: best-effort cleanup must not abort on cancellation
                closeCachedDatabase(legacy)
                deleteDatabase(legacy)
            }
                .onSuccess { Logger.i { "Deleted legacy DB ${anonymizeDbName(legacy)}" } }
                .onFailure { Logger.w(it) { "Failed to delete legacy database ${anonymizeDbName(legacy)}" } }
        }
        datastore.edit { it[legacyCleanedKey] = true }
    }

    /** Closes all open databases and cancels background work. */
    fun close() {
        managerScope.cancel()
        dbCache.values.forEach { it.close() }
        dbCache.clear()
        _currentDb.value = null
    }
}
