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
package org.meshtastic.app.map.repository

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.meshtastic.app.map.model.CustomTileProviderConfig
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.testing.FakeMapTileProviderPrefs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CustomTileProviderRepositoryTest {
    @Test
    fun `stored providers load before first mutation and survive recreation`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val dispatchers = CoroutineDispatchers(dispatcher, dispatcher, dispatcher)
        val prefs = FakeMapTileProviderPrefs()
        val json = Json { ignoreUnknownKeys = true }
        val existing = provider(id = "existing", name = "Existing")
        prefs.customTileProviders.value = json.encodeToString(listOf(existing))
        val repository = CustomTileProviderRepositoryImpl(json, dispatchers, prefs)

        assertEquals(
            CustomTileProviderSaveResult.SAVED,
            repository.addCustomTileProvider(provider(id = "new", name = "New")),
        )
        advanceUntilIdle()

        val stored = json.decodeFromString<List<CustomTileProviderConfig>>(prefs.customTileProviders.value!!)
        assertEquals(listOf("existing", "new"), stored.map { it.id })
        val recreated = CustomTileProviderRepositoryImpl(json, dispatchers, prefs)
        val reloaded = recreated.awaitCustomTileProviders()
        assertTrue(reloaded.isSuccessful)
        assertEquals(listOf("existing", "new"), reloaded.providers.map { it.id })
        advanceUntilIdle()
        assertEquals(listOf("existing", "new"), recreated.getCustomTileProviders().first().map { it.id })
    }

    @Test
    fun `duplicate name is rejected atomically before cold load completes`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val dispatchers = CoroutineDispatchers(dispatcher, dispatcher, dispatcher)
        val prefs = FakeMapTileProviderPrefs()
        val json = Json { ignoreUnknownKeys = true }
        val existing = provider(id = "existing", name = "Existing")
        prefs.customTileProviders.value = json.encodeToString(listOf(existing))
        val repository = CustomTileProviderRepositoryImpl(json, dispatchers, prefs)

        val result = repository.addCustomTileProvider(provider(id = "duplicate", name = "  eXiStInG  "))

        assertEquals(CustomTileProviderSaveResult.DUPLICATE_NAME, result)
        assertEquals(
            listOf(existing),
            json.decodeFromString<List<CustomTileProviderConfig>>(prefs.customTileProviders.value!!),
        )
    }

    @Test
    fun `update cannot claim another provider name`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val dispatchers = CoroutineDispatchers(dispatcher, dispatcher, dispatcher)
        val prefs = FakeMapTileProviderPrefs()
        val json = Json { ignoreUnknownKeys = true }
        val existing = provider(id = "existing", name = "Existing")
        val other = provider(id = "other", name = "Other")
        prefs.customTileProviders.value = json.encodeToString(listOf(existing, other))
        val repository = CustomTileProviderRepositoryImpl(json, dispatchers, prefs)

        val result = repository.updateCustomTileProvider(other.copy(name = "EXISTING"))

        assertEquals(CustomTileProviderSaveResult.DUPLICATE_NAME, result)
        assertEquals(
            listOf(existing, other),
            json.decodeFromString<List<CustomTileProviderConfig>>(prefs.customTileProviders.value!!),
        )
    }

    @Test
    fun `successful update normalizes and persists the provider`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val dispatchers = CoroutineDispatchers(dispatcher, dispatcher, dispatcher)
        val prefs = FakeMapTileProviderPrefs()
        val json = Json { ignoreUnknownKeys = true }
        val existing = provider(id = "existing", name = "Existing")
        prefs.customTileProviders.value = json.encodeToString(listOf(existing))
        val repository = CustomTileProviderRepositoryImpl(json, dispatchers, prefs)

        val result =
            repository.updateCustomTileProvider(
                existing.copy(name = "  Updated  ", urlTemplate = "  https://updated.example.org/{z}/{x}/{y}.png  "),
            )

        assertEquals(CustomTileProviderSaveResult.SAVED, result)
        assertEquals(
            listOf(existing.copy(name = "Updated", urlTemplate = "https://updated.example.org/{z}/{x}/{y}.png")),
            json.decodeFromString<List<CustomTileProviderConfig>>(prefs.customTileProviders.value!!),
        )
    }

    @Test
    fun `missing update leaves persisted providers unchanged`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val dispatchers = CoroutineDispatchers(dispatcher, dispatcher, dispatcher)
        val prefs = FakeMapTileProviderPrefs()
        val json = Json { ignoreUnknownKeys = true }
        val existing = provider(id = "existing", name = "Existing")
        prefs.customTileProviders.value = json.encodeToString(listOf(existing))
        val repository = CustomTileProviderRepositoryImpl(json, dispatchers, prefs)

        val result = repository.updateCustomTileProvider(provider(id = "missing", name = "Missing"))

        assertEquals(CustomTileProviderSaveResult.NOT_FOUND, result)
        assertEquals(
            listOf(existing),
            json.decodeFromString<List<CustomTileProviderConfig>>(prefs.customTileProviders.value!!),
        )
    }

    @Test
    fun `delete persists the remaining providers`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val dispatchers = CoroutineDispatchers(dispatcher, dispatcher, dispatcher)
        val prefs = FakeMapTileProviderPrefs()
        val json = Json { ignoreUnknownKeys = true }
        val first = provider(id = "first", name = "First")
        val second = provider(id = "second", name = "Second")
        prefs.customTileProviders.value = json.encodeToString(listOf(first, second))
        val repository = CustomTileProviderRepositoryImpl(json, dispatchers, prefs)

        repository.deleteCustomTileProvider(first.id)

        assertEquals(
            listOf(second),
            json.decodeFromString<List<CustomTileProviderConfig>>(prefs.customTileProviders.value!!),
        )
    }

    @Test
    fun `malformed persisted providers report a failed load without rewriting storage`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val dispatchers = CoroutineDispatchers(dispatcher, dispatcher, dispatcher)
        val prefs = FakeMapTileProviderPrefs()
        val malformed = "[{not-json}]"
        prefs.customTileProviders.value = malformed
        val repository = CustomTileProviderRepositoryImpl(Json { ignoreUnknownKeys = true }, dispatchers, prefs)

        val result = repository.awaitCustomTileProviders()

        assertFalse(result.isSuccessful)
        assertEquals(emptyList(), result.providers)
        assertEquals(malformed, prefs.customTileProviders.value)
    }

    private fun provider(id: String, name: String) =
        CustomTileProviderConfig(id = id, name = name, urlTemplate = "https://tiles.example.org/{z}/{x}/{y}.png")
}
