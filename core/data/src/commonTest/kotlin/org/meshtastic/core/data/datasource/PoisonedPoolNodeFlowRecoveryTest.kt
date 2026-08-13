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
package org.meshtastic.core.data.datasource

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.database.DatabaseProvider
import org.meshtastic.core.database.MeshtasticDatabase
import org.meshtastic.core.database.getInMemoryDatabaseBuilder
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * Regression for #6608. A Room permit leak surfaces as an endless acquire-timeout storm, and
 * `DatabaseProvider.observeCurrentDb` recovery is budgeted — once the budget is spent, the DAO flow terminates. Before
 * this hardening, a terminated upstream under `stateIn(..., SharingStarted.Eagerly, ...)` left the node list empty for
 * the rest of the process. These tests poison the pool for more failures than any recovery budget allows and assert the
 * node flows still reach a value.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PoisonedPoolNodeFlowRecoveryTest {

    private val database = getInMemoryDatabaseBuilder().build()

    @AfterTest fun tearDown() = database.close()

    @Test
    fun nodeDbFlowRecoversAfterMoreFailuresThanTheRecoveryBudgetAllows() = runTest {
        val provider = PoisonedPoolProvider(database, failuresBeforeRecovery = POISONED_FAILURES)
        val dataSource = SwitchingNodeInfoReadDataSource(provider)

        dataSource.nodeDBbyNumFlow().test(timeout = EMISSION_TIMEOUT) {
            assertEquals(emptyMap(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        assertTrue(provider.observedQueries > 1, "the wedged flow must be restarted, not left terminal")
    }

    /** Proves the fix at the layer that actually broke: an eagerly-shared StateFlow over the poisoned DAO flow. */
    @Test
    fun eagerlySharedNodeStateFlowStillPublishesAfterAPoisonedPool() = runTest {
        val provider = PoisonedPoolProvider(database, failuresBeforeRecovery = POISONED_FAILURES)
        val dataSource = SwitchingNodeInfoReadDataSource(provider)

        dataSource
            .nodeDBbyNumFlow()
            .map { map -> map.keys.toSet() }
            .stateIn(backgroundScope, SharingStarted.Eagerly, null)
            .test(timeout = EMISSION_TIMEOUT) {
                // The seed null is the StateFlow's initial value; the set proves the shared upstream survived.
                assertEquals(emptySet(), awaitItem() ?: awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
    }

    @Test
    fun myNodeInfoFlowRecoversAfterAPoisonedPool() = runTest {
        val provider = PoisonedPoolProvider(database, failuresBeforeRecovery = POISONED_FAILURES)
        val dataSource = SwitchingNodeInfoReadDataSource(provider)

        dataSource.myNodeInfoFlow().test(timeout = EMISSION_TIMEOUT) {
            assertEquals(null, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * Models a leaked-permit pool with an exhausted recovery budget: the first [failuresBeforeRecovery] collections
     * throw Room's acquire-timeout message and no in-place pool replacement happens, exactly as when
     * `DatabaseManager`'s Flow-recovery budget refuses another reopen.
     */
    private class PoisonedPoolProvider(database: MeshtasticDatabase, private val failuresBeforeRecovery: Int) :
        DatabaseProvider {
        private val mutableCurrentDb = MutableStateFlow(database)
        override val currentDb: StateFlow<MeshtasticDatabase> = mutableCurrentDb

        var observedQueries: Int = 0
            private set

        override fun <T> observeCurrentDb(query: (MeshtasticDatabase) -> Flow<T>): Flow<T> =
            currentDb.flatMapLatest { database ->
                flow {
                    observedQueries += 1
                    if (observedQueries <= failuresBeforeRecovery) {
                        throw IllegalStateException(
                            "Error code: 5, message: Timed out attempting to acquire a reader connection",
                        )
                    }
                    emitAll(query(database))
                }
            }

        override suspend fun <T> withReadDb(block: suspend (MeshtasticDatabase) -> T): T = block(currentDb.value)

        override suspend fun <T> withDb(block: suspend (MeshtasticDatabase) -> T): T? = block(currentDb.value)
    }

    private companion object {
        /** Far more failures than any per-collector or per-window recovery budget allows. */
        const val POISONED_FAILURES = 24

        // Turbine awaits run on the test scheduler's virtual clock, which the retry backoff also advances, so this
        // bound covers the accumulated capped backoff rather than any wall-clock expectation.
        val EMISSION_TIMEOUT = 30.minutes
    }
}
