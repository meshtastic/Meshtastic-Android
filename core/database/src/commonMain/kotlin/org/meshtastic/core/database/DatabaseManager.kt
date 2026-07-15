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
import androidx.datastore.preferences.core.stringSetPreferencesKey
import co.touchlab.kermit.Logger
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.normalizeAddress
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.di.CoroutineDispatchers
import kotlin.concurrent.Volatile
import org.meshtastic.core.common.database.DatabaseManager as SharedDatabaseManager

/** Returns database names that form either side of an unfinished, crash-recoverable association route. */
internal fun pendingRouteDbNames(preferences: Preferences): Set<String> = preferences
    .asMap()
    .asSequence()
    .filter { (key, _) ->
        key.name.startsWith(DatabaseConstants.PENDING_SOURCE_DB_FOR_PREFIX) ||
            key.name.startsWith(DatabaseConstants.PENDING_DESTINATION_DB_FOR_PREFIX)
    }
    .mapNotNull { (_, value) -> value as? String }
    .toSet()

/** Manages per-device Room database instances for node data, with LRU eviction. */
@Single(binds = [DatabaseProvider::class, SharedDatabaseManager::class])
@Suppress("TooManyFunctions", "LargeClass")
@OptIn(ExperimentalCoroutinesApi::class)
open class DatabaseManager(
    @Named("DatabaseDataStore") private val datastore: DataStore<Preferences>,
    private val dispatchers: CoroutineDispatchers,
) : DatabaseProvider,
    SharedDatabaseManager {

    private val managerScope = CoroutineScope(SupervisorJob() + dispatchers.default)
    private val managerJobLock = SynchronizedObject()
    private val activeManagerJobs = mutableSetOf<Job>()
    private var managerJobDrain: CompletableDeferred<Unit>? = null
    private val mutex = Mutex()
    private val closeMutex = Mutex()

    private enum class LifecycleState {
        OPEN,
        CLOSING,
        CLOSED,
    }

    @Volatile private var lifecycleState = LifecycleState.OPEN

    // Per-source write barrier for merges. `withDb` deliberately does NOT take [mutex] (hot path), so a merge under
    // [mutex] must still drain any in-flight writer that captured the source DB before folding it away — otherwise a
    // late-committing write is lost when the source is retired. This dedicated lock (never held across a drain await,
    // so it can't deadlock the merge) tracks live `withDb` blocks per captured DB instance and the writer-admission
    // gate. The gate is armed at the start of an association attempt: while it is pending, [beginWrite] blocks new
    // writers instead of letting them capture a DB, so a new `withDb` can never write to `source` once it is being
    // retired, nor land on `dest` before the merge commits. The gate completes with `source` if the attempt aborts
    // (drain timeout, cancellation, or pre-commit merge failure) and with `dest` once the merge commits — source is
    // never restored after the merge commits. The lock is released before any suspend (drain await, gate await, Room
    // work, merge work, or DataStore work), so it can't deadlock any of them.
    private val writerTrackerMutex = Mutex()
    private val activeWriters = mutableMapOf<MeshtasticDatabase, Int>()
    private val drainWaiters = mutableMapOf<MeshtasticDatabase, MutableList<CompletableDeferred<Unit>>>()
    private val deferredEvictions = mutableSetOf<MeshtasticDatabase>()
    private var shutdownWriterDrain: CompletableDeferred<Unit>? = null

    // Admitted manager-operation tokens (one per serialized associateDevice/switch/recovery/eviction/cleanup that
    // takes the manager [mutex]). Guarded by [writerTrackerMutex]. [close] arms [managerOperationDrain] and bound-waits
    // for this set to empty BEFORE acquiring [mutex] for its ownership snapshot — otherwise an admitted operation that
    // holds [mutex] indefinitely (e.g. a stalled merge) pins shutdown forever despite the writer-drain bound already
    // in place. New operations are rejected at admission once lifecycleState != OPEN.
    private val activeManagerOperations = mutableSetOf<Any>()
    private var managerOperationDrain: CompletableDeferred<Unit>? = null

    // Armed at the start of an association attempt; null otherwise. A non-null gate blocks [beginWrite] until the
    // attempt resolves. It completes with the canonical DB (source on abort, dest on commit) so blocked writers resume
    // against the right instance. Never read or written outside [writerTrackerMutex].
    private var writerGate: CompletableDeferred<MeshtasticDatabase>? = null

    private val cacheLimitKey = intPreferencesKey(DatabaseConstants.CACHE_LIMIT_KEY)
    private val legacyCleanedKey = booleanPreferencesKey(DatabaseConstants.LEGACY_DB_CLEANED_KEY)
    private val retiredDbNamesKey = stringSetPreferencesKey(DatabaseConstants.RETIRED_DB_NAMES_KEY)

    private fun lastUsedKey(dbName: String) = longPreferencesKey("db_last_used:$dbName")

    private fun addrDbKey(address: String?) =
        stringPreferencesKey("${DatabaseConstants.ADDR_DB_FOR_PREFIX}${normalizeAddress(address)}")

    private fun pendingSourceDbKey(address: String) =
        stringPreferencesKey("${DatabaseConstants.PENDING_SOURCE_DB_FOR_PREFIX}${normalizeAddress(address)}")

    private fun pendingDestinationDbKey(address: String) =
        stringPreferencesKey("${DatabaseConstants.PENDING_DESTINATION_DB_FOR_PREFIX}${normalizeAddress(address)}")

    private var backfillJob: Job? = null

    /** Launches and tracks manager-owned work so shutdown waits only for jobs that can still touch owned resources. */
    protected fun launchManagerWork(
        dispatcher: CoroutineDispatcher = dispatchers.default,
        block: suspend CoroutineScope.() -> Unit,
    ): Job {
        lateinit var job: Job
        job = managerScope.launch(dispatcher, start = CoroutineStart.LAZY, block = block)
        val accepted =
            synchronized(managerJobLock) {
                if (lifecycleState == LifecycleState.OPEN) {
                    activeManagerJobs.add(job)
                    // A LAZY coroutine can be cancelled after registration but before its body starts. Completion
                    // handlers run in both that case and the normal completion path, so every accepted job releases
                    // its shutdown-drain registration exactly once.
                    job.invokeOnCompletion {
                        synchronized(managerJobLock) {
                            if (activeManagerJobs.remove(job) && activeManagerJobs.isEmpty()) {
                                managerJobDrain?.complete(Unit)
                            }
                        }
                    }
                    true
                } else {
                    false
                }
            }
        if (accepted) job.start() else job.cancel()
        return job
    }

    @Volatile private var hasDelayedFirstDeviceBackfill = false

    override val cacheLimit: StateFlow<Int> =
        datastore.data
            .map { it[cacheLimitKey] ?: DatabaseConstants.DEFAULT_CACHE_LIMIT }
            .stateIn(managerScope, SharingStarted.Eagerly, DatabaseConstants.DEFAULT_CACHE_LIMIT)

    override fun getCurrentCacheLimit(): Int = cacheLimit.value

    override fun setCacheLimit(limit: Int) {
        val clamped = limit.coerceIn(DatabaseConstants.MIN_CACHE_LIMIT, DatabaseConstants.MAX_CACHE_LIMIT)
        launchManagerWork {
            datastore.edit { it[cacheLimitKey] = clamped }
            // Resolve the protected DB only once this deferred work owns the manager mutex. A switch may complete
            // between scheduling and execution, so capturing currentDbName here could evict the newly active pool.
            enforceCacheLimit()
        }
    }

    private val dbCache = mutableMapOf<String, MeshtasticDatabase>()

    /** Replaced pools that remain live for consumers of an earlier [currentDb] emission until orderly shutdown. */
    private val detachedDatabases = mutableListOf<NamedDatabase>()

    /** Databases merged and logically retired but kept open — app-wide consumers may still hold references. */
    private val logicallyRetired = mutableSetOf<String>()

    /** Guarded by [mutex]; cleanup is safe only once, before this manager opens a device database. */
    private var attemptedStartupRetirementCleanup = false

    /** Guarded by [mutex]; keeps crash-route recovery from deleting a source after device DBs have been published. */
    private var hasOpenedDeviceDatabase = false

    private data class NamedDatabase(val dbName: String, val database: MeshtasticDatabase)

    private data class ShutdownDatabase(val database: MeshtasticDatabase, val dbNames: MutableSet<String>)

    private data class ShutdownSnapshot(val databases: List<ShutdownDatabase>, val retiredNames: List<String>)

    /**
     * Covers default-pool creation, current-flow publication, and shutdown ownership transfer as one state machine.
     * Unlike two independent Kotlin lazy delegates, this lock leaves no gap where shutdown can close a newly built
     * default pool before the flow that owns it becomes visible.
     */
    private val initializationLock = SynchronizedObject()

    /** Guarded by [initializationLock]. The pool may exist before it is inserted into [dbCache] or published. */
    private var initializedDefaultDb: MeshtasticDatabase? = null

    /** Guarded by [initializationLock]. Cleared only after shutdown has acquired its contained pool. */
    private var currentDbState: MutableStateFlow<MeshtasticDatabase>? = null

    /** Caller must hold [initializationLock]. */
    private fun getOrCreateDefaultDatabaseLocked(): MeshtasticDatabase {
        initializedDefaultDb?.let {
            return it
        }
        checkOpen()
        val database = buildDatabase(DatabaseConstants.DEFAULT_DB_NAME)
        if (lifecycleState != LifecycleState.OPEN) {
            runCatching { closeDatabase(database) }
                .onFailure {
                    // Retain ownership so the shutdown snapshot can retry instead of losing an open pool.
                    initializedDefaultDb = database
                    Logger.w(it) { "Failed to close default database initialized during shutdown" }
                }
            checkOpen()
        }
        initializedDefaultDb = database
        return database
    }

    /** Builds the default pool once without touching [dbCache]; cache insertion remains manager-mutex-only. */
    private fun getOrCreateDefaultDatabase(): MeshtasticDatabase =
        synchronized(initializationLock) { getOrCreateDefaultDatabaseLocked() }

    /** Lazily builds and publishes the default pool as one [initializationLock]-guarded ownership transfer. */
    private fun getOrCreateCurrentDbState(): MutableStateFlow<MeshtasticDatabase> = synchronized(initializationLock) {
        currentDbState?.let {
            return@synchronized it
        }
        checkOpen()
        MutableStateFlow(getOrCreateDefaultDatabaseLocked()).also { currentDbState = it }
    }

    /**
     * The currently active database. The default DB is opened lazily on first access and every internal publication
     * ([switchActiveDatabase], association rollback/release, active-DB reopen recovery) writes [_currentDb] directly,
     * so [currentDb].value reflects the new instance on the same program step — no `stateIn`/`filterNotNull` derivation
     * that would delay visibility to a coroutine dispatch.
     *
     * Initialization is deferred until first use so the overridable [buildDatabase] runs only after subclass properties
     * are set. It is construction-safe and does not mutate [dbCache]; switch and association paths insert the default
     * instance while holding [mutex]. Default creation, flow publication, and shutdown acquisition share
     * [initializationLock], so shutdown cannot miss or prematurely close an in-progress publication. Room's `onOpen`
     * callback remains lazy until the first query.
     */
    private val _currentDb: MutableStateFlow<MeshtasticDatabase>
        get() = getOrCreateCurrentDbState()

    override val currentDb: StateFlow<MeshtasticDatabase>
        get() = _currentDb

    private val _currentAddress = MutableStateFlow<String?>(null)
    val currentAddress: StateFlow<String?> = _currentAddress

    /**
     * Name of the currently active database. Tracked explicitly rather than recomputed from the address, because
     * cross-transport aliasing ([associateDevice]) decouples the two: a secondary transport's address maps to the DB
     * claimed by the first transport, which `buildDbName(address)` would never produce. Written under [mutex].
     */
    @Volatile private var currentDbName: String = DatabaseConstants.DEFAULT_DB_NAME

    /** Initialize the active database for [address]. */
    suspend fun init(address: String?) {
        switchActiveDatabase(address)
    }

    /** Returns a cached database or builds one. Every caller must hold [mutex]. */
    private fun getOrOpenDatabase(dbName: String): MeshtasticDatabase = dbCache.getOrPut(dbName) {
        if (dbName == DatabaseConstants.DEFAULT_DB_NAME) getOrCreateDefaultDatabase() else buildDatabase(dbName)
    }

    private fun checkOpen() {
        check(lifecycleState == LifecycleState.OPEN) { "DatabaseManager is closing or closed" }
    }

    /**
     * Admits one serialized manager operation (anything that takes [mutex]) and drains it on completion.
     *
     * Registration happens BEFORE the operation waits on [mutex] (after the lifecycle check); the token is removed in
     * `finally` so cancellation, merge failure, and timeout all release admission. A [close] that observes a non-empty
     * admission set arms [managerOperationDrain] and bound-waits on it before taking [mutex] for its snapshot, so a
     * wedged admitted operation cannot pin shutdown past [WRITER_DRAIN_TIMEOUT_MS].
     */
    private suspend fun <T> withManagerOperation(block: suspend () -> T): T {
        val token = Any()
        writerTrackerMutex.withLock {
            checkOpen()
            activeManagerOperations.add(token)
        }
        try {
            return block()
        } finally {
            withContext(NonCancellable) {
                writerTrackerMutex.withLock {
                    activeManagerOperations.remove(token)
                    if (activeManagerOperations.isEmpty()) {
                        managerOperationDrain?.complete(Unit)
                    }
                }
            }
        }
    }

    /**
     * Builds a new [MeshtasticDatabase] for [dbName]. Tests override this to control file placement (temp directory
     * instead of the platform data dir). Production delegates to the platform-specific [getDatabaseBuilder].
     */
    protected open fun buildDatabase(dbName: String): MeshtasticDatabase = getDatabaseBuilder(dbName).build()

    /**
     * Resolves the DB name to use for [address], honoring a cross-transport alias when one exists. A secondary
     * transport (e.g. TCP) that has been unified with a node points at the DB the first transport (e.g. BLE) claimed;
     * without an alias this falls back to the address-hashed name — today's default — for a first-time or primary
     * connection. See [associateDevice].
     */
    @Suppress("ReturnCount")
    private suspend fun resolveDbName(address: String?, canReclaimRecoveredSource: Boolean): String {
        val fallback = buildDbName(address)
        if (fallback == DatabaseConstants.DEFAULT_DB_NAME) return fallback
        val transportAddress = address ?: return fallback
        val aliasKey = addrDbKey(transportAddress)
        val pendingSourceKey = pendingSourceDbKey(transportAddress)
        val pendingDestinationKey = pendingDestinationDbKey(transportAddress)
        val prefs = datastore.data.first()

        val pendingSource = prefs[pendingSourceKey]
        val pendingDestination = prefs[pendingDestinationKey]
        if (pendingSource == null && pendingDestination == null) return prefs[aliasKey] ?: fallback
        if (pendingSource == null || pendingDestination == null) {
            datastore.edit {
                it.remove(pendingSourceKey)
                it.remove(pendingDestinationKey)
            }
            return prefs[aliasKey] ?: fallback
        }

        // A pending route is only intent. The destination's merge marker is the durable proof that the data copy
        // committed. Verify it before publishing either database so no caller can write to a merged-away fallback.
        val destinationDb = withContext(dispatchers.io) { getOrOpenDatabase(pendingDestination) }
        val mergeCommitted = verifyMergeMarker(destinationDb, pendingSource)
        if (!mergeCommitted) {
            datastore.edit {
                it.remove(pendingSourceKey)
                it.remove(pendingDestinationKey)
            }
            return prefs[aliasKey] ?: fallback
        }

        // If this process may already have published the source pool, its durable retirement and in-memory
        // protection are one cancellation-atomic transition. Otherwise cancellation after DataStore commits but
        // before logicallyRetired is updated could let cache eviction close/delete a pool still held by consumers.
        withContext(NonCancellable) {
            datastore.edit {
                it[aliasKey] = pendingDestination
                it.remove(pendingSourceKey)
                it.remove(pendingDestinationKey)
                it[lastUsedKey(pendingDestination)] = nowMillis
                it[retiredDbNamesKey] = it[retiredDbNamesKey].orEmpty() + pendingSource
            }
            if (!canReclaimRecoveredSource) logicallyRetired.add(pendingSource)
        }
        if (canReclaimRecoveredSource) {
            physicallyRetireDatabase(pendingSource)
            currentCoroutineContext().ensureActive()
        }
        Logger.i {
            "Repaired pending route from ${anonymizeDbName(pendingSource)} to " + anonymizeDbName(pendingDestination)
        }
        return pendingDestination
    }

    /** Reads the destination merge marker used as commit proof for pending-route recovery. */
    protected open suspend fun verifyMergeMarker(destination: MeshtasticDatabase, sourceName: String): Boolean =
        destination.mergeMarkerDao().isMerged(sourceName)

    /** Switch active database to the one associated with [address]. Serialized via mutex. */
    override suspend fun switchActiveDatabase(address: String?) = withManagerOperation {
        mutex.withLock {
            checkOpen()
            cleanupPersistedRetirementsAtStartup()
            val dbName = resolveDbName(address, canReclaimRecoveredSource = !hasOpenedDeviceDatabase)

            // Remember the previously active DB name (any) so we can record its last-used time as well.
            val previousDbName = currentDbName

            // Fast path: no-op only when both the selected address and its resolved database are already active.
            // resolveDbName() may repair a committed pending route to a different destination for the same address.
            if (_currentAddress.value == address && currentDbName == dbName) {
                markLastUsed(dbName)
                return@withLock
            }

            // Build/open Room DB off the main thread
            val db = withContext(dispatchers.io) { getOrOpenDatabase(dbName) }
            if (dbName != DatabaseConstants.DEFAULT_DB_NAME) hasOpenedDeviceDatabase = true

            // Emit the new DB BEFORE closing the old ones. flatMapLatest collectors on
            // currentDb will cancel their in-flight queries on the previous database once
            // the new value is emitted. Closing the old pool first would race with those
            // collectors, causing "Connection pool is closed" crashes.
            writerTrackerMutex.withLock {
                _currentDb.value = db
                currentDbName = dbName
            }
            _currentAddress.value = address
            markLastUsed(dbName)
            // Also mark the previous DB as used "just now" so LRU has an accurate, recent timestamp
            markLastUsed(previousDbName)

            // Do NOT close the previous DB synchronously here. Even though _currentDb has been
            // updated, in-flight `withDb` calls may still hold a reference to the old database
            // (captured before the emission). Closing the connection pool while those queries are
            // executing causes "Connection pool is closed" crashes. Instead, let LRU eviction
            // (enforceCacheLimit) handle cleanup — it only runs on databases that are not the
            // active target and have not been used recently.

            schedulePostSwitchMaintenance(dbName = dbName, db = db)

            Logger.i { "Switched active DB to ${anonymizeDbName(dbName)} for address ${anonymizeAddress(address)}" }
        }
    }

    /**
     * Schedules deferred maintenance that runs after switching the active database. Posts work to [managerScope] on
     * [dispatchers.io] so the switch path is not blocked by filesystem or search-index I/O.
     *
     * In production this schedules LRU cache-limit enforcement, legacy-DB cleanup, and FTS search-index backfill.
     * In-memory test fixtures override it to no-op because they do not have a filesystem-backed database directory and
     * must not access platform context singletons (e.g. `ContextServices.app`).
     */
    protected open fun schedulePostSwitchMaintenance(dbName: String, db: MeshtasticDatabase) {
        // Defer LRU eviction so switch is not blocked by filesystem work
        launchManagerWork(dispatchers.io) { enforceCacheLimit() }

        // One-time cleanup: remove legacy DB if present and not active
        launchManagerWork(dispatchers.io) { cleanupLegacyDbIfNeeded(activeDbName = dbName) }

        // Backfill FTS search index for any text messages missing messageText.
        // On the first real device DB, defer this so it does not starve the single DB connection while
        // the UI is collecting startup flows. The default DB should not consume the cold-start delay.
        val shouldDelayBackfill = dbName != DatabaseConstants.DEFAULT_DB_NAME && !hasDelayedFirstDeviceBackfill
        if (shouldDelayBackfill) hasDelayedFirstDeviceBackfill = true
        scheduleSearchIndexBackfill(dbName = dbName, db = db, shouldDelayBackfill = shouldDelayBackfill)
    }

    @Suppress("TooGenericExceptionCaught", "CyclomaticComplexMethod", "LongMethod")
    override suspend fun associateDevice(
        address: String,
        nodeNum: Int,
        deviceId: String?,
        isSessionActive: () -> Boolean,
    ) = withManagerOperation {
        try {
            mutex.withLock {
                fun ensureAssociationActive() {
                    if (!isSessionActive()) throw StaleAssociationException()
                }

                checkOpen()
                ensureAssociationActive()
                if (_currentAddress.value != address) {
                    Logger.i {
                        "Ignored stale database association for ${anonymizeAddress(address)}; active transport is " +
                            anonymizeAddress(_currentAddress.value)
                    }
                    return@withLock
                }
                val sourceName = currentDbName
                // Never claim or merge into the sentinel "no device" DB.
                if (sourceName == DatabaseConstants.DEFAULT_DB_NAME) return@withLock

                // The device-id claim is the durable one (node numbers renumber under firmware 2.8); the
                // node-num claim stays as the fallback for hardware without a device id, for lockdown
                // sessions (device_id zeroed), and for claims written by older app versions. Writes always
                // refresh both keys so either lookup path resolves on the next connection.
                val deviceKey = validDeviceIdOrNull(deviceId)?.let(::deviceDbPrefKey)
                val nodeKey = nodeDbPrefKey(nodeNum)
                val prefs = datastore.data.first()
                val claimed = resolveDbClaim(prefs, deviceKey, nodeKey)
                suspend fun writeClaims(dbName: String) = datastore.edit {
                    ensureAssociationActive()
                    deviceKey?.let { key -> it[key] = dbName }
                    it[nodeKey] = dbName
                    ensureAssociationActive()
                }

                when {
                    claimed == null -> {
                        // First transport to learn this device: its current DB becomes the device's canonical DB.
                        // No address alias is needed — a primary connection already resolves to this DB via
                        // buildDbName.
                        writeClaims(sourceName)
                        Logger.i { "Claimed ${anonymizeDbName(sourceName)} as canonical DB for node $nodeNum" }
                    }

                    claimed == sourceName -> {
                        // Already unified — backfill or refresh any stale/missing routing metadata atomically.
                        // This also repairs a post-merge routing failure (merge committed but DataStore edit failed):
                        // the next connect reaches this branch and writes claims + alias in one edit without
                        // re-copying source (the merge marker prevents duplicate data).
                        val addressKey = addrDbKey(address)
                        val needsDeviceKey = deviceKey != null && prefs[deviceKey] != sourceName
                        val needsNodeKey = prefs[nodeKey] != sourceName
                        val needsAlias = prefs[addressKey] != sourceName
                        val pendingSourceKey = pendingSourceDbKey(address)
                        val pendingDestinationKey = pendingDestinationDbKey(address)
                        val needsPendingCleanup =
                            prefs[pendingSourceKey] != null || prefs[pendingDestinationKey] != null
                        if (needsDeviceKey || needsNodeKey || needsAlias || needsPendingCleanup) {
                            datastore.edit {
                                ensureAssociationActive()
                                deviceKey?.let { key -> if (needsDeviceKey) it[key] = sourceName }
                                if (needsNodeKey) it[nodeKey] = sourceName
                                if (needsAlias) it[addressKey] = sourceName
                                it.remove(pendingSourceKey)
                                it.remove(pendingDestinationKey)
                                it[lastUsedKey(sourceName)] = nowMillis
                                ensureAssociationActive()
                            }
                            Logger.i { "Refreshed routing metadata for ${anonymizeDbName(sourceName)}" }
                        }
                    }

                    else -> {
                        // Secondary transport reached an already-known node: fold this DB into the canonical one,
                        // switch the active DB to it, alias this address to it, and retire the now-merged source.
                        ensureAssociationActive()
                        val source = _currentDb.value
                        val dest = withContext(dispatchers.io) { getOrOpenDatabase(claimed) }
                        ensureAssociationActive()

                        // Arm the writer-admission gate before any drain or merge work. The complete armed lifetime is
                        // enclosed by the try/finally below, so every CancellationException, Exception, and Error
                        // releases blocked writers onto source before commit or destination after commit.
                        val gate = CompletableDeferred<MeshtasticDatabase>()
                        val transportAddress = address
                        var mergeCommitted = false
                        var retirementPersisted = false

                        // Publishes and completes this exact gate once. A repeated cleanup call is harmless and cannot
                        // clear a later association's gate.
                        suspend fun releaseWriterGate(canonicalDb: MeshtasticDatabase, canonicalName: String) {
                            withContext(NonCancellable) {
                                val released =
                                    writerTrackerMutex.withLock {
                                        if (writerGate !== gate) {
                                            false
                                        } else {
                                            writerGate = null
                                            _currentDb.value = canonicalDb
                                            currentDbName = canonicalName
                                            true
                                        }
                                    }
                                if (released) gate.complete(canonicalDb)
                            }
                        }

                        suspend fun clearPendingRouteBestEffort() {
                            withContext(NonCancellable) {
                                try {
                                    datastore.edit {
                                        it.remove(pendingSourceDbKey(transportAddress))
                                        it.remove(pendingDestinationDbKey(transportAddress))
                                    }
                                } catch (cleanupFailure: Throwable) {
                                    Logger.w(cleanupFailure) { "Failed to clear aborted pending database route" }
                                }
                            }
                        }

                        try {
                            // Start the outer try before arming the gate. Once writerGate is assigned, no Throwable can
                            // escape without running the identity-checked release in finally.
                            writerTrackerMutex.withLock {
                                check(writerGate == null) { "Database writer gate already armed" }
                                writerGate = gate
                            }
                            try {
                                // Phase 1: drain every writer admitted against source before this gate was armed.
                                val drained = withContext(dispatchers.io) { drainWriters(source, sourceName) }
                                if (!drained) {
                                    Logger.w {
                                        "Aborted merge of ${anonymizeDbName(sourceName)} into " +
                                            "${anonymizeDbName(claimed)}: writer drain timed out; kept " +
                                            "${anonymizeDbName(sourceName)} active"
                                    }
                                    return@withLock
                                }

                                // Phase 2: persist address-scoped intent, commit the database merge and marker, then
                                // finalize every route and remove intent in one DataStore transaction. If finalization
                                // fails after the merge commits, a later switch verifies the marker and repairs the
                                // alias before publishing.
                                withContext(NonCancellable + dispatchers.io) {
                                    datastore.edit {
                                        ensureAssociationActive()
                                        it[pendingSourceDbKey(transportAddress)] = sourceName
                                        it[pendingDestinationDbKey(transportAddress)] = claimed
                                        ensureAssociationActive()
                                    }
                                    mergeDatabases(source, dest, sourceName, isSessionActive)
                                    mergeCommitted = true
                                    ensureAssociationActive()
                                    datastore.edit {
                                        ensureAssociationActive()
                                        deviceKey?.let { key -> it[key] = claimed }
                                        it[nodeKey] = claimed
                                        it[addrDbKey(transportAddress)] = claimed
                                        it.remove(pendingSourceDbKey(transportAddress))
                                        it.remove(pendingDestinationDbKey(transportAddress))
                                        it[lastUsedKey(claimed)] = nowMillis
                                        it[retiredDbNamesKey] = it[retiredDbNamesKey].orEmpty() + sourceName
                                        ensureAssociationActive()
                                    }
                                    retirementPersisted = true
                                    Logger.i {
                                        "Unified ${anonymizeDbName(
                                            sourceName,
                                        )} into ${anonymizeDbName(claimed)} for node $nodeNum"
                                    }
                                }
                                currentCoroutineContext().ensureActive()
                            } catch (failure: Throwable) {
                                if (!mergeCommitted) {
                                    clearPendingRouteBestEffort()
                                }
                                when (failure) {
                                    is CancellationException,
                                    is StaleAssociationException,
                                    -> throw failure

                                    is Exception -> {
                                        if (!mergeCommitted) {
                                            Logger.w(failure) {
                                                "Merge into ${anonymizeDbName(claimed)} failed; " +
                                                    "kept ${anonymizeDbName(sourceName)} active"
                                            }
                                        } else {
                                            Logger.w(failure) {
                                                "Routing metadata for ${anonymizeDbName(claimed)} failed after merge " +
                                                    "commit; destination remains active and the pending route will " +
                                                    "repair the address alias on a later switch"
                                            }
                                        }
                                        return@withLock
                                    }

                                    else -> throw failure
                                }
                            }
                        } finally {
                            withContext(NonCancellable) {
                                if (mergeCommitted) {
                                    releaseWriterGate(dest, claimed)
                                    recordLogicalRetirement(sourceName, persistIntent = !retirementPersisted)
                                } else {
                                    releaseWriterGate(source, sourceName)
                                }
                            }
                        }
                    }
                }
            }
        } catch (_: StaleAssociationException) {
            Logger.i {
                "Aborted stale database association for ${anonymizeAddress(address)} after transport-session rollover"
            }
        }
    }

    /**
     * Logically retires a database whose contents have been merged into another.
     *
     * Physical close/delete is deferred — the merged source was published through [currentDb]; app-wide Flow, Paging,
     * UI, worker, and one-shot read consumers may still hold its Room instance. Physically closing it now can surface
     * "Connection pool is closed" to those readers (see [switchActiveDatabase] and [reopenActiveDatabaseIfStillCurrent]
     * no-sync-close discipline). Retirement intent is persisted so the next manager lifetime can safely remove the file
     * before opening a device DB; [close] also performs orderly physical teardown when the platform invokes it.
     */
    private suspend fun recordLogicalRetirement(dbName: String, persistIntent: Boolean) {
        // associateDevice already owns the manager mutex. Reacquiring it here would deadlock finalization.
        logicallyRetired.add(dbName)
        if (persistIntent) persistRetirementIntent(dbName)
        Logger.i { "Logically retired merged DB ${anonymizeDbName(dbName)}; physical cleanup deferred" }
    }

    /** Persists merge retirement separately as a fallback when post-commit route finalization failed. */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun persistRetirementIntent(dbName: String) {
        try {
            datastore.edit { it[retiredDbNamesKey] = it[retiredDbNamesKey].orEmpty() + dbName }
        } catch (failure: Throwable) {
            Logger.w(failure) { "Failed to persist retirement for ${anonymizeDbName(dbName)}" }
        }
    }

    /**
     * Reclaims retirement intents left by a previous application process. This runs at most once and before opening any
     * device DB, which is the only point where no consumer from this process can hold a retired Room instance.
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun cleanupPersistedRetirementsAtStartup() {
        if (attemptedStartupRetirementCleanup) return
        attemptedStartupRetirementCleanup = true
        val retiredNames =
            try {
                datastore.data.first()[retiredDbNamesKey].orEmpty()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                Logger.w(failure) { "Failed to read persisted database retirements" }
                return
            }
        retiredNames.forEach {
            currentCoroutineContext().ensureActive()
            physicallyRetireDatabase(it)
            currentCoroutineContext().ensureActive()
        }
    }

    /** Deletes one retired file, then atomically clears its retirement and last-used metadata. */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun physicallyRetireDatabase(dbName: String) {
        try {
            deleteDatabaseFiles(dbName)
        } catch (failure: Throwable) {
            Logger.w(failure) { "Failed to delete retired database ${anonymizeDbName(dbName)}" }
            return
        }
        try {
            datastore.edit {
                it.remove(lastUsedKey(dbName))
                val remaining = it[retiredDbNamesKey].orEmpty() - dbName
                if (remaining.isEmpty()) it.remove(retiredDbNamesKey) else it[retiredDbNamesKey] = remaining
            }
            Logger.i { "Physically retired merged DB ${anonymizeDbName(dbName)}" }
        } catch (failure: Throwable) {
            // The file deletion is idempotent. Retain the intent so a later process retries metadata cleanup.
            Logger.w(failure) { "Failed to clear retirement metadata for ${anonymizeDbName(dbName)}" }
        }
    }

    /**
     * Closes and removes a cached database by name. Safe to call even if the database was already closed or not in the
     * cache. Does NOT delete the underlying file — the database can be re-opened on next access.
     *
     * Room KMP is configured with a single-connection pool on every platform and has no common auto-close timeout, so
     * an idle cached database keeps that connection open until explicitly closed. This method is the primary mechanism
     * for releasing it when a database is no longer the active target. The caller must hold [mutex].
     */
    @Suppress("TooGenericExceptionCaught")
    protected open suspend fun closeCachedDatabase(dbName: String) {
        val database = dbCache[dbName] ?: return
        try {
            closeDatabase(database)
        } catch (failure: Throwable) {
            Logger.w(failure) { "Failed to close cached database ${anonymizeDbName(dbName)}" }
            throw failure
        }
        dbCache.remove(dbName)
        Logger.d { "Closed inactive database ${anonymizeDbName(dbName)} to free connections" }
    }

    /** Room-close seam used by deterministic shutdown tests. */
    protected open fun closeDatabase(database: MeshtasticDatabase) = database.close()

    /**
     * Reopens the active database under [mutex], but only if it hasn't switched since the caller snapshotted it.
     *
     * The replaced Room instance is intentionally left open for the rest of the process. [currentDb] reads [_currentDb]
     * directly, so every publication is visible to app-wide collectors on the same program step — but there is no
     * deterministic handoff point where every collector has stopped using the previous instance.
     *
     * Returns the reopened DB, or null if another coroutine switched databases or shutdown has started.
     */
    @Suppress("ReturnCount")
    private suspend fun reopenActiveDatabaseIfStillCurrent(
        expectedDb: MeshtasticDatabase,
        expectedDbName: String,
    ): MeshtasticDatabase? = withManagerOperation {
        mutex.withLock {
            if (lifecycleState != LifecycleState.OPEN) return@withManagerOperation null
            if (_currentDb.value !== expectedDb || currentDbName != expectedDbName) return@withManagerOperation null

            val registered =
                dbCache[expectedDbName]
                    ?: if (expectedDbName == DatabaseConstants.DEFAULT_DB_NAME) {
                        synchronized(initializationLock) { initializedDefaultDb }
                    } else {
                        null
                    }
            if (registered !== expectedDb) {
                Logger.w { "withDb: active DB registration changed before reopen; skipping active DB reopen" }
                return@withManagerOperation null
            }

            // Build a fresh instance directly (not through getOrPut) before touching the cache,
            // so a failed or cancelled build leaves the existing cache entry and _currentDb consistent.
            val reopened = withContext(dispatchers.io) { buildDatabase(expectedDbName) }
            if (lifecycleState != LifecycleState.OPEN) {
                runCatching { closeDatabase(reopened) }
                    .onFailure {
                        detachedDatabases.add(NamedDatabase(expectedDbName, reopened))
                        Logger.w(it) { "Failed to close database built during shutdown; retained for shutdown retry" }
                    }
                return@withManagerOperation null
            }
            dbCache[expectedDbName] = reopened
            if (expectedDbName == DatabaseConstants.DEFAULT_DB_NAME) {
                synchronized(initializationLock) { initializedDefaultDb = reopened }
            }
            _currentDb.value = reopened
            if (detachedDatabases.none { it.database === expectedDb }) {
                detachedDatabases.add(NamedDatabase(expectedDbName, expectedDb))
            }

            // Intentionally do not close expectedDb here. The public currentDb Flow exposes _currentDb directly,
            // so downstream flatMapLatest collectors may still be using the replaced Room instance after this
            // function emits the reopened DB. Closing the old pool here can surface "Connection pool is closed"
            // to app-wide DB observers that do not have closed-pool recovery. This mirrors switchActiveDatabase's
            // no-sync-close discipline. [close] owns the detached-pool set and reclaims every replaced instance after
            // all
            // application consumers and admitted writers have stopped.

            reopened
        }
    }

    // Short-term runtime containment: route withDb entry through a single-lane dispatcher to narrow the Room/SQLite
    // connection-pool churn window seen during device/firmware update flows. Room suspend DAOs may continue on Room's
    // own executor after suspension, so this is not a strict global DB-I/O serialization guarantee. Preserve bounded
    // one-shot DB-critical blocks through cancellation, then re-check cancellation so stale callers do not continue
    // after the DB releases. Long-lived Flow/Paging reads must stay out of withDb; revisit after direct currentDb.value
    // callers are audited and safe DB concurrency can be restored.
    protected open val limitedIo: CoroutineDispatcher by lazy { dispatchers.io.limitedParallelism(1) }

    /**
     * Executes [block] once against the admitted current DB instance.
     *
     * A callback is never replayed after it starts: an arbitrary block can perform one side effect and then fail, so
     * transparently invoking it again against another pool could duplicate or split a logical write. Pool-timeout
     * recovery may reopen the active database for future calls, but the original failure is still propagated.
     */
    @Suppress("TooGenericExceptionCaught", "ReturnCount")
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

    /**
     * Atomically snapshots the canonical active DB (held by [_currentDb], which initializes lazily to the default DB)
     * and registers a writer against it.
     *
     * If an association attempt is in flight, the writer-admission gate is armed. The caller snapshots that gate under
     * [writerTrackerMutex], awaits it outside the lock, then retries admission from the beginning. Selecting the active
     * database and registering the writer happen in the same critical section, so an association cannot arm its gate
     * between those operations. This guarantees a new `withDb` never writes to a DB that is being retired, nor lands on
     * `dest` before its data exists.
     */
    private data class AdmittedDatabase(val database: MeshtasticDatabase, val name: String)

    private suspend fun beginWrite(): AdmittedDatabase {
        while (true) {
            var admitted: AdmittedDatabase? = null
            val gate =
                writerTrackerMutex.withLock {
                    checkOpen()
                    val pendingGate = writerGate
                    if (pendingGate == null) {
                        val db = _currentDb.value
                        activeWriters[db] = (activeWriters[db] ?: 0) + 1
                        admitted = AdmittedDatabase(database = db, name = currentDbName)
                    }
                    pendingGate
                }
            admitted?.let {
                return it
            }
            val pendingGate = checkNotNull(gate) { "Writer admission produced neither a database nor a gate" }
            val released =
                withTimeoutOrNull(WRITER_GATE_TIMEOUT_MS) {
                    pendingGate.await()
                    true
                }
            if (released == null) {
                throw IllegalStateException(
                    "Timed out waiting ${WRITER_GATE_TIMEOUT_MS}ms for database writer admission gate",
                )
            }
        }
    }

    /** Deregisters a writer and releases any merge waiting for [db] to quiesce. Cancellation-safe (see call site). */
    private suspend fun endWrite(db: MeshtasticDatabase) {
        val retryEviction =
            writerTrackerMutex.withLock {
                val remaining = (activeWriters[db] ?: 1) - 1
                val drained = remaining <= 0
                if (drained) {
                    activeWriters.remove(db)
                    drainWaiters.remove(db)?.forEach { it.complete(Unit) }
                } else {
                    activeWriters[db] = remaining
                }
                if (activeWriters.isEmpty()) shutdownWriterDrain?.complete(Unit)
                drained && deferredEvictions.remove(db)
            }
        if (retryEviction && lifecycleState == LifecycleState.OPEN) {
            launchManagerWork(dispatchers.io) { enforceCacheLimit() }
        }
    }

    /**
     * Folds [source] into [dest]. Override in tests to inject merge failures. Production delegates to
     * [DatabaseMerger.merge]; the merge runs in a single transaction so a crash rolls back cleanly and the destination
     * is never left half-merged.
     */
    protected open suspend fun mergeDatabases(
        source: MeshtasticDatabase,
        dest: MeshtasticDatabase,
        sourceName: String,
        isAssociationActive: () -> Boolean,
    ) {
        DatabaseMerger.merge(source, dest, sourceName, isAssociationActive)
    }

    /**
     * Test-only snapshot of the writer tracker: total live writers and total pending drain waiters. Both are zero once
     * every association attempt has released its gate and drained its source — a non-zero pair after a quiescent period
     * indicates a leaked writer or waiter.
     */
    internal suspend fun debugWriterCounts(): Pair<Int, Int> =
        writerTrackerMutex.withLock { activeWriters.values.sum() to drainWaiters.values.sumOf { it.size } }

    /** Test-only visibility for asserting an association did not leak writer admission. */
    internal suspend fun debugWriterGateArmed(): Boolean = writerTrackerMutex.withLock { writerGate != null }

    /** Test-only visibility for cancellation-atomic pending-route recovery assertions. */
    internal suspend fun debugIsLogicallyRetired(dbName: String): Boolean =
        mutex.withLock { dbName in logicallyRetired }

    /** Test-only visibility for deterministic shutdown assertions. */
    internal fun debugAcceptingWrites(): Boolean = lifecycleState == LifecycleState.OPEN

    /**
     * Suspends until every writer that captured [db] before this call has finished, so a merge never snapshots [db]
     * while a write is still in flight (and then loses it when [db] is retired). Bounded by [WRITER_DRAIN_TIMEOUT_MS]
     * so a wedged writer can't pin the merge — and [mutex] — forever.
     *
     * Returns `true` if all writers drained (or none were active), `false` on timeout. The caller must abort the merge
     * and roll the active DB back to source on `false`.
     *
     * The waiter is removed in a [finally] block on every exit path — success, timeout, and external cancellation — so
     * a stale [CompletableDeferred] never leaks into [drainWaiters]. The cleanup runs under [NonCancellable] so
     * cancellation during cleanup doesn't skip the removal.
     */
    @Suppress("ReturnCount")
    private suspend fun drainWriters(db: MeshtasticDatabase, dbName: String): Boolean {
        val waiter =
            writerTrackerMutex.withLock {
                if ((activeWriters[db] ?: 0) == 0) return true
                CompletableDeferred<Unit>().also { drainWaiters.getOrPut(db) { mutableListOf() }.add(it) }
            }
        try {
            val drained = withTimeoutOrNull(WRITER_DRAIN_TIMEOUT_MS) { waiter.await() }
            if (drained == null) {
                Logger.w { "Timed out draining writers on ${anonymizeDbName(dbName)} before merge" }
                return false
            }
            return true
        } finally {
            // Remove our waiter on every exit path. On success, endWrite may have already removed the
            // entire list — the removal is idempotent. On timeout or cancellation, the waiter is still
            // registered and must be cleaned up so a late endWrite doesn't complete a dead deferred.
            withContext(NonCancellable) {
                writerTrackerMutex.withLock {
                    val list = drainWaiters[db]
                    if (list != null) {
                        list.remove(waiter)
                        if (list.isEmpty()) drainWaiters.remove(db)
                    }
                }
            }
        }
    }

    @Suppress("ReturnCount", "ThrowsCount", "TooGenericExceptionCaught", "CyclomaticComplexMethod")
    private suspend fun <T> withCurrentDb(block: suspend (MeshtasticDatabase) -> T): T? {
        val admission = beginWrite()
        val db = admission.database
        val active = admission.name
        try {
            return runCancellableDbBlock(db, block)
        } catch (e: CancellationException) {
            throw e // Preserve structured concurrency cancellation propagation.
        } catch (e: Exception) {
            // Shutdown in progress: do not touch _currentDb (its getter calls checkOpen()) nor attempt a reopen that
            // would build a pool. Propagate the original failure with its message intact.
            if (lifecycleState != LifecycleState.OPEN) throw e
            val currentDb = _currentDb.value
            if (currentDb !== db && isDbClosedException(e)) {
                Logger.w {
                    "withDb: database closed during switch (${e.message}); callback will not be replayed automatically"
                }
                throw e
            }

            // Same active DB but Room's connection pool is wedged. Reopen for future calls, but do not replay this
            // callback: it may already have completed an earlier side effect before the timeout surfaced.
            if (currentDb === db && isDbPoolAcquireTimeoutException(e)) {
                val reopened =
                    try {
                        reopenActiveDatabaseIfStillCurrent(db, active)
                    } catch (recoveryCancel: CancellationException) {
                        throw recoveryCancel
                    } catch (recoveryFailure: Exception) {
                        e.addSuppressed(recoveryFailure)
                        Logger.w(recoveryFailure) {
                            "withDb: failed to reopen active DB after a connection-pool timeout"
                        }
                        null
                    }
                Logger.w {
                    if (reopened != null) {
                        "withDb: reopened active DB after transient Room connection-pool timeout; " +
                            "failed callback was not replayed"
                    } else {
                        "withDb: active DB was not reopened during timeout recovery; failed callback was " +
                            "not replayed"
                    }
                }
                throw e
            }

            throw e
        } finally {
            // NonCancellable so a cancelled withDb still deregisters — a leaked +1 would make every future
            // drain on this DB instance time out.
            withContext(NonCancellable) { endWrite(db) }
        }
    }

    private suspend fun <T> runCancellableDbBlock(db: MeshtasticDatabase, block: suspend (MeshtasticDatabase) -> T): T {
        // Keep withDb callbacks bounded and one-shot: NonCancellable can hold the containment lane until this returns.
        currentCoroutineContext().ensureActive()
        val result = withContext(NonCancellable) { block(db) }
        currentCoroutineContext().ensureActive()
        return result
    }

    private fun isDbClosedException(e: Exception): Boolean = isDbPoolAcquireTimeoutException(e) ||
        generateSequence<Throwable>(e) { it.cause }
            .any { throwable ->
                val msg = throwable.message?.lowercase() ?: return@any false
                val hasDbContext = DB_TERMS.any { it in msg }
                ("closed" in msg && hasDbContext) || "database is locked" in msg || "sqlite_busy" in msg
            }

    internal companion object {
        private const val BACKFILL_COLD_START_DELAY_MS = 2_000L
        private const val WITH_DB_SLOW_OPERATION_MS = 1_000L

        /**
         * Upper bound on how long a merge waits for in-flight writers on the source DB to drain (see [drainWriters]).
         */
        private const val WRITER_DRAIN_TIMEOUT_MS = 5_000L
        private const val WRITER_GATE_TIMEOUT_MS = 30_000L
        val DB_TERMS = listOf("pool", "database", "connection", "sqlite")

        private const val ROOM_POOL_ACQUIRE_TIMEOUT_PHRASE = "timed out attempting to acquire"
        private const val ROOM_READER_CONNECTION_PHRASE = "reader connection"
        private const val ROOM_WRITER_CONNECTION_PHRASE = "writer connection"

        /**
         * Room KMP currently exposes pool-acquire timeouts as exception message text instead of a stable common typed
         * signal. Keep this fallback narrow so BLE/GATT/transport connection errors do not trigger DB reopen recovery.
         */
        private fun isRoomPoolAcquireTimeoutMessage(message: String): Boolean =
            ROOM_POOL_ACQUIRE_TIMEOUT_PHRASE in message &&
                (ROOM_READER_CONNECTION_PHRASE in message || ROOM_WRITER_CONNECTION_PHRASE in message)

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
        launchManagerWork { datastore.edit { it[lastUsedKey(dbName)] = nowMillis } }
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

    private suspend fun enforceCacheLimit() = withManagerOperation {
        mutex.withLock {
            // Deferred enforcement can wait behind a later switch. Resolve the protected name under the same mutex
            // that publishes currentDbName so the active database at execution time can never become an LRU victim.
            val activeDbName = currentDbName
            val limit = getCurrentCacheLimit()
            val all = listExistingDbNames()
            val pendingRouteNames = pendingRouteDbNames(datastore.data.first())
            val detachedDbNames = detachedDatabases.mapTo(mutableSetOf()) { it.dbName }
            // Only enforce the limit over device-specific DBs. A detached pool is still live for a consumer of an
            // earlier currentDb emission, so its files must remain protected until orderly shutdown.
            val deviceDbs =
                all.filterNot {
                    it in logicallyRetired ||
                        it in detachedDbNames ||
                        it == DatabaseConstants.LEGACY_DB_NAME ||
                        it == DatabaseConstants.DEFAULT_DB_NAME
                }

            if (deviceDbs.size <= limit) return@withLock
            val usageSnapshot = deviceDbs.associateWith { lastUsed(it) }
            // A pending destination can be the only merged copy and its merge marker is the proof needed to repair
            // the route after a crash. Keep both route endpoints until address-scoped recovery finalizes or clears it.
            val victims =
                selectEvictionVictims(
                    dbNames = deviceDbs,
                    activeDbName = activeDbName,
                    limit = limit,
                    lastUsedMsByDb = usageSnapshot,
                    protectedDbNames = pendingRouteNames,
                )
            val evictableVictims =
                writerTrackerMutex.withLock {
                    victims.filter { name ->
                        val cached = dbCache[name]
                        if (cached != null && (activeWriters[cached] ?: 0) > 0) {
                            deferredEvictions.add(cached)
                            false
                        } else {
                            true
                        }
                    }
                }

            evictableVictims.forEach { name ->
                try {
                    closeCachedDatabase(name)
                    deleteDatabaseFiles(name)
                    datastore.edit { it.remove(lastUsedKey(name)) }
                    Logger.i { "Evicted cached DB ${anonymizeDbName(name)}" }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
                    Logger.w(failure) { "Failed to evict database ${anonymizeDbName(name)}" }
                }
            }
        }
    }

    private suspend fun cleanupLegacyDbIfNeeded(activeDbName: String) = withManagerOperation {
        mutex.withLock {
            val cleaned = datastore.data.first()[legacyCleanedKey] ?: false
            if (cleaned) return@withLock

            val legacy = DatabaseConstants.LEGACY_DB_NAME
            if (legacy == activeDbName) {
                datastore.edit { it[legacyCleanedKey] = true }
                return@withLock
            }

            if (dbFileExists(legacy)) {
                try {
                    closeCachedDatabase(legacy)
                    deleteDatabaseFiles(legacy)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
                    Logger.w(failure) { "Failed to delete legacy database ${anonymizeDbName(legacy)}" }
                    return@withLock
                }
                Logger.i { "Deleted legacy DB ${anonymizeDbName(legacy)}" }
            }
            datastore.edit { it[legacyCleanedKey] = true }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun scheduleSearchIndexBackfill(dbName: String, db: MeshtasticDatabase, shouldDelayBackfill: Boolean) {
        backfillJob?.cancel()
        backfillJob =
            launchManagerWork(dispatchers.io) {
                try {
                    if (shouldDelayBackfill) delay(BACKFILL_COLD_START_DELAY_MS)
                    if (_currentDb.value !== db) return@launchManagerWork
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
    internal suspend fun backfillSearchIndexIfNeeded(scheduledDb: MeshtasticDatabase) {
        val admission = beginWrite()
        val admittedDb = admission.database
        try {
            if (admittedDb !== scheduledDb) return
            runCancellableDbBlock(admittedDb) { performSearchIndexBackfill(it) }
        } finally {
            withContext(NonCancellable) { endWrite(admittedDb) }
        }
    }

    /** Performs count, message-text backfill, and FTS rebuild while caller holds one writer admission. */
    protected open suspend fun performSearchIndexBackfill(db: MeshtasticDatabase) {
        if (db.packetDao().countPacketsNeedingBackfill() == 0) return
        val count = db.packetDao().backfillMessageTexts()
        if (count > 0) {
            Logger.i { "Backfilled $count messages for FTS search index" }
            db.packetDao().rebuildFtsIndex()
            Logger.i { "FTS search index rebuild complete" }
        }
    }

    /** Platform file removal seam used by orderly retirement and test fixtures. */
    protected open fun deleteDatabaseFiles(dbName: String) = deleteDatabase(dbName)

    /**
     * Establishes an orderly shutdown boundary: rejects new work, bounds manager-job cancellation, admitted
     * serialized-operation draining, and admitted-writer draining, waits for the last serialized switch/association to
     * finalize, then closes every manager-owned Room instance. If a cancelled child, admitted operation, writer, or
     * pool close cannot finish successfully, ownership is retained and physical cleanup is skipped so a later [close]
     * call can retry without losing track of live resources. Retried attempts may call Room's idempotent `close()`
     * again for pools that completed during an earlier partial attempt.
     */
    @Suppress("CyclomaticComplexMethod", "LongMethod", "TooGenericExceptionCaught")
    suspend fun close() = withContext(NonCancellable) {
        closeMutex.withLock closeAttempt@{
            var operationsDrain: CompletableDeferred<Unit>? = null
            var writerDrain: CompletableDeferred<Unit>? = null
            var jobsDrain: CompletableDeferred<Unit>? = null
            var closedSuccessfully = false

            val shouldClose =
                writerTrackerMutex.withLock {
                    when (lifecycleState) {
                        LifecycleState.CLOSED -> false

                        LifecycleState.OPEN,
                        LifecycleState.CLOSING,
                        -> {
                            lifecycleState = LifecycleState.CLOSING
                            operationsDrain =
                                if (activeManagerOperations.isNotEmpty()) {
                                    CompletableDeferred<Unit>().also { managerOperationDrain = it }
                                } else {
                                    managerOperationDrain = null
                                    null
                                }
                            writerDrain =
                                if (activeWriters.isNotEmpty()) {
                                    CompletableDeferred<Unit>().also { shutdownWriterDrain = it }
                                } else {
                                    shutdownWriterDrain = null
                                    null
                                }
                            true
                        }
                    }
                }
            if (!shouldClose) return@closeAttempt

            val managerJobs =
                synchronized(managerJobLock) {
                    val jobs = activeManagerJobs.toList()
                    jobsDrain =
                        if (jobs.isNotEmpty()) {
                            CompletableDeferred<Unit>().also { managerJobDrain = it }
                        } else {
                            managerJobDrain = null
                            null
                        }
                    jobs
                }

            try {
                // Manager-owned cleanup jobs hold manager-operation tokens. Publish cancellation before waiting for
                // those operations so cancellable I/O can unwind instead of forcing every close attempt to time
                // out.
                managerJobs.forEach { it.cancel() }

                // Bound-wait for already-admitted serialized operations before acquiring [mutex]. New work is
                // rejected
                // while CLOSING, and a timed-out attempt leaves ownership intact so a later close() can retry.
                val operationsDrained =
                    operationsDrain?.let { drain ->
                        val completedBeforeTimeout =
                            withContext(Dispatchers.Default) {
                                withTimeoutOrNull(WRITER_DRAIN_TIMEOUT_MS) {
                                    drain.await()
                                    true
                                } ?: false
                            }
                        completedBeforeTimeout || writerTrackerMutex.withLock { activeManagerOperations.isEmpty() }
                    } ?: true
                if (!operationsDrained) {
                    Logger.w {
                        "Database shutdown timed out waiting for in-flight manager operations; retaining owned pools " +
                            "for a later close retry"
                    }
                    return@closeAttempt
                }

                val managerJobsStopped =
                    jobsDrain?.let { withTimeoutOrNull(WRITER_DRAIN_TIMEOUT_MS) { it.await() } != null } ?: true
                if (!managerJobsStopped) {
                    Logger.w {
                        "Timed out stopping database manager jobs; retaining owned pools for a later close retry"
                    }
                    return@closeAttempt
                }

                val writersDrained =
                    writerDrain?.let { withTimeoutOrNull(WRITER_DRAIN_TIMEOUT_MS) { it.await() } != null } ?: true
                if (!writersDrained) {
                    Logger.w {
                        "Database shutdown could not prove every writer stopped; retaining owned pools for a later " +
                            "close retry"
                    }
                    return@closeAttempt
                }

                // Snapshot ownership without transferring it yet. A failed pool close must leave every instance and
                // retirement intent reachable by a later close() attempt.
                val snapshot =
                    mutex.withLock {
                        val databases = mutableListOf<ShutdownDatabase>()
                        fun addDistinct(dbName: String, database: MeshtasticDatabase?) {
                            if (database == null) return
                            val existing = databases.firstOrNull { it.database === database }
                            if (existing == null) {
                                databases.add(ShutdownDatabase(database, mutableSetOf(dbName)))
                            } else {
                                existing.dbNames.add(dbName)
                            }
                        }

                        dbCache.forEach { (dbName, database) -> addDistinct(dbName, database) }
                        detachedDatabases.forEach { addDistinct(it.dbName, it.database) }
                        synchronized(initializationLock) {
                            addDistinct(DatabaseConstants.DEFAULT_DB_NAME, initializedDefaultDb)
                            addDistinct(currentDbName, currentDbState?.value)
                        }

                        val persistedRetiredNames =
                            try {
                                datastore.data.first()[retiredDbNamesKey].orEmpty()
                            } catch (failure: Throwable) {
                                Logger.w(failure) {
                                    "Failed to read persisted database retirements during shutdown"
                                }
                                emptySet()
                            }
                        ShutdownSnapshot(databases, (logicallyRetired + persistedRetiredNames).toList())
                    }

                // All tracked work has stopped. The remaining scope-owned collector does not touch Room; cancel it
                // before closing pools, but keep ownership maps intact until every close succeeds.
                managerScope.coroutineContext[Job]?.cancel()
                backfillJob = null

                val failedCloseNames = mutableSetOf<String>()
                snapshot.databases.forEach { owned ->
                    runCatching { closeDatabase(owned.database) }
                        .onFailure {
                            failedCloseNames += owned.dbNames
                            Logger.w(it) { "Failed to close database during shutdown" }
                        }
                }
                if (failedCloseNames.isNotEmpty()) {
                    Logger.w {
                        "Database shutdown retained ${failedCloseNames.size} pool name(s) after close failure; " +
                            "a later close() will retry"
                    }
                    return@closeAttempt
                }

                snapshot.retiredNames.forEach { physicallyRetireDatabase(it) }

                // Only now transfer ownership and publish the terminal state. No admitted work can add another pool
                // because the manager has remained CLOSING throughout this attempt.
                mutex.withLock {
                    dbCache.clear()
                    detachedDatabases.clear()
                    logicallyRetired.clear()
                    synchronized(initializationLock) {
                        initializedDefaultDb = null
                        currentDbState = null
                    }
                }
                writerTrackerMutex.withLock {
                    lifecycleState = LifecycleState.CLOSED
                    deferredEvictions.clear()
                    drainWaiters.clear()
                    activeWriters.clear()
                    activeManagerOperations.clear()
                    writerGate?.completeExceptionally(IllegalStateException("DatabaseManager is closing or closed"))
                    writerGate = null
                }
                closedSuccessfully = true
            } finally {
                synchronized(managerJobLock) {
                    if (managerJobDrain === jobsDrain) managerJobDrain = null
                    if (closedSuccessfully) activeManagerJobs.clear()
                }
                writerTrackerMutex.withLock {
                    if (managerOperationDrain === operationsDrain) managerOperationDrain = null
                    if (shutdownWriterDrain === writerDrain) shutdownWriterDrain = null
                }
            }
        }
    }
}
