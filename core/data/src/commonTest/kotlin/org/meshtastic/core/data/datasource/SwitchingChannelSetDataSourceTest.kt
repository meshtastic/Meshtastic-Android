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

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.testing.FakeDatabaseProvider
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SwitchingChannelSetDataSourceTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val dispatchers = CoroutineDispatchers(main = testDispatcher, io = testDispatcher, default = testDispatcher)
    private val dbProvider = FakeDatabaseProvider()
    private val dataSource = SwitchingChannelSetDataSource(dbProvider, dispatchers)

    @AfterTest fun tearDown() = dbProvider.close()

    private fun secondary(index: Int, name: String) =
        Channel(index = index, settings = ChannelSettings(name = name), role = Channel.Role.SECONDARY)

    @Test
    fun `updateChannelSettings places channel at its index`() = runTest(testDispatcher) {
        dataSource.updateChannelSettings(secondary(0, "primary"))
        dataSource.updateChannelSettings(secondary(1, "second"))

        val names = dataSource.channelSetFlow.first().settings.map { it.name }
        assertEquals(listOf("primary", "second"), names)
    }

    @Test
    fun `updateChannelSettings fills gaps with blank channels to preserve indices`() = runTest(testDispatcher) {
        // Arrive out of order / with a gap: index 2 before 0/1 exist.
        dataSource.updateChannelSettings(secondary(2, "third"))

        val settings = dataSource.channelSetFlow.first().settings
        assertEquals(3, settings.size)
        assertEquals("", settings[0].name)
        assertEquals("", settings[1].name)
        assertEquals("third", settings[2].name)
    }

    @Test
    fun `disabled channels are ignored`() = runTest(testDispatcher) {
        dataSource.updateChannelSettings(Channel(index = 0, role = Channel.Role.DISABLED))
        assertTrue(dataSource.channelSetFlow.first().settings.isEmpty())
    }

    @Test
    fun `replaceAllSettings replaces the whole list`() = runTest(testDispatcher) {
        dataSource.updateChannelSettings(secondary(0, "old"))
        dataSource.replaceAllSettings(listOf(ChannelSettings(name = "a"), ChannelSettings(name = "b")))

        assertEquals(listOf("a", "b"), dataSource.channelSetFlow.first().settings.map { it.name })
    }

    @Test
    fun `setLoraConfig preserves existing channel settings`() = runTest(testDispatcher) {
        dataSource.updateChannelSettings(secondary(0, "keep"))
        dataSource.setLoraConfig(Config.LoRaConfig(channel_num = 7))

        val set = dataSource.channelSetFlow.first()
        assertEquals("keep", set.settings.single().name)
        assertEquals(7, set.lora_config?.channel_num)
    }

    @Test
    fun `clearChannelSet empties the channel set`() = runTest(testDispatcher) {
        dataSource.updateChannelSettings(secondary(0, "gone"))
        dataSource.clearChannelSet()
        assertTrue(dataSource.channelSetFlow.first().settings.isEmpty())
    }

    /**
     * Regression for #4623: channels are now stored per-device. Switching to a different device's database must NOT
     * surface the previous device's channels — the old global DataStore was the source of the cross-device duplicate
     * channel rows.
     */
    @Test
    fun `switching device does not leak channels across databases`() = runTest(testDispatcher) {
        dataSource.updateChannelSettings(secondary(0, "deviceA-primary"))
        assertEquals("deviceA-primary", dataSource.channelSetFlow.first().settings.single().name)

        dbProvider.switchToNewDatabase()

        assertTrue(
            dataSource.channelSetFlow.first().settings.isEmpty(),
            "A freshly selected device must start with no channels, not device A's",
        )
    }

    /** Handshake fires overlapping updateChannelSettings as it streams channels; the mutex must not drop any. */
    @Test
    fun `concurrent updateChannelSettings does not lose updates`() = runTest(testDispatcher) {
        val n = 8
        coroutineScope {
            (0 until n).map { i -> async { dataSource.updateChannelSettings(secondary(i, "ch$i")) } }.awaitAll()
        }

        val settings = dataSource.channelSetFlow.first().settings
        assertEquals(n, settings.size)
        assertEquals((0 until n).map { "ch$it" }, settings.map { it.name })
    }
}
