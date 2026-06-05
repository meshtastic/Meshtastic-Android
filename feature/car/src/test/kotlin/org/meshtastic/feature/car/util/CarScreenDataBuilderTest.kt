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
@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

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

package org.meshtastic.feature.car.util

import org.meshtastic.core.model.Node
import org.meshtastic.core.model.util.onlineTimeThreshold
import org.meshtastic.feature.car.model.ConversationUi
import org.meshtastic.feature.car.model.SignalQuality
import org.meshtastic.feature.car.service.MessageSnapshot
import org.meshtastic.proto.DeviceMetrics
import org.meshtastic.proto.LocalStats
import org.meshtastic.proto.Position
import org.meshtastic.proto.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CarScreenDataBuilderTest {

    // determineSignalQuality()

    @Test
    fun `determineSignalQuality returns none when snr is max value`() {
        val quality = CarScreenDataBuilder.determineSignalQuality(Float.MAX_VALUE, -100)

        assertEquals(SignalQuality.NONE, quality)
    }

    @Test
    fun `determineSignalQuality returns none when rssi is max value`() {
        val quality = CarScreenDataBuilder.determineSignalQuality(-5f, Int.MAX_VALUE)

        assertEquals(SignalQuality.NONE, quality)
    }

    @Test
    fun `determineSignalQuality returns excellent for strong snr and strong rssi`() {
        val quality = CarScreenDataBuilder.determineSignalQuality(snr = -6f, rssi = -110)

        assertEquals(SignalQuality.EXCELLENT, quality)
    }

    @Test
    fun `determineSignalQuality returns good for strong snr and fair rssi`() {
        val quality = CarScreenDataBuilder.determineSignalQuality(snr = -6f, rssi = -120)

        assertEquals(SignalQuality.GOOD, quality)
    }

    @Test
    fun `determineSignalQuality returns good for fair snr and strong rssi`() {
        val quality = CarScreenDataBuilder.determineSignalQuality(snr = -10f, rssi = -110)

        assertEquals(SignalQuality.GOOD, quality)
    }

    @Test
    fun `determineSignalQuality returns fair for fair snr and weak rssi`() {
        val quality = CarScreenDataBuilder.determineSignalQuality(snr = -10f, rssi = -130)

        assertEquals(SignalQuality.FAIR, quality)
    }

    @Test
    fun `determineSignalQuality returns bad for weak snr`() {
        val quality = CarScreenDataBuilder.determineSignalQuality(snr = -15f, rssi = -110)

        assertEquals(SignalQuality.BAD, quality)
    }

    @Test
    fun `determineSignalQuality treats snr good threshold as not excellent`() {
        val quality = CarScreenDataBuilder.determineSignalQuality(snr = -7f, rssi = -110)

        assertEquals(SignalQuality.GOOD, quality)
    }

    @Test
    fun `determineSignalQuality treats rssi fair threshold as not good for strong snr`() {
        val quality = CarScreenDataBuilder.determineSignalQuality(snr = -6f, rssi = -126)

        assertEquals(SignalQuality.FAIR, quality)
    }

    @Test
    fun `determineSignalQuality treats snr fair threshold as bad`() {
        val quality = CarScreenDataBuilder.determineSignalQuality(snr = -15f, rssi = -130)

        assertEquals(SignalQuality.BAD, quality)
    }

    // buildNodeUi()

    @Test
    fun `buildNodeUi maps online node with all display fields`() {
        val node =
            createNode(
                num = 101,
                longName = "Alpha Base",
                shortName = "AB",
                snr = -4f,
                rssi = -108,
                lastHeard = onlineLastHeard(120),
                deviceMetrics = DeviceMetrics(battery_level = 87),
                position = validPosition(),
            )

        val ui = CarScreenDataBuilder.buildNodeUi(node)

        assertEquals(101, ui.nodeNum)
        assertEquals("Alpha Base", ui.longName)
        assertEquals("AB", ui.shortName)
        assertEquals(SignalQuality.EXCELLENT, ui.signalQuality)
        assertEquals(87, ui.batteryPercent)
        assertTrue(ui.isOnline)
        assertEquals(node.lastHeard.toLong() * 1000L, ui.lastHeard)
        assertTrue(ui.hasPosition)
    }

    @Test
    fun `buildNodeUi marks offline node from stale last heard`() {
        val node = createNode(num = 102, lastHeard = offlineLastHeard(60))

        val ui = CarScreenDataBuilder.buildNodeUi(node)

        assertFalse(ui.isOnline)
        assertEquals(node.lastHeard.toLong() * 1000L, ui.lastHeard)
    }

    @Test
    fun `buildNodeUi falls back when names are empty`() {
        val node = createNode(num = 103, longName = "", shortName = "")

        val ui = CarScreenDataBuilder.buildNodeUi(node)

        assertEquals("Unknown", ui.longName)
        assertEquals("?", ui.shortName)
    }

    @Test
    fun `buildNodeUi keeps valid battery percentage`() {
        val node = createNode(num = 104, deviceMetrics = DeviceMetrics(battery_level = 42))

        val ui = CarScreenDataBuilder.buildNodeUi(node)

        assertEquals(42, ui.batteryPercent)
    }

    @Test
    fun `buildNodeUi drops zero battery percentage`() {
        val node = createNode(num = 105, deviceMetrics = DeviceMetrics(battery_level = 0))

        val ui = CarScreenDataBuilder.buildNodeUi(node)

        assertNull(ui.batteryPercent)
    }

    @Test
    fun `buildNodeUi drops battery values above one hundred`() {
        val node = createNode(num = 106, deviceMetrics = DeviceMetrics(battery_level = 101))

        val ui = CarScreenDataBuilder.buildNodeUi(node)

        assertNull(ui.batteryPercent)
    }

    @Test
    fun `buildNodeUi returns null battery when metrics do not include one`() {
        val node = createNode(num = 107, deviceMetrics = DeviceMetrics())

        val ui = CarScreenDataBuilder.buildNodeUi(node)

        assertNull(ui.batteryPercent)
    }

    @Test
    fun `buildNodeUi marks node without position as lacking location`() {
        val node = createNode(num = 108, position = Position())

        val ui = CarScreenDataBuilder.buildNodeUi(node)

        assertFalse(ui.hasPosition)
    }

    @Test
    fun `buildNodeUi ignores invalid position coordinates`() {
        val node = createNode(num = 109, position = Position(latitude_i = 910000000, longitude_i = -1224194000))

        val ui = CarScreenDataBuilder.buildNodeUi(node)

        assertFalse(ui.hasPosition)
    }

    // sortNodes()

    @Test
    fun `sortNodes places online nodes before offline nodes`() {
        val onlineRecent = createNode(num = 201, lastHeard = onlineLastHeard(200))
        val offlineRecent = createNode(num = 202, lastHeard = offlineLastHeard(5))
        val onlineOlder = createNode(num = 203, lastHeard = onlineLastHeard(100))
        val offlineOlder = createNode(num = 204, lastHeard = 0)

        val sorted = CarScreenDataBuilder.sortNodes(listOf(offlineRecent, onlineOlder, offlineOlder, onlineRecent))

        assertEquals(listOf(201, 203, 202, 204), sorted.map { it.nodeNum })
        assertTrue(sorted[0].isOnline)
        assertTrue(sorted[1].isOnline)
        assertFalse(sorted[2].isOnline)
        assertFalse(sorted[3].isOnline)
    }

    @Test
    fun `sortNodes orders nodes by last heard descending within online and offline groups`() {
        val onlineNewest = createNode(num = 205, lastHeard = onlineLastHeard(400))
        val onlineOldest = createNode(num = 206, lastHeard = onlineLastHeard(50))
        val offlineNewest = createNode(num = 207, lastHeard = offlineLastHeard(1))
        val offlineOldest = createNode(num = 208, lastHeard = 0)

        val sorted = CarScreenDataBuilder.sortNodes(listOf(offlineOldest, offlineNewest, onlineOldest, onlineNewest))

        assertEquals(listOf(205, 206, 207, 208), sorted.map { it.nodeNum })
        assertTrue(sorted[0].lastHeard > sorted[1].lastHeard)
        assertTrue(sorted[2].lastHeard > sorted[3].lastHeard)
    }

    // sortConversations()

    @Test
    fun `sortConversations orders conversations by newest message first`() {
        val oldest = createConversation(contactKey = "0!old", name = "Old", lastMessageTime = 1_000L)
        val newest = createConversation(contactKey = "0!new", name = "New", lastMessageTime = 5_000L)
        val middle = createConversation(contactKey = "0!mid", name = "Mid", lastMessageTime = 3_000L)

        val sorted = CarScreenDataBuilder.sortConversations(listOf(oldest, newest, middle))

        assertEquals(listOf("0!new", "0!mid", "0!old"), sorted.map { it.contactKey })
    }

    @Test
    fun `sortConversations keeps single conversation unchanged`() {
        val conversation = createConversation(contactKey = "0!solo", name = "Solo", lastMessageTime = 7_000L)

        val sorted = CarScreenDataBuilder.sortConversations(listOf(conversation))

        assertEquals(listOf(conversation), sorted)
    }

    // buildLocalStats()

    @Test
    fun `buildLocalStats uses populated local stats when available`() {
        val ourNode =
            createNode(
                num = 301,
                deviceMetrics =
                DeviceMetrics(
                    battery_level = 82,
                    channel_utilization = 10.5f,
                    air_util_tx = 2.5f,
                    uptime_seconds = 120,
                ),
            )
        val stats =
            LocalStats(
                uptime_seconds = 7_200,
                channel_utilization = 65.5f,
                air_util_tx = 12.25f,
                num_packets_tx = 91,
                num_packets_rx = 123,
            )
        val allNodes = listOf(ourNode, createNode(num = 302), createNode(num = 303, lastHeard = 0))

        val localStats = CarScreenDataBuilder.buildLocalStats(ourNode = ourNode, stats = stats, allNodes = allNodes)

        assertEquals(82, localStats.batteryLevel)
        assertTrue(localStats.hasBattery)
        assertEquals(65.5f, localStats.channelUtilization)
        assertEquals(12.25f, localStats.airUtilization)
        assertEquals(3, localStats.totalNodes)
        assertEquals(2, localStats.onlineNodes)
        assertEquals(7_200, localStats.uptimeSeconds)
        assertEquals(91, localStats.numPacketsTx)
        assertEquals(123, localStats.numPacketsRx)
    }

    @Test
    fun `buildLocalStats falls back to device metrics when local stats have no uptime`() {
        val ourNode =
            createNode(
                num = 304,
                deviceMetrics =
                DeviceMetrics(
                    battery_level = 54,
                    channel_utilization = 22.5f,
                    air_util_tx = 8.75f,
                    uptime_seconds = 3_600,
                ),
            )
        val stats =
            LocalStats(
                uptime_seconds = 0,
                channel_utilization = 99.9f,
                air_util_tx = 99.9f,
                num_packets_tx = 11,
                num_packets_rx = 17,
            )
        val allNodes = listOf(ourNode, createNode(num = 305, lastHeard = 0))

        val localStats = CarScreenDataBuilder.buildLocalStats(ourNode = ourNode, stats = stats, allNodes = allNodes)

        assertEquals(54, localStats.batteryLevel)
        assertTrue(localStats.hasBattery)
        assertEquals(22.5f, localStats.channelUtilization)
        assertEquals(8.75f, localStats.airUtilization)
        assertEquals(2, localStats.totalNodes)
        assertEquals(1, localStats.onlineNodes)
        assertEquals(3_600, localStats.uptimeSeconds)
        assertEquals(11, localStats.numPacketsTx)
        assertEquals(17, localStats.numPacketsRx)
    }

    @Test
    fun `buildLocalStats handles null local node by using zeros and node counts`() {
        val stats =
            LocalStats(
                uptime_seconds = 0,
                channel_utilization = 14.5f,
                air_util_tx = 6.5f,
                num_packets_tx = 33,
                num_packets_rx = 44,
            )
        val allNodes = listOf(createNode(num = 306), createNode(num = 307, lastHeard = 0), createNode(num = 308))

        val localStats = CarScreenDataBuilder.buildLocalStats(ourNode = null, stats = stats, allNodes = allNodes)

        assertEquals(0, localStats.batteryLevel)
        assertFalse(localStats.hasBattery)
        assertEquals(0f, localStats.channelUtilization)
        assertEquals(0f, localStats.airUtilization)
        assertEquals(3, localStats.totalNodes)
        assertEquals(2, localStats.onlineNodes)
        assertEquals(0, localStats.uptimeSeconds)
        assertEquals(33, localStats.numPacketsTx)
        assertEquals(44, localStats.numPacketsRx)
    }

    @Test
    fun `buildLocalStats reports no battery when local node metrics omit it`() {
        val ourNode =
            createNode(num = 309, deviceMetrics = DeviceMetrics(channel_utilization = 5.5f, air_util_tx = 1.5f))
        val stats = LocalStats()

        val localStats =
            CarScreenDataBuilder.buildLocalStats(ourNode = ourNode, stats = stats, allNodes = listOf(ourNode))

        assertEquals(0, localStats.batteryLevel)
        assertFalse(localStats.hasBattery)
        assertEquals(5.5f, localStats.channelUtilization)
        assertEquals(1.5f, localStats.airUtilization)
    }

    // formatUptime()

    @Test
    fun `formatUptime returns zero minutes for seconds below a minute`() {
        val formatted = CarScreenDataBuilder.formatUptime(59)

        assertEquals("0m", formatted)
    }

    @Test
    fun `formatUptime returns whole minutes when under one hour`() {
        val formatted = CarScreenDataBuilder.formatUptime(120)

        assertEquals("2m", formatted)
    }

    @Test
    fun `formatUptime returns hours and minutes when under one day`() {
        val formatted = CarScreenDataBuilder.formatUptime(3_900)

        assertEquals("1h 5m", formatted)
    }

    @Test
    fun `formatUptime returns days and hours when at least one day`() {
        val formatted = CarScreenDataBuilder.formatUptime(97_200)

        assertEquals("1d 3h", formatted)
    }

    @Test
    fun `formatUptime drops leftover minutes once day format is used`() {
        val formatted = CarScreenDataBuilder.formatUptime(176_460)

        assertEquals("2d 1h", formatted)
    }

    // recentMessages()

    @Test
    fun `recentMessages returns default max number of latest messages`() {
        val messages = (1..7).map { index -> createMessage(id = index, timestamp = index * 1_000L) }

        val recent = CarScreenDataBuilder.recentMessages(messages)

        assertEquals(listOf(3, 4, 5, 6, 7), recent.map { it.id })
        assertEquals(5, recent.size)
    }

    @Test
    fun `recentMessages respects explicit limit`() {
        val messages = (1..5).map { index -> createMessage(id = index, timestamp = index * 1_000L) }

        val recent = CarScreenDataBuilder.recentMessages(messages, limit = 2)

        assertEquals(listOf(4, 5), recent.map { it.id })
    }

    @Test
    fun `recentMessages returns all messages when fewer than limit`() {
        val messages = listOf(createMessage(id = 1, timestamp = 1_000L), createMessage(id = 2, timestamp = 2_000L))

        val recent = CarScreenDataBuilder.recentMessages(messages, limit = 5)

        assertEquals(messages, recent)
    }

    @Test
    fun `recentMessages returns empty list when limit is zero`() {
        val messages = (1..3).map { index -> createMessage(id = index, timestamp = index * 1_000L) }

        val recent = CarScreenDataBuilder.recentMessages(messages, limit = 0)

        assertTrue(recent.isEmpty())
    }

    // buildContactKey() and constants

    @Test
    fun `buildContactKey appends broadcast suffix`() {
        val contactKey = CarScreenDataBuilder.buildContactKey(channelIndex = 3)

        assertEquals("3^all", contactKey)
    }

    @Test
    fun `buildContactKey supports zero channel`() {
        val contactKey = CarScreenDataBuilder.buildContactKey(channelIndex = 0)

        assertEquals("0^all", contactKey)
    }

    @Test
    fun `max conversation messages constant matches car conversation limit`() {
        val messages = (1..8).map { index -> createMessage(id = index, timestamp = index * 1_000L) }

        assertEquals(5, CarScreenDataBuilder.MAX_CONVERSATION_MESSAGES)
    }

    @Test
    fun `max conversations constant matches messaging list limit`() {
        assertEquals(10, CarScreenDataBuilder.MAX_CONVERSATIONS)
    }

    private fun createNode(
        num: Int,
        longName: String = "Node $num",
        shortName: String = "N$num",
        snr: Float = -6f,
        rssi: Int = -110,
        lastHeard: Int = onlineLastHeard(60),
        deviceMetrics: DeviceMetrics = DeviceMetrics(),
        position: Position = validPosition(),
    ): Node {
        val user = User(id = "!$num", long_name = longName, short_name = shortName)

        return Node(
            num = num,
            user = user,
            snr = snr,
            rssi = rssi,
            lastHeard = lastHeard,
            deviceMetrics = deviceMetrics,
            position = position,
        )
    }

    private fun createConversation(contactKey: String, name: String, lastMessageTime: Long): ConversationUi =
        ConversationUi(
            contactKey = contactKey,
            displayName = name,
            lastMessage = "Latest from $name",
            lastMessageTime = lastMessageTime,
            unreadCount = 0,
            isEmergency = false,
        )

    private fun createMessage(id: Int, timestamp: Long): MessageSnapshot = MessageSnapshot(
        id = id,
        senderName = "Sender $id",
        text = "Message $id",
        timestamp = timestamp,
        isFromMe = false,
    )

    private fun validPosition(): Position = Position(latitude_i = 377749000, longitude_i = -1224194000)

    private fun onlineLastHeard(offsetSeconds: Int): Int = onlineTimeThreshold() + offsetSeconds

    private fun offlineLastHeard(offsetSeconds: Int): Int = onlineTimeThreshold() - offsetSeconds
}
