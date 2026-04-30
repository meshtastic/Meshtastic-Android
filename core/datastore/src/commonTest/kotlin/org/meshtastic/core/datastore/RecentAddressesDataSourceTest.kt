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
package org.meshtastic.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okio.FileSystem
import okio.Path
import org.meshtastic.core.datastore.model.RecentAddress
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class RecentAddressesDataSourceTest {
    private lateinit var tmpDir: Path
    private lateinit var dataSource: RecentAddressesDataSource

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @BeforeTest
    fun setup() {
        tmpDir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "recentAddressesTest-${Uuid.random()}"
        FileSystem.SYSTEM.createDirectories(tmpDir)
        val dataStore =
            PreferenceDataStoreFactory.createWithPath(
                scope = testScope,
                produceFile = { tmpDir / "test.preferences_pb" },
            )
        dataSource = RecentAddressesDataSource(dataStore)
    }

    @AfterTest
    fun tearDown() {
        FileSystem.SYSTEM.deleteRecursively(tmpDir)
    }

    // ---- recentAddresses flow ----

    @Test
    fun `recentAddresses emits empty list when no data stored`() = testScope.runTest {
        val result = dataSource.recentAddresses.first()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `setRecentAddresses persists and emits the list`() = testScope.runTest {
        val addresses =
            listOf(
                RecentAddress(address = "192.168.1.1", name = "Home"),
                RecentAddress(address = "10.0.0.1", name = "Office"),
            )
        dataSource.setRecentAddresses(addresses)

        val result = dataSource.recentAddresses.first()
        assertEquals(addresses, result)
    }

    @Test
    fun `setRecentAddresses overwrites previous value`() = testScope.runTest {
        dataSource.setRecentAddresses(listOf(RecentAddress("1.2.3.4", "Old")))
        dataSource.setRecentAddresses(listOf(RecentAddress("5.6.7.8", "New")))

        val result = dataSource.recentAddresses.first()
        assertEquals(1, result.size)
        assertEquals("5.6.7.8", result[0].address)
    }

    // ---- add() LRU behaviour ----

    @Test
    fun `add to empty list stores single entry`() = testScope.runTest {
        dataSource.add(RecentAddress("192.168.0.1", "Router"))

        val result = dataSource.recentAddresses.first()
        assertEquals(1, result.size)
        assertEquals("192.168.0.1", result[0].address)
    }

    @Test
    fun `add prepends new address to front`() = testScope.runTest {
        dataSource.setRecentAddresses(listOf(RecentAddress("1.1.1.1", "Existing")))
        dataSource.add(RecentAddress("2.2.2.2", "New"))

        val result = dataSource.recentAddresses.first()
        assertEquals("2.2.2.2", result[0].address)
        assertEquals("1.1.1.1", result[1].address)
    }

    @Test
    fun `add deduplicates by address moving existing entry to front with updated name`() = testScope.runTest {
        dataSource.setRecentAddresses(listOf(RecentAddress("1.1.1.1", "First"), RecentAddress("2.2.2.2", "Second")))
        dataSource.add(RecentAddress("2.2.2.2", "Second-updated"))

        val result = dataSource.recentAddresses.first()
        assertEquals(2, result.size)
        assertEquals("2.2.2.2", result[0].address)
        assertEquals("Second-updated", result[0].name)
        assertEquals("1.1.1.1", result[1].address)
    }

    @Test
    fun `add enforces CACHE_CAPACITY of 3 evicting oldest entry`() = testScope.runTest {
        dataSource.setRecentAddresses(
            listOf(RecentAddress("1.1.1.1", "A"), RecentAddress("2.2.2.2", "B"), RecentAddress("3.3.3.3", "C")),
        )
        dataSource.add(RecentAddress("4.4.4.4", "D"))

        val result = dataSource.recentAddresses.first()
        assertEquals(3, result.size)
        assertEquals("4.4.4.4", result[0].address)
        assertEquals("1.1.1.1", result[1].address)
        assertEquals("2.2.2.2", result[2].address)
        assertFalse(result.any { it.address == "3.3.3.3" })
    }

    @Test
    fun `add re-adding the same address at front keeps capacity`() = testScope.runTest {
        dataSource.setRecentAddresses(
            listOf(RecentAddress("1.1.1.1", "A"), RecentAddress("2.2.2.2", "B"), RecentAddress("3.3.3.3", "C")),
        )
        dataSource.add(RecentAddress("1.1.1.1", "A"))

        val result = dataSource.recentAddresses.first()
        assertEquals(3, result.size)
        assertEquals("1.1.1.1", result[0].address)
    }

    // ---- remove() ----

    @Test
    fun `remove deletes the matching address`() = testScope.runTest {
        dataSource.setRecentAddresses(listOf(RecentAddress("1.1.1.1", "A"), RecentAddress("2.2.2.2", "B")))
        dataSource.remove("1.1.1.1")

        val result = dataSource.recentAddresses.first()
        assertEquals(1, result.size)
        assertEquals("2.2.2.2", result[0].address)
    }

    @Test
    fun `remove on unknown address is a no-op`() = testScope.runTest {
        dataSource.setRecentAddresses(listOf(RecentAddress("1.1.1.1", "A")))
        dataSource.remove("9.9.9.9")

        val result = dataSource.recentAddresses.first()
        assertEquals(1, result.size)
    }

    @Test
    fun `remove last address yields empty list`() = testScope.runTest {
        dataSource.setRecentAddresses(listOf(RecentAddress("1.1.1.1", "A")))
        dataSource.remove("1.1.1.1")

        assertTrue(dataSource.recentAddresses.first().isEmpty())
    }

    // ---- legacy JSON parsing (via LegacyParsingHarness) ----

    @Test
    fun `legacy JsonObject array is parsed correctly`() = testScope.runTest {
        val legacyJson =
            """[{"address":"192.168.1.100","name":"NodeA"},{"address":"192.168.1.101","name":"NodeB"}]"""
        val result = LegacyParsingHarness(legacyJson).recentAddresses.first()

        assertEquals(2, result.size)
        assertEquals("192.168.1.100", result[0].address)
        assertEquals("NodeA", result[0].name)
        assertEquals("192.168.1.101", result[1].address)
        assertEquals("NodeB", result[1].name)
    }

    @Test
    fun `legacy bare string JsonPrimitive array is parsed correctly`() = testScope.runTest {
        // Old clients stored plain IP strings with no name field
        val legacyJson = """["192.168.1.50","10.0.0.2"]"""
        val result = LegacyParsingHarness(legacyJson).recentAddresses.first()

        assertEquals(2, result.size)
        assertEquals("192.168.1.50", result[0].address)
        assertEquals("Meshtastic", result[0].name)
        assertEquals("10.0.0.2", result[1].address)
        assertEquals("Meshtastic", result[1].name)
    }

    @Test
    fun `legacy JsonObject missing address field is skipped`() = testScope.runTest {
        val legacyJson = """[{"name":"NoAddress"},{"address":"1.2.3.4","name":"Good"}]"""
        val result = LegacyParsingHarness(legacyJson).recentAddresses.first()

        assertEquals(1, result.size)
        assertEquals("1.2.3.4", result[0].address)
    }

    @Test
    fun `legacy JsonObject missing name field is skipped`() = testScope.runTest {
        val legacyJson = """[{"address":"1.2.3.4"},{"address":"5.6.7.8","name":"Good"}]"""
        val result = LegacyParsingHarness(legacyJson).recentAddresses.first()

        assertEquals(1, result.size)
        assertEquals("5.6.7.8", result[0].address)
    }

    @Test
    fun `legacy nested JsonArray entries are skipped`() = testScope.runTest {
        val legacyJson = """[["nested","array"],{"address":"1.2.3.4","name":"Good"}]"""
        val result = LegacyParsingHarness(legacyJson).recentAddresses.first()

        assertEquals(1, result.size)
        assertEquals("1.2.3.4", result[0].address)
    }

    @Test
    fun `legacy mixed array handles all element types`() = testScope.runTest {
        // JsonPrimitive + valid JsonObject + malformed JsonObject + nested JsonArray
        val legacyJson = """["10.0.0.1",{"address":"10.0.0.2","name":"Node"},{"name":"bad"},[1,2]]"""
        val result = LegacyParsingHarness(legacyJson).recentAddresses.first()

        assertEquals(2, result.size)
        assertEquals("10.0.0.1", result[0].address)
        assertEquals("Meshtastic", result[0].name)
        assertEquals("10.0.0.2", result[1].address)
    }
}

/**
 * Test harness that mirrors the private legacy parsing logic of [RecentAddressesDataSource] without needing to bypass
 * encapsulation. Exposes a [Flow] that emits the result of parsing a raw legacy JSON string using the same rules as the
 * production fallback path.
 */
private class LegacyParsingHarness(private val rawJson: String) {
    val recentAddresses: Flow<List<RecentAddress>> = flow {
        val jsonArray = Json.parseToJsonElement(rawJson).jsonArray
        emit(
            jsonArray.mapNotNull { item ->
                when (item) {
                    is JsonObject -> {
                        val address = item["address"]?.jsonPrimitive?.contentOrNull
                        val name = item["name"]?.jsonPrimitive?.contentOrNull
                        if (address != null && name != null) {
                            RecentAddress(address = address, name = name)
                        } else {
                            null
                        }
                    }

                    is JsonPrimitive -> {
                        item.contentOrNull?.let { RecentAddress(address = it, name = "Meshtastic") }
                    }

                    is JsonArray -> null
                }
            },
        )
    }
}
