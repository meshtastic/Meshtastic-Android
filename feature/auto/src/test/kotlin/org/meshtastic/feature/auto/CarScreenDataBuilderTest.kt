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
package org.meshtastic.feature.auto

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEmpty
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.DeviceMetrics
import org.meshtastic.core.model.Node
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.LocalStats
import org.meshtastic.proto.User
import kotlin.test.Test

/**
 * Unit tests for [CarScreenDataBuilder].
 *
 * All tests are pure JVM — no Android framework or Car App Library dependencies required.
 * Time formatters are injected as lambdas returning fixed strings to keep assertions deterministic.
 */
class CarScreenDataBuilderTest {

    // ---- buildChannelPlaceholders ----

    @Test
    fun `buildChannelPlaceholders - empty channelSet returns empty map`() {
        val result = CarScreenDataBuilder.buildChannelPlaceholders(ChannelSet())
        result.shouldBeEmpty()
    }

    @Test
    fun `buildChannelPlaceholders - single channel produces correct contact key`() {
        val channelSet = ChannelSet(settings = listOf(ChannelSettings(name = "LongFast")))
        val result = CarScreenDataBuilder.buildChannelPlaceholders(channelSet)

        result.keys shouldBe setOf("0${DataPacket.ID_BROADCAST}")
    }

    @Test
    fun `buildChannelPlaceholders - three channels produce three distinct keys`() {
        val channelSet = ChannelSet(
            settings = listOf(
                ChannelSettings(name = "Ch0"),
                ChannelSettings(name = "Ch1"),
                ChannelSettings(name = "Ch2"),
            ),
        )
        val result = CarScreenDataBuilder.buildChannelPlaceholders(channelSet)

        result shouldHaveSize 3
        result.keys shouldBe setOf("0^all", "1^all", "2^all")
    }

    @Test
    fun `buildChannelPlaceholders - placeholder packets have zero time`() {
        val channelSet = ChannelSet(settings = listOf(ChannelSettings(name = "Test")))
        val packet = CarScreenDataBuilder.buildChannelPlaceholders(channelSet).values.first()

        packet.time shouldBe 0L
        packet.to shouldBe DataPacket.ID_BROADCAST
    }

    // ---- buildCarContacts - display names ----

    @Test
    fun `buildCarContacts - broadcast contact uses channel name from channelSet`() {
        val channelSet = ChannelSet(settings = listOf(ChannelSettings(name = "LongFast")))
        val packet = DataPacket(bytes = null, dataType = 1, time = 1000L, channel = 0).apply {
            to = DataPacket.ID_BROADCAST
            from = DataPacket.ID_LOCAL
        }

        val contacts = CarScreenDataBuilder.buildCarContacts(
            merged = mapOf("0^all" to packet),
            myId = "!aabbccdd",
            channelSet = channelSet,
            resolveUser = { User() },
        )

        contacts shouldHaveSize 1
        contacts[0].displayName shouldBe "LongFast"
        contacts[0].isBroadcast shouldBe true
    }

    @Test
    fun `buildCarContacts - broadcast contact uses Channel N fallback when name is empty`() {
        val channelSet = ChannelSet(settings = listOf(ChannelSettings(name = "")))
        val packet = DataPacket(bytes = null, dataType = 1, time = 1000L, channel = 0).apply {
            to = DataPacket.ID_BROADCAST
            from = DataPacket.ID_LOCAL
        }

        val contacts = CarScreenDataBuilder.buildCarContacts(
            merged = mapOf("0^all" to packet),
            myId = null,
            channelSet = channelSet,
            resolveUser = { User() },
        )

        contacts[0].displayName shouldBe "Channel 0"
    }

    @Test
    fun `buildCarContacts - DM contact uses sender long name`() {
        val senderUser = User(id = "!sender", long_name = "Alice Tester", short_name = "ALIC")
        val packet = DataPacket(bytes = null, dataType = 1, time = 2000L, channel = 0).apply {
            to = "!localnode"
            from = "!sender"
        }

        val contacts = CarScreenDataBuilder.buildCarContacts(
            merged = mapOf("!sender" to packet),
            myId = "!localnode",
            channelSet = ChannelSet(),
            resolveUser = { if (it == "!sender") senderUser else User() },
        )

        contacts[0].displayName shouldBe "Alice Tester"
        contacts[0].isBroadcast shouldBe false
    }

    @Test
    fun `buildCarContacts - DM contact falls back to short name when long name is blank`() {
        val senderUser = User(id = "!sender", long_name = "", short_name = "ALIC")
        val packet = DataPacket(bytes = null, dataType = 1, time = 2000L, channel = 0).apply {
            to = "!localnode"
            from = "!sender"
        }

        val contacts = CarScreenDataBuilder.buildCarContacts(
            merged = mapOf("!sender" to packet),
            myId = "!localnode",
            channelSet = ChannelSet(),
            resolveUser = { senderUser },
        )

        contacts[0].displayName shouldBe "ALIC"
    }

    // ---- buildCarContacts - lastMessageText ----

    @Test
    fun `buildCarContacts - received DM prefixes lastMessageText with sender short name`() {
        val senderUser = User(id = "!sender", long_name = "Alice", short_name = "ALIC")
        val packet = DataPacket(to = "!me", channel = 0, text = "Hello!")
        packet.from = "!sender"

        val contacts = CarScreenDataBuilder.buildCarContacts(
            merged = mapOf("!sender" to packet),
            myId = "!me",
            channelSet = ChannelSet(),
            resolveUser = { senderUser },
        )

        contacts[0].lastMessageText shouldBe "ALIC: Hello!"
    }

    @Test
    fun `buildCarContacts - sent DM does not prefix lastMessageText`() {
        val recipientUser = User(id = "!bob", long_name = "Bob", short_name = "BOB")
        val packet = DataPacket(to = "!bob", channel = 0, text = "Hey Bob")
        packet.from = "!me"

        val contacts = CarScreenDataBuilder.buildCarContacts(
            merged = mapOf("!bob" to packet),
            myId = "!me",
            channelSet = ChannelSet(),
            resolveUser = { recipientUser },
        )

        // Sent message — no prefix
        contacts[0].lastMessageText shouldBe "Hey Bob"
    }

    @Test
    fun `buildCarContacts - null packet text yields null lastMessageText`() {
        val packet = DataPacket(bytes = null, dataType = 1, time = 1000L, channel = 0).apply {
            to = DataPacket.ID_BROADCAST
            from = DataPacket.ID_LOCAL
        }
        val channelSet = ChannelSet(settings = listOf(ChannelSettings(name = "Ch0")))

        val contacts = CarScreenDataBuilder.buildCarContacts(
            merged = mapOf("0^all" to packet),
            myId = null,
            channelSet = channelSet,
            resolveUser = { User() },
        )

        contacts[0].lastMessageText shouldBe null
    }

    // ---- buildCarContacts - ordering ----

    @Test
    fun `buildCarContacts - channel contacts appear before DM contacts`() {
        val channelSet = ChannelSet(settings = listOf(ChannelSettings(name = "Ch0")))
        val channelPacket = DataPacket(bytes = null, dataType = 1, time = 1000L, channel = 0).apply {
            to = DataPacket.ID_BROADCAST
            from = DataPacket.ID_LOCAL
        }
        val dmPacket = DataPacket(bytes = null, dataType = 1, time = 2000L, channel = 0).apply {
            to = "!me"
            from = "!alice"
        }

        val contacts = CarScreenDataBuilder.buildCarContacts(
            merged = mapOf("!alice" to dmPacket, "0^all" to channelPacket),
            myId = "!me",
            channelSet = channelSet,
            resolveUser = { User() },
        )

        contacts[0].isBroadcast shouldBe true
        contacts[1].isBroadcast shouldBe false
    }

    @Test
    fun `buildCarContacts - channels are sorted by channelIndex ascending`() {
        val channelSet = ChannelSet(
            settings = listOf(
                ChannelSettings(name = "Ch0"),
                ChannelSettings(name = "Ch1"),
                ChannelSettings(name = "Ch2"),
            ),
        )
        // Insert in reverse order to verify sorting is applied
        val packets = mapOf(
            "2^all" to makeChannelPacket(ch = 2),
            "0^all" to makeChannelPacket(ch = 0),
            "1^all" to makeChannelPacket(ch = 1),
        )

        val contacts = CarScreenDataBuilder.buildCarContacts(
            merged = packets,
            myId = null,
            channelSet = channelSet,
            resolveUser = { User() },
        )

        contacts.map { it.channelIndex } shouldBe listOf(0, 1, 2)
    }

    @Test
    fun `buildCarContacts - DMs are sorted by lastMessageTime descending`() {
        val dmOld = makeDmPacket(from = "!alice", to = "!me", time = 1_000L)
        val dmNew = makeDmPacket(from = "!bob", to = "!me", time = 3_000L)
        val dmMid = makeDmPacket(from = "!carol", to = "!me", time = 2_000L)

        val contacts = CarScreenDataBuilder.buildCarContacts(
            merged = mapOf("!alice" to dmOld, "!carol" to dmMid, "!bob" to dmNew),
            myId = "!me",
            channelSet = ChannelSet(),
            resolveUser = { userId ->
                when (userId) {
                    "!alice" -> User(id = "!alice", long_name = "Alice")
                    "!bob" -> User(id = "!bob", long_name = "Bob")
                    "!carol" -> User(id = "!carol", long_name = "Carol")
                    else -> User()
                }
            },
        )

        contacts.map { it.displayName } shouldBe listOf("Bob", "Carol", "Alice")
    }

    // ---- sortFavorites ----

    @Test
    fun `sortFavorites - excludes non-favorite nodes`() {
        val nodes = listOf(
            Node(num = 1, user = User(long_name = "Alice"), isFavorite = false),
            Node(num = 2, user = User(long_name = "Bob"), isFavorite = true),
        )
        val result = CarScreenDataBuilder.sortFavorites(nodes)

        result shouldHaveSize 1
        result[0].user.long_name shouldBe "Bob"
    }

    @Test
    fun `sortFavorites - results are sorted alphabetically by long name`() {
        val nodes = listOf(
            Node(num = 3, user = User(long_name = "Charlie"), isFavorite = true),
            Node(num = 1, user = User(long_name = "Alice"), isFavorite = true),
            Node(num = 2, user = User(long_name = "Bob"), isFavorite = true),
        )
        val result = CarScreenDataBuilder.sortFavorites(nodes)

        result.map { it.user.long_name } shouldBe listOf("Alice", "Bob", "Charlie")
    }

    @Test
    fun `sortFavorites - falls back to short name when long name is empty`() {
        val nodes = listOf(
            Node(num = 2, user = User(long_name = "", short_name = "ZZZ"), isFavorite = true),
            Node(num = 1, user = User(long_name = "", short_name = "AAA"), isFavorite = true),
        )
        val result = CarScreenDataBuilder.sortFavorites(nodes)

        result[0].user.short_name shouldBe "AAA"
        result[1].user.short_name shouldBe "ZZZ"
    }

    @Test
    fun `sortFavorites - empty collection returns empty list`() {
        CarScreenDataBuilder.sortFavorites(emptyList()).shouldBeEmpty()
    }

    // ---- nodeStatusText ----

    @Test
    fun `nodeStatusText - online node with hopsAway 0 shows Direct`() {
        val node = onlineNode(hopsAway = 0)
        val text = CarScreenDataBuilder.nodeStatusText(node)

        text shouldContain "Online"
        text shouldContain "Direct"
    }

    @Test
    fun `nodeStatusText - online node with 2 hops shows hops count`() {
        val node = onlineNode(hopsAway = 2)
        val text = CarScreenDataBuilder.nodeStatusText(node)

        text shouldContain "Online"
        text shouldContain "2 hops"
    }

    @Test
    fun `nodeStatusText - online node with unknown hops shows just Online`() {
        val node = onlineNode(hopsAway = -1)
        val text = CarScreenDataBuilder.nodeStatusText(node)

        text shouldBe "Online"
    }

    @Test
    fun `nodeStatusText - offline node with no lastHeard shows just Offline`() {
        val node = Node(num = 1, user = User(long_name = "Test"), isFavorite = true, lastHeard = 0)
        val text = CarScreenDataBuilder.nodeStatusText(node)

        text shouldBe "Offline"
    }

    @Test
    fun `nodeStatusText - offline node with lastHeard calls formatRelativeTime`() {
        val node = Node(num = 1, user = User(long_name = "Test"), isFavorite = true, lastHeard = 12345)
        val text = CarScreenDataBuilder.nodeStatusText(node, formatRelativeTime = { "3h ago" })

        text shouldBe "Offline · 3h ago"
    }

    @Test
    fun `nodeStatusText - formatRelativeTime receives lastHeard in millis`() {
        val lastHeardSecs = 100_000
        var receivedMillis = 0L
        val node = Node(num = 1, user = User(long_name = "Test"), isFavorite = true, lastHeard = lastHeardSecs)
        CarScreenDataBuilder.nodeStatusText(node, formatRelativeTime = { millis ->
            receivedMillis = millis
            "ago"
        })

        receivedMillis shouldBe lastHeardSecs * 1000L
    }

    // ---- nodeDetailText ----

    @Test
    fun `nodeDetailText - shows short name and battery separated by bullet`() {
        val node = Node(
            num = 1,
            user = User(long_name = "Alice", short_name = "ALIC"),
            isFavorite = true,
            deviceMetrics = DeviceMetrics(battery_level = 85),
        )
        val text = CarScreenDataBuilder.nodeDetailText(node)

        text shouldContain "ALIC"
        text shouldContain "85%"
        text shouldContain "·"
    }

    @Test
    fun `nodeDetailText - shows only short name when no battery data`() {
        val node = Node(
            num = 1,
            user = User(long_name = "Alice", short_name = "ALIC"),
            isFavorite = true,
        )
        val text = CarScreenDataBuilder.nodeDetailText(node)

        text shouldBe "ALIC"
    }

    @Test
    fun `nodeDetailText - returns empty string when no short name and no battery`() {
        val node = Node(num = 1, user = User(long_name = "Alice", short_name = ""), isFavorite = true)
        CarScreenDataBuilder.nodeDetailText(node).shouldBeEmpty()
    }

    @Test
    fun `nodeDetailText - shows only battery when short name is blank`() {
        val node = Node(
            num = 1,
            user = User(long_name = "Alice", short_name = ""),
            isFavorite = true,
            deviceMetrics = DeviceMetrics(battery_level = 72),
        )
        val text = CarScreenDataBuilder.nodeDetailText(node)

        text shouldBe "72%"
        text shouldNotContain "·"
    }

    // ---- contactPreviewText ----

    @Test
    fun `contactPreviewText - returns lastMessageText when present`() {
        val contact = makeCarContact(lastMessageText = "Hello world")
        CarScreenDataBuilder.contactPreviewText(contact) shouldBe "Hello world"
    }

    @Test
    fun `contactPreviewText - returns No messages yet when lastMessageText is null`() {
        val contact = makeCarContact(lastMessageText = null)
        CarScreenDataBuilder.contactPreviewText(contact) shouldBe "No messages yet"
    }

    @Test
    fun `contactPreviewText - returns No messages yet when lastMessageText is empty`() {
        val contact = makeCarContact(lastMessageText = "")
        CarScreenDataBuilder.contactPreviewText(contact) shouldBe "No messages yet"
    }

    // ---- contactSecondaryText ----

    @Test
    fun `contactSecondaryText - shows N unread when unreadCount is positive`() {
        val contact = makeCarContact(unreadCount = 5)
        CarScreenDataBuilder.contactSecondaryText(contact) shouldBe "5 unread"
    }

    @Test
    fun `contactSecondaryText - calls formatShortDate when no unread but time is set`() {
        val contact = makeCarContact(unreadCount = 0, lastMessageTime = 999_999L)
        val text = CarScreenDataBuilder.contactSecondaryText(contact, formatShortDate = { "Jan 1" })

        text shouldBe "Jan 1"
    }

    @Test
    fun `contactSecondaryText - formatShortDate receives the exact lastMessageTime`() {
        val timestamp = 123_456_789L
        val contact = makeCarContact(unreadCount = 0, lastMessageTime = timestamp)
        var received = 0L
        CarScreenDataBuilder.contactSecondaryText(contact, formatShortDate = { millis ->
            received = millis
            "date"
        })

        received shouldBe timestamp
    }

    @Test
    fun `contactSecondaryText - returns empty string when no unread and no lastMessageTime`() {
        val contact = makeCarContact(unreadCount = 0, lastMessageTime = null)
        CarScreenDataBuilder.contactSecondaryText(contact).shouldBeEmpty()
    }

    @Test
    fun `contactSecondaryText - unread takes precedence over lastMessageTime`() {
        val contact = makeCarContact(unreadCount = 3, lastMessageTime = 500L)
        CarScreenDataBuilder.contactSecondaryText(contact, formatShortDate = { "should not appear" }) shouldBe "3 unread"
    }

    // ---- buildLocalStats ----

    @Test
    fun `buildLocalStats - returns defaults when ourNode is null and stats are empty`() {
        val result = CarScreenDataBuilder.buildLocalStats(null, LocalStats(), emptyList())

        result.hasBattery shouldBe false
        result.batteryLevel shouldBe 0
        result.channelUtilization shouldBe 0f
        result.airUtilization shouldBe 0f
        result.totalNodes shouldBe 0
        result.onlineNodes shouldBe 0
        result.uptimeSeconds shouldBe 0
    }

    @Test
    fun `buildLocalStats - reads battery from device metrics`() {
        val node = Node(
            num = 1,
            user = User(long_name = "Me"),
            deviceMetrics = DeviceMetrics(battery_level = 85),
        )
        val result = CarScreenDataBuilder.buildLocalStats(node, LocalStats(), emptyList())

        result.hasBattery shouldBe true
        result.batteryLevel shouldBe 85
    }

    @Test
    fun `buildLocalStats - prefers LocalStats utilization over device metrics`() {
        val node = Node(
            num = 1,
            user = User(long_name = "Me"),
            deviceMetrics = DeviceMetrics(channel_utilization = 5f, air_util_tx = 1f),
        )
        val stats = LocalStats(
            uptime_seconds = 100,
            channel_utilization = 18.5f,
            air_util_tx = 3.2f,
        )
        val result = CarScreenDataBuilder.buildLocalStats(node, stats, emptyList())

        result.channelUtilization shouldBe 18.5f
        result.airUtilization shouldBe 3.2f
        result.uptimeSeconds shouldBe 100
    }

    @Test
    fun `buildLocalStats - falls back to device metrics when LocalStats uptime is zero`() {
        val node = Node(
            num = 1,
            user = User(long_name = "Me"),
            deviceMetrics = DeviceMetrics(
                channel_utilization = 5f,
                air_util_tx = 1f,
                uptime_seconds = 3600,
            ),
        )
        val result = CarScreenDataBuilder.buildLocalStats(node, LocalStats(), emptyList())

        result.channelUtilization shouldBe 5f
        result.airUtilization shouldBe 1f
        result.uptimeSeconds shouldBe 3600
    }

    @Test
    fun `buildLocalStats - counts total and online nodes`() {
        val nowSecs = (System.currentTimeMillis() / 1000).toInt()
        val nodes = listOf(
            Node(num = 1, lastHeard = nowSecs),
            Node(num = 2, lastHeard = nowSecs),
            Node(num = 3, lastHeard = 0), // offline
        )
        val result = CarScreenDataBuilder.buildLocalStats(null, LocalStats(), nodes)

        result.totalNodes shouldBe 3
        result.onlineNodes shouldBe 2
    }

    @Test
    fun `buildLocalStats - copies traffic counters from LocalStats`() {
        val stats = LocalStats(
            uptime_seconds = 1,
            num_packets_tx = 145,
            num_packets_rx = 892,
            num_rx_dupe = 42,
        )
        val result = CarScreenDataBuilder.buildLocalStats(null, stats, emptyList())

        result.numPacketsTx shouldBe 145
        result.numPacketsRx shouldBe 892
        result.numRxDupe shouldBe 42
    }

    // ---- helpers ----

    /** Returns a node guaranteed to be online (lastHeard == now). */
    private fun onlineNode(hopsAway: Int): Node {
        val nowSecs = (System.currentTimeMillis() / 1000).toInt()
        return Node(
            num = 1,
            user = User(long_name = "Test"),
            isFavorite = true,
            lastHeard = nowSecs,
            hopsAway = hopsAway,
        )
    }

    private fun makeChannelPacket(ch: Int): DataPacket =
        DataPacket(bytes = null, dataType = 1, time = 1000L, channel = ch).apply {
            to = DataPacket.ID_BROADCAST
            from = DataPacket.ID_LOCAL
        }

    private fun makeDmPacket(from: String, to: String, time: Long): DataPacket =
        DataPacket(bytes = null, dataType = 1, time = time, channel = 0).apply {
            this.from = from
            this.to = to
        }

    private fun makeCarContact(
        contactKey: String = "!test",
        displayName: String = "Test",
        unreadCount: Int = 0,
        isBroadcast: Boolean = false,
        channelIndex: Int = 0,
        lastMessageTime: Long? = null,
        lastMessageText: String? = null,
    ) = CarContact(
        contactKey = contactKey,
        displayName = displayName,
        unreadCount = unreadCount,
        isBroadcast = isBroadcast,
        channelIndex = channelIndex,
        lastMessageTime = lastMessageTime,
        lastMessageText = lastMessageText,
    )
}
