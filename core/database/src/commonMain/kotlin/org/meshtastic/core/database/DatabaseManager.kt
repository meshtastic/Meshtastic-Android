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

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import co.touchlab.kermit.Logger
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.normalizeAddress
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.database.di.DatabaseDataStore
import org.meshtastic.core.di.CoroutineDispatchers
import kotlin.concurrent.Volatile
import org.meshtastic.core.common.database.DatabaseManager as SharedDatabaseManager

internal const val MAX_CONSECUTIVE_FLOW_POOL_RECOVERIES = 3

/**
 * Sliding rate window shared by every automatic pool replacement, whichever failure triggered it.
 *
 * Rate-limiting rather than capping per manager lifetime is the point: the Room 3.0.1 permit leak behind #6608 recurs
 * "multiple times within minutes" in the field, so any lifetime budget is spent early and every later wedge becomes
 * permanent. A window still bounds how fast detached pools accumulate, because each recovery retains one Room instance
 * until orderly shutdown, but it always reopens.
 */
internal const val POOL_RECOVERY_WINDOW_MS = 10 * 60 * 1_000L

/** Bounds Flow-triggered replacements per [POOL_RECOVERY_WINDOW_MS]. */
internal const val MAX_FLOW_POOL_RECOVERIES_PER_WINDOW = MAX_CONSECUTIVE_FLOW_POOL_RECOVERIES * 2

/**
 * Deadline for the first emission of a re-latched DAO Flow. Comfortably above any legitimate cold-open, migration, or
 * backfill-contended first query (Room's own acquire timeout is 30s) so a healthy database never trips it.
 */
internal const val FLOW_FIRST_EMISSION_TIMEOUT_MS = 45_000L

/**
 * Bounds wedge-triggered replacements (a `withDb` block that never returned) per [POOL_RECOVERY_WINDOW_MS]. Lower than
 * the Flow budget because each wedge recovery also strands the abandoned block's pool, and a wedge costs a caller a
 * full [DatabaseManager.withDbTimeoutMillis] to detect.
 */
internal const val MAX_WEDGE_POOL_RECOVERIES_PER_WINDOW = 3

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
open class DatabaseManager(private val datastore: DatabaseDataStore, private val dispatchers: CoroutineDispatchers) :
    DatabaseProvider,
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

    private enum class ReopenOrigin {
        BOUNDED_OPERATION,
        WEDGED_OPERATION,
        FLOW_OBSERVER,
    }

    @Volatile private var lifecycleState = LifecycleState.OPEN

    // Per-database bounded-access tracking and per-source write barrier for merges. `withDb` deliberately does NOT
    // take [mutex] (hot path), so a merge under [mutex] must still drain any in-flight writer that captured the source
    // DB before folding it away — otherwise a late-committing write is lost when the source is retired. This
    // dedicated lock (never held across a drain await, so it can't deadlock the merge) tracks live bounded reads and
    // `withDb` blocks per captured DB instance, plus the writer-admission gate. The gate is armed at the start of an
    // association attempt: while it is pending, [beginWrite] blocks new writers instead of letting them capture a
    // DB, so a new `withDb` can never write to `source` once it is being retired, nor land on `dest` before the merge
    // commits. The gate completes with `source` if the attempt aborts
    // (drain timeout, cancellation, or pre-commit merge failure) and with `dest` once the merge commits — source is
    // never restored after the merge commits. The lock is released before any suspend (drain await, gate await, Room
    // work, merge work, or DataStore work), so it can't deadlock any of them.
    private val writerTrackerMutex = Mutex()
    private val activeWriters = mutableMapOf<MeshtasticDatabase, Int>()
    private val activeReaders = mutableMapOf<MeshtasticDatabase, Int>()
    private val drainWaiters = mutableMapOf<MeshtasticDatabase, MutableList<CompletableDeferred<Unit>>>()

    /**
     * Per-pool containment lanes for one-shot [withDb] blocks, keyed by Room instance and guarded by
     * [writerTrackerMutex]. [beginWrite] hands the lane out in the same critical section that admits the writer, so a
     * block always runs on the lane of the pool it was admitted against.
     *
     * Each lane is a single-parallelism view, which narrows the Room/SQLite churn window without serializing blocks
     * across suspension — a suspended callback releases the lane, exactly as the previous process-wide lane behaved.
     * Keying by pool is what keeps a callback that blocks its lane thread from stalling every later write: a
     * replacement pool published by recovery has its own lane. Entries are dropped once a pool can no longer be
     * admitted (reopen detaches it, cached close removes it, [close] clears the map).
     */
    private val poolLanes = mutableMapOf<MeshtasticDatabase, CoroutineDispatcher>()

    /**
     * Pools proven wedged that recovery has refused to replace, mapped to when they were quarantined. Guarded by
     * [writerTrackerMutex].
     *
     * While the replacement budget is spent, a wedged pool stays published, so without this every later write would be
     * admitted against it, wait the full deadline, and abandon another block that can never finish — an unbounded pile
     * of parked coroutines and writer registrations under steady ingest. Admission fails fast instead.
     *
     * The quarantine is not terminal: it expires after [POOL_RECOVERY_WINDOW_MS], which is exactly when the wedge
     * budget has room again. That expiry is what makes windowing the budget observable at all — recovery is refused
     * until a write is admitted, and writes are refused until recovery happens, so without expiry the two block each
     * other and the pool stays write-dead for the process.
     *
     * Expiry re-admits against the *still-wedged* pool: recovery was refused, so nothing replaced it. The next write
     * therefore pays one [withDbTimeoutMillis] deadline, and its abandonment is what drives the replacement. That
     * sacrificial write is the guaranteed floor; when DAO Flows are collecting, a stall recovery usually replaces the
     * pool sooner and clears the quarantine through the reopen path.
     *
     * Entries are also pruned wherever [poolLanes] entries are: a pool that is replaced or closed is not admissible.
     */
    private val unrecoverablePools = mutableMapOf<MeshtasticDatabase, Long>()

    /**
     * Builds the containment lane for one pool. Tests override this because `limitedParallelism` on a test dispatcher
     * would replace virtual-time scheduling with a real worker view.
     */
    protected open fun createPoolLane(): CoroutineDispatcher = dispatchers.io.limitedParallelism(1)

    private val deferredEvictions = mutableSetOf<MeshtasticDatabase>()
    private var shutdownWriterDrain: CompletableDeferred<Unit>? = null

    // Admitted manager-operation tokens. Serialized associateDevice/switch/recovery/eviction/cleanup work and
    // bounded [withReadDb] callbacks register here before touching a manager-owned pool. Guarded by
    // [writerTrackerMutex]. [close] arms [managerOperationDrain] and bound-waits for this set to empty BEFORE acquiring
    // [mutex] for its ownership snapshot, so no admitted operation can resume against a closed pool. New operations are
    // rejected at admission once lifecycleState != OPEN.
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

    /**
     * Guarded by [mutex]; timestamps of successful replacements inside the sliding window, one deque per trigger.
     *
     * Per-origin rather than shared so the two failure modes cannot starve each other: a Flow-recovery storm must not
     * consume the budget that lets a wedged write path reopen, and vice versa.
     */
    private val recoveryTimestamps = ReopenOrigin.entries.associateWith { ArrayDeque<Long>() }

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

    /**
     * Re-latches long-lived DAO flows on database switches and recovers the active Room pool after a reader/writer
     * acquisition timeout. A failed query is never replayed on the same pool; publishing the replacement causes
     * [flatMapLatest] to start a fresh DAO flow. Concurrent failing collectors converge on the same replacement.
     *
     * Each collector stops after [MAX_CONSECUTIVE_FLOW_POOL_RECOVERIES] replacements without a successful emission,
     * while the manager permits at most [MAX_FLOW_POOL_RECOVERIES_PER_WINDOW] successful Flow-triggered replacements
     * per sliding [POOL_RECOVERY_WINDOW_MS]. The rate limit bounds how fast wedge/recover/emission cycles can create
     * detached Room instances without ever becoming a permanent refusal; a collector that fails while the window is
     * full can retry after backoff (see `retryOnDbPoolFailure`) and recover once the window clears.
     *
     * A leaked pool permit does not surface as an exception at all: Room's pool logs the acquire timeout and retries
     * forever (see [requireFirstEmissionWithin]), so the DAO flow simply never emits. Every latch is therefore also
     * bounded by [FLOW_FIRST_EMISSION_TIMEOUT_MS], which converts that silent stall into the same recovery path.
     */
    override fun <T> observeCurrentDb(query: (MeshtasticDatabase) -> Flow<T>): Flow<T> = flow {
        val consecutivePoolRecoveries = atomic(0)
        emitAll(
            currentDb.flatMapLatest { database ->
                flow {
                    emitAll(
                        query(database).requireFirstEmissionWithin(FLOW_FIRST_EMISSION_TIMEOUT_MS) {
                            consecutivePoolRecoveries.value = 0
                        },
                    )
                }
                    .catch { failure ->
                        if (failure is CancellationException) throw failure
                        val exception = failure as? Exception ?: throw failure
                        if (!isDbPoolAcquireTimeoutException(exception) && exception !is DbFlowStalledException) {
                            throw failure
                        }
                        if (
                            consecutivePoolRecoveries.value >= MAX_CONSECUTIVE_FLOW_POOL_RECOVERIES &&
                            shouldRethrowFlowFailure(database)
                        ) {
                            throw exception
                        }
                        consecutivePoolRecoveries.incrementAndGet()

                        val reopened =
                            try {
                                // Once a timeout is classified, finish or reject the manager-level ownership transfer
                                // atomically even if this collector is cancelled. A cancellable reopen could otherwise
                                // build a replacement and abandon the cache/publication handoff halfway through.
                                withContext(NonCancellable) { reopenFlowDatabaseIfStillCurrent(database) }
                            } catch (@Suppress("TooGenericExceptionCaught") recoveryFailure: Exception) {
                                exception.addSuppressed(recoveryFailure)
                                Logger.w(recoveryFailure) { "Failed to recover active DB after a Flow pool timeout" }
                                throw exception
                            }
                        if (reopened != null) {
                            Logger.w { "Reopened active DB after a Room Flow connection-pool timeout" }
                        } else if (shouldRethrowFlowFailure(database)) {
                            throw exception
                        }
                    }
            },
        )
    }

    /**
     * Fails the flow with [DbFlowStalledException] if the first value does not arrive within [timeoutMs].
     *
     * A Room DAO Flow always queries once on collection, so a first emission that never arrives means the query never
     * ran. Room 3.0.1 leaks a pool permit under cancellation churn and its pool is configured to *log* acquire timeouts
     * and retry forever, so a wedged pool produces neither a value nor an exception — every DB read hangs silently
     * (#6608). Only the first emission per latch is bounded; later emissions are event-driven and legitimately sparse.
     *
     * [onEmission] runs in the producing coroutine as each value leaves the upstream, before it crosses the channel. A
     * downstream `onEach` could not be used for recovery bookkeeping: the upstream's own failure travels by cancelling
     * this producer and can overtake the value it emitted a moment earlier.
     */
    private fun <T> Flow<T>.requireFirstEmissionWithin(timeoutMs: Long, onEmission: () -> Unit): Flow<T> = channelFlow {
        val firstEmission = CompletableDeferred<Unit>()
        // A failing child fails this channelFlow with its own exception, so the stall reaches the recovery
        // `catch`
        // above as DbFlowStalledException rather than as a cancellation.
        val watchdog = launch {
            if (withTimeoutOrNull(timeoutMs) { firstEmission.await() } == null) {
                throw DbFlowStalledException(
                    "Database Flow produced no first emission within ${timeoutMs}ms; the Room connection pool " +
                        "is not serving queries",
                )
            }
        }
        collect { value ->
            onEmission()
            firstEmission.complete(Unit)
            send(value)
        }
        watchdog.cancel()
    }
        // channelFlow buffers 64 by default; DAO snapshots are large, so keep the upstream's rendezvous
        // backpressure.
        .buffer(Channel.RENDEZVOUS)

    /** Checks flow ownership without lazily rebuilding [currentDb] while shutdown is clearing its publication. */
    private fun shouldRethrowFlowFailure(database: MeshtasticDatabase): Boolean {
        val isStillCurrent = synchronized(initializationLock) { currentDbState?.value === database }
        return isStillCurrent || lifecycleState != LifecycleState.OPEN
    }

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
     * Admits one operation that may touch manager-owned database pools and drains it on completion.
     *
     * Registration happens before the operation captures a pool or waits on [mutex] (after the lifecycle check); the
     * token is removed in `finally` so cancellation, failure, and timeout all release admission. A [close] that
     * observes a non-empty admission set arms [managerOperationDrain] and bound-waits on it before taking its ownership
     * snapshot, so an admitted callback cannot resume against a closed pool.
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
        // A closed pool can never be admitted again, so its lane and wedge verdict are dead weight.
        writerTrackerMutex.withLock {
            poolLanes.remove(database)
            unrecoverablePools.remove(database)
        }
        Logger.d { "Closed inactive database ${anonymizeDbName(dbName)} to free connections" }
    }

    /** Room-close seam used by deterministic shutdown tests. */
    protected open fun closeDatabase(database: MeshtasticDatabase) = database.close()

    /** Clock for the Flow-recovery rate window; tests override it to drive window expiry deterministically. */
    protected open fun recoveryNowMillis(): Long = nowMillis

    /**
     * Reopens the active database under [mutex], but only if it hasn't switched since the caller snapshotted it.
     *
     * The replaced Room instance is intentionally left open for the rest of the process. [currentDb] reads [_currentDb]
     * directly, so every publication is visible to app-wide collectors on the same program step — but there is no
     * deterministic handoff point where every collector has stopped using the previous instance.
     *
     * Returns the reopened DB, or null if another coroutine switched databases or shutdown has started.
     */
    private suspend fun reopenFlowDatabaseIfStillCurrent(expectedDb: MeshtasticDatabase): MeshtasticDatabase? {
        val expectedDbName =
            mutex.withLock {
                if (lifecycleState != LifecycleState.OPEN || _currentDb.value !== expectedDb) return null
                currentDbName
            }
        return reopenActiveDatabaseIfStillCurrent(expectedDb, expectedDbName, ReopenOrigin.FLOW_OBSERVER)
    }

    /** Per-window budget for [origin], or null when [origin] needs no budget of its own. */
    private fun recoveryLimitFor(origin: ReopenOrigin): Int? = when (origin) {
        ReopenOrigin.FLOW_OBSERVER -> MAX_FLOW_POOL_RECOVERIES_PER_WINDOW

        ReopenOrigin.WEDGED_OPERATION -> MAX_WEDGE_POOL_RECOVERIES_PER_WINDOW

        // Failure-triggered recovery is already bounded by the failing call itself.
        ReopenOrigin.BOUNDED_OPERATION -> null
    }

    /** Drops [origin]'s recoveries that have aged out of the window and returns what remains. Caller holds [mutex]. */
    private fun recoveriesInWindow(origin: ReopenOrigin): ArrayDeque<Long> {
        val timestamps = recoveryTimestamps.getValue(origin)
        val now = recoveryNowMillis()
        while (timestamps.isNotEmpty() && now - timestamps.first() >= POOL_RECOVERY_WINDOW_MS) {
            timestamps.removeFirst()
        }
        return timestamps
    }

    /**
     * Reports whether automatic replacement is still allowed for [origin] right now. Each recovery retains one detached
     * Room instance until orderly shutdown, so an intermittent wedge/recover cycle must not run unbounded — but the
     * bound is a rate, not a lifetime total, so a wedge that recurs for hours stays recoverable (#6608). Caller must
     * hold [mutex].
     */
    private fun hasReachedRecoveryLimit(origin: ReopenOrigin): Boolean {
        val limit = recoveryLimitFor(origin) ?: return false
        val count = recoveriesInWindow(origin).size
        return (count >= limit).also { reached ->
            if (reached) {
                Logger.w {
                    "DB recovery rate limit reached for $origin ($count in ${POOL_RECOVERY_WINDOW_MS}ms); deferring " +
                        "another automatic replacement until the window clears"
                }
            }
        }
    }

    /** Counts one successful automatic replacement against [origin]'s window budget. Caller must hold [mutex]. */
    private fun recordRecovery(origin: ReopenOrigin) {
        if (recoveryLimitFor(origin) == null) return
        recoveriesInWindow(origin).addLast(recoveryNowMillis())
    }

    @Suppress("ReturnCount")
    private suspend fun reopenActiveDatabaseIfStillCurrent(
        expectedDb: MeshtasticDatabase,
        expectedDbName: String,
        origin: ReopenOrigin,
    ): MeshtasticDatabase? = withManagerOperation {
        mutex.withLock {
            if (lifecycleState != LifecycleState.OPEN) return@withManagerOperation null
            if (_currentDb.value !== expectedDb || currentDbName != expectedDbName) return@withManagerOperation null
            if (hasReachedRecoveryLimit(origin)) return@withManagerOperation null

            val registered =
                dbCache[expectedDbName]
                    ?: if (expectedDbName == DatabaseConstants.DEFAULT_DB_NAME) {
                        synchronized(initializationLock) { initializedDefaultDb }
                    } else {
                        null
                    }
            if (registered !== expectedDb) {
                Logger.w { "DB recovery: active registration changed before reopen; skipping active DB reopen" }
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
            recordRecovery(origin)
            if (detachedDatabases.none { it.database === expectedDb }) {
                detachedDatabases.add(NamedDatabase(expectedDbName, expectedDb))
            }
            // The replaced instance can no longer be admitted (beginWrite only ever hands out the published pool), so
            // drop its lane and any wedge verdict. An abandoned block keeps running on the lane it already captured.
            writerTrackerMutex.withLock {
                poolLanes.remove(expectedDb)
                unrecoverablePools.remove(expectedDb)
            }

            // Intentionally do not close expectedDb here. The public currentDb Flow exposes _currentDb directly,
            // so downstream flatMapLatest collectors may still be using the replaced Room instance after this
            // function emits the reopened DB. Closing the old pool here can surface "Connection pool is closed"
            // to app-wide DB observers that do not have closed-pool recovery. This mirrors switchActiveDatabase's
            // no-sync-close discipline. [close] reclaims every retained detached pool during orderly shutdown.
            //
            // Retention is deliberately unbounded before then: a detached pool has no provable last user. Bounded
            // reads/writes are tracked, but DAO Flow collectors are not, and Paging factories latch a raw
            // `currentDb.value` (see PacketRepositoryImpl), so no counter here can show a pool is unreachable.
            // [POOL_RECOVERY_WINDOW_MS] bounds how fast replacements can be created instead.

            reopened
        }
    }

    /** Test-only visibility for detached-pool retention. */
    internal suspend fun debugDetachedPoolCount(): Int = mutex.withLock { detachedDatabases.size }

    /**
     * Deadline for one [withDb] call. Tests override this: a non-positive value waits without a bound, which is the
     * only way a fixture can park a writer across a virtual-time idle period without the scheduler auto-advancing into
     * this deadline.
     */
    protected open val withDbTimeoutMillis: Long
        get() = WITH_DB_TIMEOUT_MS

    /**
     * Executes [block] once against the admitted current DB instance, bounding the caller's wait at
     * [withDbTimeoutMillis].
     *
     * A callback is never replayed after it starts: an arbitrary block can perform one side effect and then fail, so
     * transparently invoking it again against another pool could duplicate or split a logical write. Pool-timeout
     * recovery may reopen the active database for future calls, but the original failure is still propagated.
     *
     * Containment is per pool, not process-wide: the callback runs on the pool's containment lane ([poolLanes]) in a
     * manager-scoped child coroutine that owns the writer registration and releases it in its own `finally`. Only the
     * *wait* is bounded — a started block still runs to completion under [NonCancellable], so a bounded DB-critical
     * section is never torn apart mid-write. A block that never returns (Room 3.x logs and retries connection-pool
     * acquisition instead of throwing, so a leaked permit hangs forever) is abandoned rather than cancelled: the caller
     * fails with [DatabaseOperationTimeoutException] and the active pool is reopened, so later calls are admitted
     * against the replacement pool and its fresh lane instead of queueing behind the wedge.
     *
     * Long-lived Flow/Paging reads must stay out of `withDb`; see [observeCurrentDb] and [withReadDb].
     */
    @Suppress("ReturnCount")
    override suspend fun <T> withDb(block: suspend (MeshtasticDatabase) -> T): T? {
        val queuedAt = nowMillis
        currentCoroutineContext().ensureActive()
        val admission = beginWrite()
        val blockStarted = CompletableDeferred<Unit>()
        val execution = launchDbBlock(admission, blockStarted, block)
        val timeoutMillis = withDbTimeoutMillis
        val completed =
            if (timeoutMillis <= 0) execution.join() else withTimeoutOrNull(timeoutMillis) { execution.join() }
        if (completed == null) abandonWedgedDbBlock(admission, blockStarted, execution, timeoutMillis)

        // Re-check cancellation so a stale caller does not continue after the DB releases.
        currentCoroutineContext().ensureActive()
        val elapsedMillis = nowMillis - queuedAt
        if (elapsedMillis >= WITH_DB_SLOW_OPERATION_MS) {
            Logger.w {
                "withDb took ${elapsedMillis}ms including lane wait; persistent slow logs indicate the DB access " +
                    "path should be revisited"
            }
        }
        val failure = execution.getCompletionExceptionOrNull() ?: return execution.getCompleted()
        handleDbBlockFailure(admission, failure)
    }

    /**
     * Starts [block] on the admitted pool's lane.
     *
     * The coroutine owns the writer registration and releases it in its own `finally`, so an abandoned block can never
     * double-release it and always deregisters if it eventually returns. It starts [CoroutineStart.ATOMIC] so that
     * `finally` also runs when the coroutine is cancelled before its first dispatch, and it completes [blockStarted]
     * only once it is about to invoke the callback — a call still waiting for its lane has performed no side effect and
     * is aborted at the cancellation check instead of running late.
     */
    private fun <T> launchDbBlock(
        admission: AdmittedDatabase,
        blockStarted: CompletableDeferred<Unit>,
        block: suspend (MeshtasticDatabase) -> T,
    ): Deferred<T> = managerScope.async(admission.lane, start = CoroutineStart.ATOMIC) {
        val db = admission.database
        try {
            currentCoroutineContext().ensureActive()
            blockStarted.complete(Unit)
            withContext(NonCancellable) { block(db) }
        } finally {
            withContext(NonCancellable) { endWrite(db) }
        }
    }

    /**
     * Stops admitting work against a pool that stayed published after recovery declined to replace it.
     *
     * Recovery declines while the windowed replacement budget is spent, and it can also fail outright. Either way the
     * wedged instance remains [_currentDb], so every later admission would park another block on it forever. Marking it
     * turns those calls into an immediate failure until the quarantine expires (see [unrecoverablePools]). Recovery
     * during shutdown is not a wedge verdict, and neither is a pool that has already been replaced, so both are left
     * unmarked.
     */
    private suspend fun quarantineWedgedPoolIfStillPublished(database: MeshtasticDatabase) {
        if (lifecycleState != LifecycleState.OPEN) return
        val quarantined =
            writerTrackerMutex.withLock {
                if (lifecycleState != LifecycleState.OPEN || _currentDb.value !== database) {
                    false
                } else {
                    unrecoverablePools.put(database, recoveryNowMillis()) == null
                }
            }
        if (quarantined) {
            Logger.w {
                "Marked the active DB pool unrecoverable after wedge recovery was refused; database operations fail " +
                    "fast for the next ${POOL_RECOVERY_WINDOW_MS}ms, then one is admitted to drive a replacement"
            }
        }
    }

    /**
     * Resolves a [withDb] call whose bounded wait expired: cancels a block that never started, or abandons a started
     * one and recovers the pool.
     *
     * A started block is not cancellable by design, so it keeps its lane and writer registration until it returns — a
     * merge drain or shutdown drain that waits on it still resolves on its own bound and logs. Reopening the active
     * pool is what unwedges the app: the wedged instance moves to the detached set (protected from eviction, reclaimed
     * by [close]) and later callers admit against the replacement pool and its fresh lane.
     */
    @Suppress("TooGenericExceptionCaught", "ThrowsCount")
    private suspend fun abandonWedgedDbBlock(
        admission: AdmittedDatabase,
        blockStarted: CompletableDeferred<Unit>,
        execution: Deferred<*>,
        timeoutMillis: Long,
    ): Nothing {
        if (!blockStarted.isCompleted) {
            execution.cancel()
            // Re-check: the block may have acquired the lane between the timeout and the cancellation, in which case it
            // is inside NonCancellable and must be abandoned instead of reported as never started.
            if (!blockStarted.isCompleted) {
                Logger.w { "withDb waited ${timeoutMillis}ms for the DB lane; its callback never started" }
                throw DatabaseOperationTimeoutException(
                    "withDb did not start within ${timeoutMillis}ms; an earlier block is wedged on this database",
                )
            }
        }
        val reopened =
            try {
                reopenActiveDatabaseIfStillCurrent(admission.database, admission.name, ReopenOrigin.WEDGED_OPERATION)
            } catch (recoveryCancel: CancellationException) {
                throw recoveryCancel
            } catch (recoveryFailure: Exception) {
                Logger.w(recoveryFailure) { "withDb: failed to reopen active DB after abandoning a wedged callback" }
                null
            }
        if (reopened == null) quarantineWedgedPoolIfStillPublished(admission.database)
        Logger.w {
            if (reopened != null) {
                "withDb callback exceeded ${timeoutMillis}ms; abandoned it and reopened the active DB so later " +
                    "calls run on a fresh connection pool"
            } else {
                "withDb callback exceeded ${timeoutMillis}ms; abandoned it but the active DB was not reopened"
            }
        }
        throw DatabaseOperationTimeoutException(
            "withDb callback did not finish within ${timeoutMillis}ms and was abandoned without being replayed",
        )
    }

    /**
     * Executes one bounded read without writer admission or the serialized write-containment lane. Active database
     * publication is synchronous and the captured pool is registered against eviction until the callback completes. The
     * read is also admitted into the shutdown drain, is never replayed automatically, and new reads are rejected once
     * shutdown begins.
     */
    override suspend fun <T> withReadDb(block: suspend (MeshtasticDatabase) -> T): T = withManagerOperation {
        val database = beginRead()
        try {
            withContext(dispatchers.io) {
                currentCoroutineContext().ensureActive()
                val result = block(database)
                currentCoroutineContext().ensureActive()
                result
            }
        } finally {
            withContext(NonCancellable) { endRead(database) }
        }
    }

    /** Captures and registers the currently published pool so eviction cannot close it while the callback is active. */
    private suspend fun beginRead(): MeshtasticDatabase = writerTrackerMutex.withLock {
        checkOpen()
        _currentDb.value.also { database -> activeReaders[database] = (activeReaders[database] ?: 0) + 1 }
    }

    /** Releases a bounded reader and retries an eviction that was deferred while the captured pool was in use. */
    private suspend fun endRead(database: MeshtasticDatabase) {
        val retryEviction =
            writerTrackerMutex.withLock {
                val remaining = (activeReaders[database] ?: 1) - 1
                if (remaining <= 0) {
                    activeReaders.remove(database)
                } else {
                    activeReaders[database] = remaining
                }
                !hasActiveDatabaseAccessLocked(database) && deferredEvictions.remove(database)
            }
        if (retryEviction && lifecycleState == LifecycleState.OPEN) {
            launchManagerWork(dispatchers.io) { enforceCacheLimit() }
        }
    }

    /** Caller must hold [writerTrackerMutex]. */
    private fun hasActiveDatabaseAccessLocked(database: MeshtasticDatabase): Boolean =
        (activeWriters[database] ?: 0) > 0 || (activeReaders[database] ?: 0) > 0

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
    private data class AdmittedDatabase(
        val database: MeshtasticDatabase,
        val name: String,
        /** Containment lane of [database]; see [poolLanes]. */
        val lane: CoroutineDispatcher,
    )

    /**
     * Reports whether [database] is still inside its quarantine, lifting the entry once the window has passed.
     *
     * The expiry is evaluated here rather than against the recovery budget itself because this runs under
     * [writerTrackerMutex] while the budget deques are guarded by [mutex], and the established lock order is [mutex]
     * then [writerTrackerMutex]. Reading the budget from here would invert it. Both are keyed to
     * [POOL_RECOVERY_WINDOW_MS], so the quarantine lifts exactly when a replacement becomes permissible again.
     *
     * Caller must hold [writerTrackerMutex].
     */
    private fun isQuarantinedLocked(database: MeshtasticDatabase): Boolean {
        val quarantinedAt = unrecoverablePools[database] ?: return false
        val expired = recoveryNowMillis() - quarantinedAt >= POOL_RECOVERY_WINDOW_MS
        if (expired) {
            unrecoverablePools.remove(database)
            Logger.i {
                "Lifted the wedged-pool quarantine after ${POOL_RECOVERY_WINDOW_MS}ms; admitting one operation to " +
                    "drive a replacement"
            }
        }
        return !expired
    }

    private suspend fun beginWrite(): AdmittedDatabase {
        while (true) {
            var admitted: AdmittedDatabase? = null
            val gate =
                writerTrackerMutex.withLock {
                    checkOpen()
                    val pendingGate = writerGate
                    if (pendingGate == null) {
                        val db = _currentDb.value
                        if (isQuarantinedLocked(db)) {
                            throw DatabaseOperationTimeoutException(
                                "database pool is wedged and its replacement budget is spent; refusing to start " +
                                    "another operation that cannot finish",
                            )
                        }
                        activeWriters[db] = (activeWriters[db] ?: 0) + 1
                        admitted =
                            AdmittedDatabase(
                                database = db,
                                name = currentDbName,
                                lane = poolLanes.getOrPut(db) { createPoolLane() },
                            )
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
                !hasActiveDatabaseAccessLocked(db) && deferredEvictions.remove(db)
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

    internal suspend fun debugReaderCount(database: MeshtasticDatabase? = null): Int = writerTrackerMutex.withLock {
        if (database == null) activeReaders.values.sum() else activeReaders[database] ?: 0
    }

    internal suspend fun debugEnforceCacheLimit() = enforceCacheLimit()

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

    /**
     * Maps a failed [withDb] execution onto the existing recovery policy and rethrows.
     *
     * The failed callback is never replayed; recovery only reopens the pool so *future* calls succeed.
     */
    @Suppress("ReturnCount", "ThrowsCount", "TooGenericExceptionCaught", "CyclomaticComplexMethod")
    private suspend fun handleDbBlockFailure(admission: AdmittedDatabase, failure: Throwable): Nothing {
        val db = admission.database
        val active = admission.name
        if (failure is CancellationException) {
            // The execution coroutine was cancelled from outside this call — only manager shutdown does that. The
            // caller itself is still active (its own cancellation surfaces from join), so do not impersonate it.
            throw IllegalStateException("withDb work was cancelled before completing", failure)
        }
        val e = failure as? Exception ?: throw failure
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
                    reopenActiveDatabaseIfStillCurrent(db, active, ReopenOrigin.BOUNDED_OPERATION)
                } catch (recoveryCancel: CancellationException) {
                    throw recoveryCancel
                } catch (recoveryFailure: Exception) {
                    e.addSuppressed(recoveryFailure)
                    Logger.w(recoveryFailure) { "withDb: failed to reopen active DB after a connection-pool timeout" }
                    null
                }
            Logger.w {
                if (reopened != null) {
                    "withDb: reopened active DB after transient Room connection-pool timeout; " +
                        "failed callback was not replayed"
                } else {
                    "withDb: active DB was not reopened during timeout recovery; failed callback was not replayed"
                }
            }
            throw e
        }

        throw e
    }

    /**
     * Runs one bounded DB-critical block under the caller's own writer admission, preserved through cancellation.
     *
     * Unlike [withDb] this has no lane and no wait bound: the only caller is manager-owned search-index backfill, which
     * runs in a cancellable manager job and can legitimately take much longer than [WITH_DB_TIMEOUT_MS].
     */
    private suspend fun <T> runCancellableDbBlock(db: MeshtasticDatabase, block: suspend (MeshtasticDatabase) -> T): T {
        currentCoroutineContext().ensureActive()
        val result = withContext(NonCancellable) { block(db) }
        currentCoroutineContext().ensureActive()
        return result
    }

    internal companion object {
        private const val BACKFILL_COLD_START_DELAY_MS = 2_000L
        private const val WITH_DB_SLOW_OPERATION_MS = 1_000L

        /**
         * Upper bound on the lane wait plus the callback itself. Writer admission is bounded separately by
         * [WRITER_GATE_TIMEOUT_MS] and runs before this deadline starts, so a call blocked behind an association can
         * take the sum of the two. Generous enough that no legitimate one-shot write reaches it, and short enough that
         * a pool wedge surfaces as a failure with recovery instead of a hang.
         */
        internal const val WITH_DB_TIMEOUT_MS = 30_000L

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

        fun isDbClosedException(e: Exception): Boolean = isDbPoolAcquireTimeoutException(e) ||
            generateSequence<Throwable>(e) { it.cause }
                .any { throwable ->
                    val msg = throwable.message?.lowercase() ?: return@any false
                    val hasDbContext = DB_TERMS.any { it in msg }
                    ("closed" in msg && hasDbContext) || "database is locked" in msg || "sqlite_busy" in msg
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

    protected open fun listExistingDbNames(): List<String> {
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
                        if (cached != null && hasActiveDatabaseAccessLocked(cached)) {
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
     * manager-operation draining, and admitted-writer draining, waits for the last serialized switch/association to
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

                // Bound-wait for already-admitted manager operations before acquiring [mutex]. New work is rejected
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
                    activeReaders.clear()
                    poolLanes.clear()
                    unrecoverablePools.clear()
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

/**
 * A bounded one-shot database operation did not finish within its deadline and was abandoned, never replayed.
 *
 * Room's connection pool retries a failed acquisition indefinitely instead of throwing, so a leaked permit surfaces as
 * a callback that never returns. This is the deadline that turns that silent hang into a failure the caller can react
 * to; the active pool is reopened so subsequent operations proceed.
 */
class DatabaseOperationTimeoutException(message: String) : IllegalStateException(message)
