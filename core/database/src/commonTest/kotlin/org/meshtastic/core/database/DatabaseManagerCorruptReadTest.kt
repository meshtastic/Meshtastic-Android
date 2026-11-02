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

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.common.util.Exceptions
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DatabaseManagerCorruptReadTest : DatabaseManagerTestFixture() {

    private val reportedTags = mutableListOf<String?>()
    private var previousReporter: ((Throwable, String?, String?) -> Unit)? = null

    @BeforeTest
    fun setUp() {
        setUpFixture()
        previousReporter = Exceptions.reporter
        Exceptions.reporter = { _, tag, _ -> reportedTags += tag }
    }

    @AfterTest
    fun tearDown() {
        Exceptions.reporter = previousReporter
        tearDownFixture()
    }

    @Test
    fun corruptPageCompletesTheFlowAndMarksTheDatabase() = runTest(testDispatcher) {
        manager.switchActiveDatabase("addrA")
        val buildsBefore = manager.builtDatabases.size

        // toList returning is the assertion: the flow completed rather than failing or waiting for a switch.
        manager.observeCurrentDb<Unit> { flow { throw corruptPage() } }.toList()
        manager.observeCurrentDb<Unit> { flow { throw corruptPage() } }.toList()

        assertEquals(listOf<String?>("database-corrupt"), reportedTags, "reported once per database, not per query")
        assertEquals(1, manager.corruptMarks.size, "the device database is marked for the next open")
        assertEquals(buildsBefore, manager.builtDatabases.size, "a corrupt page must not rebuild the live pool")
    }

    @Test
    fun oneShotReadsOfACorruptPageEndWithoutAValue() = runTest(testDispatcher) {
        manager.switchActiveDatabase("addrA")

        assertNull(manager.observeCurrentDb<Unit> { flow { throw corruptPage() } }.firstOrNull())
        assertFailsWith<NoSuchElementException> {
            manager.observeCurrentDb<Unit> { flow { throw corruptPage() } }.first()
        }
    }

    @Test
    fun aCorruptPageOnTheLazyDefaultPoolIsReportedButNotMarked() = runTest(testDispatcher) {
        manager.observeCurrentDb<Unit> { flow { throw corruptPage() } }.toList()

        assertEquals(listOf<String?>("database-corrupt"), reportedTags)
        assertTrue(manager.corruptMarks.isEmpty(), "the default database is never moved aside")
    }

    @Test
    fun fts5VirtualTableErrorStillPropagates() = runTest(testDispatcher) {
        manager.switchActiveDatabase("addrA")

        assertFailsWith<IllegalStateException> {
            manager
                .observeCurrentDb<Unit> {
                    flow {
                        throw IllegalStateException("Error code: 267, message: database disk image is malformed")
                    }
                }
                .first()
        }
        assertTrue(reportedTags.isEmpty())
    }

    private fun corruptPage() = IllegalStateException("Error code: 11, message: database disk image is malformed")
}
