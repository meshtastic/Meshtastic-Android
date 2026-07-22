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
package org.meshtastic.core.service

import android.content.Context
import android.content.pm.ShortcutManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.Node
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.User
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.meshtastic.core.model.Channel as MeshChannel

/**
 * Behavioral coverage for conversation-shortcut publishing: recency ranking across DMs and channels, effective channel
 * naming (empty primary → modem-preset name), and stale-shortcut removal.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ConversationShortcutPublisherTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val shortcutManager = context.getSystemService(ShortcutManager::class.java)!!

    private val hawk = Node(num = 7, user = User(id = "!00000007", long_name = "Hawk Ridge", short_name = "HAWK"))

    private val packetRepository: PacketRepository = mock(MockMode.autofill)
    private val nodeRepository: NodeRepository = mock(MockMode.autofill)
    private val radioConfigRepository: RadioConfigRepository = mock(MockMode.autofill)
    private val dispatchers =
        CoroutineDispatchers(
            io = Dispatchers.Unconfined,
            main = Dispatchers.Unconfined,
            default = Dispatchers.Unconfined,
        )

    private val publisher =
        ConversationShortcutPublisher(context, nodeRepository, packetRepository, radioConfigRepository, dispatchers)

    @Before
    fun setUp() {
        shortcutManager.removeAllDynamicShortcuts()
        every { nodeRepository.nodeDBbyNum } returns MutableStateFlow(mapOf(7 to hawk))
        // Primary channel with an empty name (resolves to the preset name) plus one named secondary channel.
        every { radioConfigRepository.channelSetFlow } returns
            flowOf(
                ChannelSet(
                    settings = listOf(MeshChannel.default.settings, ChannelSettings(name = "Beta")),
                    lora_config = MeshChannel.default.loraConfig,
                ),
            )
    }

    private fun contact(from: String?, time: Long) = DataPacket(bytes = null, dataType = 1, from = from, time = time)

    @Test
    fun `shortcuts are ranked by recency across dms and channels`() = runTest {
        every { packetRepository.getContacts() } returns
            flowOf(
                mapOf(
                    "0!00000007" to contact(from = "!00000007", time = 3_000), // newest → rank 0
                    "0^all" to contact(from = null, time = 2_000), // rank 1; "Beta" never messaged → last
                ),
            )

        publisher.startObserving(this)
        advanceUntilIdle()

        val ranks = shortcutManager.dynamicShortcuts.associate { it.id to it.rank }
        assertEquals(0, ranks["0!00000007"], "most recently active conversation ranks first")
        assertEquals(1, ranks["0^all"])
        assertEquals(2, ranks["1^all"], "never-messaged channel sorts last")
    }

    @Test
    fun `dm shortcuts carry node labels and the empty primary resolves to its preset name`() = runTest {
        every { packetRepository.getContacts() } returns
            flowOf(mapOf("0!00000007" to contact(from = "!00000007", time = 1_000)))

        publisher.startObserving(this)
        advanceUntilIdle()

        val byId = shortcutManager.dynamicShortcuts.associateBy { it.id }
        assertEquals("HAWK", byId.getValue("0!00000007").shortLabel)
        assertEquals("Hawk Ridge", byId.getValue("0!00000007").longLabel)
        assertEquals("LongFast", byId.getValue("0^all").shortLabel)
        assertEquals("Beta", byId.getValue("1^all").shortLabel)
    }

    @Test
    fun `stale shortcuts are removed when no longer part of the conversation set`() = runTest {
        // Simulate a leftover shortcut from a previous session (raw push: the in-memory on-demand protection does not
        // survive a process restart, so the observer owns its cleanup).
        pushRawShortcut("9^all", "Old Channel")
        assertTrue(shortcutManager.dynamicShortcuts.any { it.id == "9^all" })

        every { packetRepository.getContacts() } returns flowOf(emptyMap())

        publisher.startObserving(this)
        advanceUntilIdle()

        assertNull(shortcutManager.dynamicShortcuts.find { it.id == "9^all" }, "stale shortcut should be pruned")
        assertTrue(shortcutManager.dynamicShortcuts.any { it.id == "0^all" }, "current channels are published")
    }

    @Test
    fun `on-demand shortcuts survive pruning until a snapshot includes their conversation`() = runTest {
        // A brand-new conversation's first notification publishes its shortcut before the contacts flow emits it.
        publisher.ensureConversationShortcut("0!000000ff", "New Peer")

        every { packetRepository.getContacts() } returns flowOf(emptyMap())

        publisher.startObserving(this)
        advanceUntilIdle()

        assertTrue(
            shortcutManager.dynamicShortcuts.any { it.id == "0!000000ff" },
            "pending on-demand shortcut must not be pruned before the snapshot catches up",
        )
    }

    private fun pushRawShortcut(id: String, label: String) {
        val shortcut =
            androidx.core.content.pm.ShortcutInfoCompat.Builder(context, id)
                .setShortLabel(label)
                .setIntent(android.content.Intent(android.content.Intent.ACTION_VIEW))
                .build()
        androidx.core.content.pm.ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
    }
}
