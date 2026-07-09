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
package org.meshtastic.core.database

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
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
import org.meshtastic.core.common.util.normalizeAddress
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.di.CoroutineDispatchers
import kotlin.concurrent.Volatile
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

    private fun addrDbKey(address: String?) =
        stringPreferencesKey("${DatabaseConstants.ADDR_DB_FOR_PREFIX}${normalizeAddress(address)}")

    private fun nodeDbKey(nodeNum: Int) = stringPreferencesKey("${DatabaseConstants.NODE_DB_FOR_PREFIX}$nodeNum")

    private var backfillJob: Job? = null

    @Volatile private var hasDelayedFirstDeviceBackfill = false

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
            enforceCacheLimit(activeDbName = currentDbName)
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

    /**
     * Name of the currently active database. Tracked explicitly rather than recomputed from the address, because
     * cross-transport aliasing ([associateNode]) decouples the two: a secondary transport's address maps to the DB
     * claimed by the first transport, which `buildDbName(address)` would never produce. Written under [mutex].
     */
    @Volatile private var currentDbName: String = DatabaseConstants.DEFAULT_DB_NAME

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

    /**
     * Resolves the DB name to use for [address], honoring a cross-transport alias when one exists. A secondary
     * transport (e.g. TCP) that has been unified with a node points at the DB the first transport (e.g. BLE) claimed;
     * without an alias this falls back to the address-hashed name — today's default — for a first-time or primary
     * connection. See [associateNode].
     */
    private suspend fun resolveDbName(address: String?): String {
        val fallback = buildDbName(address)
        if (fallback == DatabaseConstants.DEFAULT_DB_NAME) return fallback
        return datastore.data.first()[addrDbKey(address)] ?: fallback
    }

    /** Switch active database to the one associated with [address]. Serialized via mutex. */
    override suspend fun switchActiveDatabase(address: String?) = mutex.withLock {
        val dbName = resolveDbName(address)

        // Remember the previously active DB name (any) so we can record its last-used time as well.
        val previousDbName = if (_currentDb.value != null) currentDbName else null

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
        currentDbName = dbName
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

        // Backfill FTS search index for any text messages missing messageText.
        // On the first real device DB, defer this so it does not starve the single DB connection while
        // the UI is collecting startup flows. The default DB should not consume the cold-start delay.
        val shouldDelayBackfill = dbName != DatabaseConstants.DEFAULT_DB_NAME && !hasDelayedFirstDeviceBackfill
        if (shouldDelayBackfill) hasDelayedFirstDeviceBackfill = true
        scheduleSearchIndexBackfill(dbName = dbName, db = db, shouldDelayBackfill = shouldDelayBackfill)

        Logger.i { "Switched active DB to ${anonymizeDbName(dbName)} for address ${anonymizeAddress(address)}" }
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun associateNode(nodeNum: Int) {
        mutex.withLock {
            val sourceName = currentDbName
            // Never claim or merge into the sentinel "no device" DB.
            if (sourceName == DatabaseConstants.DEFAULT_DB_NAME) return@withLock

            val claimed = datastore.data.first()[nodeDbKey(nodeNum)]
            when {
                claimed == null -> {
                    // First transport to learn this node: its current DB becomes the node's canonical DB.
                    // No address alias is needed — a primary connection already resolves to this DB via buildDbName.
                    datastore.edit { it[nodeDbKey(nodeNum)] = sourceName }
                    Logger.i { "Claimed ${anonymizeDbName(sourceName)} as canonical DB for node $nodeNum" }
                }

                claimed == sourceName -> Unit

                // Already unified — nothing to do.

                else -> {
                    // Secondary transport reached an already-known node: fold this DB into the canonical one,
                    // switch the active DB to it, alias this address to it, and retire the now-merged source.
                    val source = _currentDb.value ?: return@withLock
                    val dest = withContext(dispatchers.io) { getOrOpenDatabase(claimed) }

                    // Redirect live writers to the canonical DB BEFORE merging. Otherwise a concurrent withDb
                    // write (connect triggers a full NodeDB re-dump) could land in `source` after the merge has
                    // already snapshotted it, then be lost when `source` is retired — and withDb's closed-pool
                    // retry can't recover it because the write itself succeeded. New writers now capture `dest`;
                    // any still holding `source` are covered by that retry once retireDatabase closes it.
                    _currentDb.value = dest
                    currentDbName = claimed
                    try {
                        withContext(dispatchers.io) { DatabaseMerger.merge(source, dest) }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // merge() is atomic, so on failure `dest` is unchanged. Roll the active DB back to
                        // `source` so the address still resolves consistently and the merge retries next connect.
                        _currentDb.value = source
                        currentDbName = sourceName
                        Logger.w(e) {
                            "Merge into ${anonymizeDbName(claimed)} failed; kept ${anonymizeDbName(sourceName)} active"
                        }
                        return@withLock
                    }

                    markLastUsed(claimed)
                    datastore.edit { it[addrDbKey(_currentAddress.value)] = claimed }
                    Logger.i {
                        "Unified ${anonymizeDbName(sourceName)} into ${anonymizeDbName(claimed)} for node $nodeNum"
                    }

                    // Retire the merged source off the critical path; its data now lives in the canonical DB.
                    managerScope.launch(dispatchers.io) { retireDatabase(sourceName) }
                }
            }
        }
    }

    /** Closes, deletes, and forgets a database whose contents have been merged into another. */
    private suspend fun retireDatabase(dbName: String) = mutex.withLock {
        runCatching {
            closeCachedDatabase(dbName)
            deleteDatabase(dbName)
            datastore.edit { it.remove(lastUsedKey(dbName)) }
        }
            .onSuccess { Logger.i { "Retired merged DB ${anonymizeDbName(dbName)}" } }
            .onFailure { Logger.w(it) { "Failed to retire merged database ${anonymizeDbName(dbName)}" } }
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

    /**
     * Reopens the active database under [mutex], but only if it hasn't switched since the caller snapshotted it. Emits
     * the replacement DB BEFORE closing the old pool so [currentDb] Flow collectors can move to the new instance
     * without seeing a closed-pool error.
     *
     * Returns the reopened DB, or null if another coroutine already switched to a different device.
     */
    private suspend fun reopenActiveDatabaseIfStillCurrent(
        expectedDb: MeshtasticDatabase,
        expectedDbName: String,
    ): MeshtasticDatabase? = mutex.withLock {
        if (_currentDb.value !== expectedDb || currentDbName != expectedDbName) return null

        val cached = dbCache[expectedDbName]
        if (cached !== expectedDb) {
            Logger.w { "withDb: active DB cache entry changed before reopen; skipping active DB reopen" }
            return null
        }

        // Remove from cache without closing so getDatabaseBuilder builds a fresh instance.
        dbCache.remove(expectedDbName)

        val reopened = withContext(dispatchers.io) { getDatabaseBuilder(expectedDbName).build() }
        dbCache[expectedDbName] = reopened
        _currentDb.value = reopened

        // Close the replaced instance AFTER emitting the replacement so flatMapLatest collectors
        // have already moved to the new DB before the old pool closes.
        managerScope.launch(dispatchers.io) {
            runCatching { expectedDb.close() }
                .onFailure {
                    Logger.w(it) { "Failed to close replaced active database ${anonymizeDbName(expectedDbName)}" }
                }
                .onSuccess {
                    Logger.d { "Closed replaced active database ${anonymizeDbName(expectedDbName)} after reopen" }
                }
        }

        reopened
    }

    // Short-term runtime containment: route withDb entry through a single-lane dispatcher to narrow the Room/SQLite
    // connection-pool churn window seen during device/firmware update flows. Room suspend DAOs may continue on Room's
    // own executor after suspension, so this is not a strict global DB-I/O serialization guarantee. Preserve bounded
    // one-shot DB-critical blocks through cancellation, then re-check cancellation so stale callers do not continue
    // after the DB releases. Long-lived Flow/Paging reads must stay out of withDb; revisit after direct currentDb.value
    // callers are audited and safe DB concurrency can be restored.
    private val limitedIo = dispatchers.io.limitedParallelism(1)

    /** Execute [block] with the current DB instance. Retries once if the pool closes during a DB switch. */
    @Suppress("TooGenericExceptionCaught")
    override suspend fun <T> withDb(block: suspend (MeshtasticDatabase) -> T): T? {
        val queuedAt = nowMillis
        return withContext(limitedIo) {
            val queuedMillis = nowMillis - queuedAt
            if (queuedMillis >= WITH_DB_SLOW_OPERATION_MS) {
                Logger.w { "withDb waited ${queuedMillis}ms for the temporary DB containment lane" }
            }

            val startedAt = nowMillis
            try {
                withCurrentDb(block)
            } finally {
                val elapsedMillis = nowMillis - startedAt
                if (elapsedMillis >= WITH_DB_SLOW_OPERATION_MS) {
                    Logger.w {
                        "withDb callback took ${elapsedMillis}ms on the temporary DB containment lane; persistent " +
                            "slow logs indicate DB access path should be revisited"
                    }
                }
            }
        }
    }

    @Suppress("ReturnCount", "ThrowsCount", "TooGenericExceptionCaught")
    private suspend fun <T> withCurrentDb(block: suspend (MeshtasticDatabase) -> T): T? {
        val db = _currentDb.value ?: return null
        val active = currentDbName
        markLastUsed(active)
        try {
            return runCancellableDbBlock(db, block)
        } catch (e: CancellationException) {
            throw e // Preserve structured concurrency cancellation propagation.
        } catch (e: Exception) {
            // If the active database switched while we held a reference to the old one,
            // and the exception indicates a closed pool/connection, retry with the new DB.
            val retryDb = _currentDb.value
            if (retryDb != null && retryDb !== db && isDbClosedException(e)) {
                Logger.w { "withDb: database closed during switch (${e.message}), retrying with current DB" }
                return try {
                    runCancellableDbBlock(retryDb, block)
                } catch (retryCancel: CancellationException) {
                    throw retryCancel
                } catch (retryEx: Exception) {
                    retryEx.addSuppressed(e)
                    throw retryEx
                }
            }

            // Same active DB but Room's connection pool is wedged — close and reopen once.
            if (retryDb === db && isDbPoolAcquireTimeoutException(e)) {
                val reopened = reopenActiveDatabaseIfStillCurrent(db, active) ?: throw e
                Logger.w { "withDb: reopened active DB after transient Room connection-pool timeout" }
                return try {
                    runCancellableDbBlock(reopened, block)
                } catch (retryCancel: CancellationException) {
                    throw retryCancel
                } catch (retryEx: Exception) {
                    retryEx.addSuppressed(e)
                    throw retryEx
                }
            }

            throw e
        }
    }

    private suspend fun <T> runCancellableDbBlock(db: MeshtasticDatabase, block: suspend (MeshtasticDatabase) -> T): T {
        // Keep withDb callbacks bounded and one-shot: NonCancellable can hold the containment lane until this returns.
        currentCoroutineContext().ensureActive()
        val result = withContext(NonCancellable) { block(db) }
        currentCoroutineContext().ensureActive()
        return result
    }

    private fun isDbClosedException(e: Exception): Boolean = generateSequence<Throwable>(e) { it.cause }
        .any { throwable ->
            val msg = throwable.message?.lowercase() ?: return@any false
            val hasDbContext = DB_TERMS.any { it in msg }
            // Room can surface switched/pool-churn failures as SQLite BUSY/acquire timeout, not only "closed".
            ("closed" in msg && hasDbContext) || (TRANSIENT_DB_POOL_TERMS.any { it in msg } && hasDbContext)
        }

    internal companion object {
        private const val BACKFILL_COLD_START_DELAY_MS = 2_000L
        private const val WITH_DB_SLOW_OPERATION_MS = 1_000L
        val DB_TERMS = listOf("pool", "database", "connection", "sqlite")
        val TRANSIENT_DB_POOL_TERMS =
            listOf("timed out attempting to acquire", "error code: 5", "database is locked", "sqlite_busy")

        private const val ROOM_POOL_ACQUIRE_TIMEOUT_PHRASE = "timed out attempting to acquire"
        private const val ROOM_READER_CONNECTION_PHRASE = "reader connection"
        private const val ROOM_WRITER_CONNECTION_PHRASE = "writer connection"

        /**
         * Room KMP currently exposes pool-acquire timeouts as exception message text instead of a stable common typed
         * signal. Keep this fallback narrow so BLE/GATT/transport connection errors do not trigger DB reopen recovery.
         */
        private fun isRoomPoolAcquireTimeoutMessage(message: String): Boolean =
            ROOM_POOL_ACQUIRE_TIMEOUT_PHRASE in message &&
                (ROOM_READER_CONNECTION_PHRASE in message || ROOM_WRITER_CONNECTION_PHRASE in message) &&
                DB_TERMS.any { it in message }

        fun isDbPoolAcquireTimeoutException(e: Exception): Boolean = generateSequence<Throwable>(e) { it.cause }
            .any { throwable ->
                val msg = throwable.message?.lowercase() ?: return@any false
                isRoomPoolAcquireTimeoutMessage(msg)
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

    @Suppress("TooGenericExceptionCaught")
    private fun scheduleSearchIndexBackfill(dbName: String, db: MeshtasticDatabase, shouldDelayBackfill: Boolean) {
        backfillJob?.cancel()
        backfillJob =
            managerScope.launch(dispatchers.io) {
                try {
                    if (shouldDelayBackfill) delay(BACKFILL_COLD_START_DELAY_MS)
                    if (_currentDb.value !== db) return@launch
                    backfillSearchIndexIfNeeded(db)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Logger.w(e) { "Failed to backfill search index for ${anonymizeDbName(dbName)}" }
                }
            }
    }

    /**
     * Backfills [Packet.messageText] for existing text-message packets that predate the FTS5 schema, then rebuilds the
     * FTS index so search covers historical messages. The text is decoded in Kotlin from each packet's payload (see
     * [PacketDao.backfillMessageTexts]); it cannot be read in SQL because the message body is stored as serialized
     * `bytes`, not a `text` JSON field.
     */
    private suspend fun backfillSearchIndexIfNeeded(db: MeshtasticDatabase) {
        val needsBackfill = db.packetDao().countPacketsNeedingBackfill() > 0
        if (!needsBackfill) return

        // Perform the write operations inside NonCancellable to prevent
        // connection pool leaks due to coroutine cancellation.
        withContext(NonCancellable) {
            val count = db.packetDao().backfillMessageTexts()
            if (count > 0) {
                Logger.i { "Backfilled $count messages for FTS search index" }
                db.packetDao().rebuildFtsIndex()
                Logger.i { "FTS search index rebuild complete" }
            }
        }
    }

    /** Closes all open databases and cancels background work. */
    fun close() {
        backfillJob?.cancel()
        backfillJob = null
        managerScope.cancel()
        dbCache.values.forEach { it.close() }
        dbCache.clear()
        _currentDb.value = null
    }
}
