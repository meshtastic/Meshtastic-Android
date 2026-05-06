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

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.meshtastic.core.repository.NodeMetadata
import org.meshtastic.core.testing.FakeDatabaseProvider
import org.meshtastic.core.testing.setupTestContext
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class AppMetadataRepositoryImplTest {

    private lateinit var dbProvider: FakeDatabaseProvider
    private lateinit var repository: AppMetadataRepositoryImpl

    @BeforeTest
    fun setUp() {
        setupTestContext()
        dbProvider = FakeDatabaseProvider()
        repository = AppMetadataRepositoryImpl(dbProvider)
    }

    @AfterTest
    fun tearDown() {
        dbProvider.close()
    }

    @Test
    fun `metadataByNum starts empty`() = runTest {
        assertTrue(repository.metadataByNum.first().isEmpty())
    }

    @Test
    fun `setFavorite creates missing metadata row`() = runTest {
        repository.setFavorite(nodeNum = 101, isFavorite = true)
        advanceUntilIdle()

        assertEquals(
            NodeMetadata(num = 101, isFavorite = true, notes = ""),
            repository.metadataByNum.first().getValue(101),
        )
    }

    @Test
    fun `setIgnored creates missing metadata row`() = runTest {
        repository.setIgnored(nodeNum = 102, isIgnored = true)
        advanceUntilIdle()

        assertEquals(
            NodeMetadata(num = 102, isIgnored = true, notes = ""),
            repository.metadataByNum.first().getValue(102),
        )
    }

    @Test
    fun `setMuted and setNotes preserve existing flags`() = runTest {
        repository.setFavorite(nodeNum = 103, isFavorite = true)
        repository.setMuted(nodeNum = 103, isMuted = true)
        repository.setNotes(nodeNum = 103, notes = "Portable node")
        advanceUntilIdle()

        assertEquals(
            NodeMetadata(num = 103, isFavorite = true, isMuted = true, notes = "Portable node"),
            repository.metadataByNum.first().getValue(103),
        )
    }

    @Test
    fun `setManuallyVerified updates verification flag`() = runTest {
        repository.setManuallyVerified(nodeNum = 104, verified = true)
        advanceUntilIdle()

        assertEquals(
            NodeMetadata(num = 104, manuallyVerified = true, notes = ""),
            repository.metadataByNum.first().getValue(104),
        )
    }

    @Test
    fun `repeated updates keep a single metadata entry per node`() = runTest {
        repository.setFavorite(nodeNum = 105, isFavorite = true)
        repository.setFavorite(nodeNum = 105, isFavorite = false)
        repository.setNotes(nodeNum = 105, notes = "Updated")
        advanceUntilIdle()

        val metadata = repository.metadataByNum.first()
        assertEquals(1, metadata.size)
        assertEquals(NodeMetadata(num = 105, notes = "Updated"), metadata.getValue(105))
    }

    @Test
    fun `delete removes existing metadata`() = runTest {
        repository.setFavorite(nodeNum = 106, isFavorite = true)
        advanceUntilIdle()

        repository.delete(106)
        advanceUntilIdle()

        assertTrue(repository.metadataByNum.first().isEmpty())
    }

    @Test
    fun `delete missing metadata is a no-op`() = runTest {
        repository.delete(999)
        advanceUntilIdle()

        assertTrue(repository.metadataByNum.first().isEmpty())
    }

    @Test
    fun `metadataByNum flow reflects create update and delete changes`() = runTest {
        val created = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) {
            repository.metadataByNum.drop(1).first { it[107]?.isFavorite == true }
        }

        repository.setFavorite(nodeNum = 107, isFavorite = true)
        advanceUntilIdle()
        assertEquals(NodeMetadata(num = 107, isFavorite = true, notes = ""), created.await().getValue(107))

        val updated = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) {
            repository.metadataByNum.drop(1).first { it[107]?.notes == "Flow note" }
        }

        repository.setNotes(nodeNum = 107, notes = "Flow note")
        advanceUntilIdle()
        assertEquals("Flow note", updated.await().getValue(107).notes)

        val deleted = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) {
            repository.metadataByNum.drop(1).first { it.isEmpty() }
        }

        repository.delete(107)
        advanceUntilIdle()
        assertTrue(deleted.await().isEmpty())
    }

    @Test
    fun `concurrent updates on same missing node produce one merged row`() = runTest {
        coroutineScope {
            launch { repository.setFavorite(nodeNum = 108, isFavorite = true) }
            launch { repository.setIgnored(nodeNum = 108, isIgnored = true) }
            launch { repository.setMuted(nodeNum = 108, isMuted = true) }
            launch { repository.setNotes(nodeNum = 108, notes = "Concurrent") }
            launch { repository.setManuallyVerified(nodeNum = 108, verified = true) }
        }
        advanceUntilIdle()

        val metadata = repository.metadataByNum.first()
        assertEquals(1, metadata.size)
        assertEquals(
            NodeMetadata(
                num = 108,
                isFavorite = true,
                isIgnored = true,
                isMuted = true,
                notes = "Concurrent",
                manuallyVerified = true,
            ),
            metadata.getValue(108),
        )
    }

    @Test
    fun `updates for multiple nodes stay isolated`() = runTest {
        repository.setFavorite(nodeNum = 201, isFavorite = true)
        repository.setIgnored(nodeNum = 202, isIgnored = true)
        repository.setNotes(nodeNum = 203, notes = "Third")
        advanceUntilIdle()

        val metadata = repository.metadataByNum.first()
        assertEquals(3, metadata.size)
        assertEquals(NodeMetadata(num = 201, isFavorite = true, notes = ""), metadata.getValue(201))
        assertEquals(NodeMetadata(num = 202, isIgnored = true, notes = ""), metadata.getValue(202))
        assertEquals(NodeMetadata(num = 203, notes = "Third"), metadata.getValue(203))
    }
}
