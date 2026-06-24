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
import okio.Buffer
import okio.Source
import org.meshtastic.core.data.datasource.BundledAssetReader
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.EventFirmwareEdition
import org.meshtastic.core.model.EventFirmwareResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EventFirmwareRepositoryImplTest {

    /** Serves only `event_firmware.json`, serializing [editions] so the repo decodes via the real path. */
    private class FakeBundledAssetReader(
        var editions: List<EventFirmwareEdition>,
        private val json: Json,
        var present: Boolean = true,
    ) : BundledAssetReader {
        var opens = 0
            private set

        override fun open(name: String): Source? {
            if (name != "event_firmware.json" || !present) return null
            opens++
            val bytes = json.encodeToString(EventFirmwareResponse(editions = editions)).encodeToByteArray()
            return Buffer().write(bytes)
        }
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val unconfined = Dispatchers.Unconfined
    private val dispatchers = CoroutineDispatchers(main = unconfined, io = unconfined, default = unconfined)

    private fun edition(name: String) =
        EventFirmwareEdition(edition = name, displayName = name.lowercase(), welcomeMessage = "hi $name")

    @Test
    fun getEditionReturnsMatchingRecordByEnumName() = runBlocking {
        val reader = FakeBundledAssetReader(listOf(edition("HAMVENTION"), edition("DEFCON")), json)
        val repo = EventFirmwareRepositoryImpl(reader, json, dispatchers)

        assertEquals("hamvention", repo.getEdition("HAMVENTION")?.displayName)
        assertEquals("hi DEFCON", repo.getEdition("DEFCON")?.welcomeMessage)
    }

    @Test
    fun getEditionReturnsNullForUnknownEdition() = runBlocking {
        val repo =
            EventFirmwareRepositoryImpl(FakeBundledAssetReader(listOf(edition("HAMVENTION")), json), json, dispatchers)

        assertNull(repo.getEdition("VANILLA"))
    }

    @Test
    fun absentAssetYieldsNullWithoutCrashing() = runBlocking {
        val repo =
            EventFirmwareRepositoryImpl(FakeBundledAssetReader(emptyList(), json, present = false), json, dispatchers)

        assertNull(repo.getEdition("HAMVENTION"))
    }

    @Test
    fun snapshotIsDecodedOnlyOnceAndCached() = runBlocking {
        val reader = FakeBundledAssetReader(listOf(edition("HAMVENTION")), json)
        val repo = EventFirmwareRepositoryImpl(reader, json, dispatchers)

        repo.getEdition("HAMVENTION")
        repo.getEdition("DEFCON")
        repo.getEdition("HAMVENTION")

        assertEquals(1, reader.opens)
    }
}
