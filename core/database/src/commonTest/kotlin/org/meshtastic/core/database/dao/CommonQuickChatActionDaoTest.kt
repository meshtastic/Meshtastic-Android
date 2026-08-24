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
package org.meshtastic.core.database.dao

import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.database.MeshtasticDatabase
import org.meshtastic.core.database.entity.QuickChatAction
import org.meshtastic.core.database.getInMemoryDatabaseBuilder
import org.meshtastic.core.testing.setupTestContext
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

abstract class CommonQuickChatActionDaoTest {
    private lateinit var database: MeshtasticDatabase
    private lateinit var dao: QuickChatActionDao

    suspend fun createDb() {
        setupTestContext()
        database = getInMemoryDatabaseBuilder().build()
        dao = database.quickChatActionDao()
    }

    @AfterTest
    fun closeDb() {
        database.close()
    }

    @Test
    fun testInsertActionAndRetrieveIt() = runTest {
        createDb()
        val action = testAction(uuid = 1L, name = "Greeting", message = "Hello", position = 0)

        dao.upsert(action)

        assertEquals(listOf(action), dao.getAll().first())
    }

    @Test
    fun testUpdateAction() = runTest {
        createDb()
        val action = testAction(uuid = 1L, name = "Greeting", message = "Hello", position = 0)
        val updatedAction =
            action.copy(name = "Updated Greeting", message = "Updated Hello", mode = QuickChatAction.Mode.Append)

        dao.upsert(action)
        dao.upsert(updatedAction)

        assertEquals(listOf(updatedAction), dao.getAll().first())
    }

    @Test
    fun testDeleteAction() = runTest {
        createDb()
        val first = testAction(uuid = 1L, name = "First", position = 0)
        val second = testAction(uuid = 2L, name = "Second", position = 1)
        val third = testAction(uuid = 3L, name = "Third", position = 2)

        dao.upsert(first)
        dao.upsert(second)
        dao.upsert(third)
        dao.delete(second)

        val remaining = dao.getAll().first()
        assertEquals(listOf(first, third.copy(position = 1)), remaining)
    }

    @Test
    fun testDeleteAll() = runTest {
        createDb()
        dao.upsert(testAction(uuid = 1L, name = "First", position = 0))
        dao.upsert(testAction(uuid = 2L, name = "Second", position = 1))

        dao.deleteAll()

        assertTrue(dao.getAll().first().isEmpty())
    }

    @Test
    fun testReactiveFlowEmitsUpdatesOnInsertAndDelete() = runTest {
        createDb()
        val action = testAction(uuid = 1L, name = "Greeting", position = 0)

        assertTrue(dao.getAll().first().isEmpty())

        val inserted = async { dao.getAll().first { it == listOf(action) } }
        dao.upsert(action)
        assertEquals(listOf(action), inserted.await())

        val deleted = async { dao.getAll().first { it.isEmpty() } }
        dao.delete(action)
        assertTrue(deleted.await().isEmpty())
    }

    @Test
    fun testPositionOrdering() = runTest {
        createDb()
        val last = testAction(uuid = 1L, name = "Last", position = 2)
        val first = testAction(uuid = 2L, name = "First", position = 0)
        val middle = testAction(uuid = 3L, name = "Middle", position = 1)

        dao.upsert(last)
        dao.upsert(first)
        dao.upsert(middle)
        dao.updateActionPosition(last.uuid, position = 3)

        val actions = dao.getAll().first()
        assertEquals(listOf(first.uuid, middle.uuid, last.uuid), actions.map { it.uuid })
        assertEquals(listOf(0, 1, 3), actions.map { it.position })
    }

    private fun testAction(
        uuid: Long,
        name: String,
        message: String = "message-$uuid",
        mode: QuickChatAction.Mode = QuickChatAction.Mode.Instant,
        position: Int,
    ): QuickChatAction = QuickChatAction(uuid = uuid, name = name, message = message, mode = mode, position = position)
}
