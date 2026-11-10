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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.meshtastic.core.data.datasource.MqttRootsLocalDataSource
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.MqttRootsEntry
import org.meshtastic.core.model.MqttRootsResponse
import org.meshtastic.core.network.MqttRootsRemoteDataSource
import org.meshtastic.core.testing.FakeDatabaseProvider
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MqttRootsRepositoryImplTest {

    private val json = Json { ignoreUnknownKeys = true }

    // Real dispatchers + runBlocking, not runTest, as in MaintenanceUf2RepositoryImplTest: stays off the virtual clock
    // near Room.
    private val unconfined = Dispatchers.Unconfined
    private val dispatchers = CoroutineDispatchers(main = unconfined, io = unconfined, default = unconfined)

    private lateinit var dbProvider: FakeDatabaseProvider
    private lateinit var local: MqttRootsLocalDataSource
    private lateinit var api: FakeApiService
    private lateinit var seed: FakeBundledAssetReader
    private lateinit var repository: MqttRootsRepositoryImpl

    private val seeded =
        MqttRootsResponse(
            neighbors = mapOf("US-TX" to listOf("US-OK"), "US-OK" to listOf("US-TX")),
            subdivisionSegments = mapOf("US" to mapOf("texas" to "TX")),
        )

    private val fetched =
        seeded.copy(
            entries =
            listOf(MqttRootsEntry("msh/US/MeshSTL", "MeshSTL", center = listOf(38.581, -90.374), radiusKm = 50.0)),
        )

    @BeforeTest
    fun setup() {
        dbProvider = FakeDatabaseProvider()
        local = MqttRootsLocalDataSource(dbProvider, dispatchers)
        api = FakeApiService()
        seed = FakeBundledAssetReader()
        seed.put("mqtt_roots.json", seeded, json)
        repository =
            MqttRootsRepositoryImpl(
                remoteDataSource = MqttRootsRemoteDataSource(api, dispatchers),
                localDataSource = local,
                assetReader = seed,
                json = json,
            )
    }

    @AfterTest fun tearDown() = dbProvider.close()

    @Test
    fun getSnapshotSeedsFromBundledJsonWhenCacheIsEmpty() = runBlocking {
        assertEquals(seeded, repository.getSnapshot())
    }

    @Test
    fun getSnapshotIsEmptyWhenNoSeedAndNoCache() = runBlocking {
        seed.remove("mqtt_roots.json")

        assertEquals(MqttRootsResponse(), repository.getSnapshot())
    }

    @Test
    fun reconcileReplacesTheSeedWithTheFetchedDirectory() = runBlocking {
        api.mqttRoots = { fetched }

        repository.reconcile()

        assertEquals(fetched, repository.getSnapshot())
        assertEquals(1, api.mqttRootsCalls)
    }

    @Test
    fun aFailedFetchKeepsTheSeed() = runBlocking {
        repository.getSnapshot()
        api.mqttRoots = { error("HTTP 503") }

        repository.reconcile()

        assertEquals(seeded, repository.getSnapshot())
    }

    @Test
    fun aMalformedFetchKeepsTheSeed() = runBlocking {
        repository.getSnapshot()
        api.mqttRoots = { json.decodeFromString<MqttRootsResponse>("""{"entries": [{"name": "no root"}]}""") }

        repository.reconcile()

        assertEquals(seeded, repository.getSnapshot())
    }

    @Test
    fun anEmptyFetchLeavesTheCacheUntouched() = runBlocking {
        api.mqttRoots = { fetched }
        repository.reconcile()

        api.mqttRoots = { MqttRootsResponse() }
        repository.reconcile()

        assertEquals(fetched, repository.getSnapshot())
    }

    @Test
    fun aFetchWhoseSegmentMapsAreAllEmptyKeepsTheSeed() = runBlocking {
        repository.getSnapshot()
        api.mqttRoots = {
            json.decodeFromString<MqttRootsResponse>(
                """{"entries": [], "neighbors": {}, "subdivisionSegments": {"US": {}}}""",
            )
        }

        repository.reconcile()

        assertEquals(seeded, repository.getSnapshot())
    }

    @Test
    fun aFetchWithEntriesButNoSegmentsKeepsTheCache() = runBlocking {
        api.mqttRoots = { fetched }
        repository.reconcile()

        api.mqttRoots = { fetched.copy(subdivisionSegments = emptyMap()) }
        repository.reconcile()

        assertEquals(fetched, repository.getSnapshot())
    }

    @Test
    fun aFailedCacheWriteKeepsTheSeedWithoutThrowing() = runBlocking {
        repository.getSnapshot()
        api.mqttRoots = { fetched }
        dbProvider.beforeWithDb = { error("disk I/O error") }

        repository.reconcile()

        assertEquals(seeded, repository.getSnapshot())
        assertEquals(1, api.mqttRootsCalls)
    }
}
