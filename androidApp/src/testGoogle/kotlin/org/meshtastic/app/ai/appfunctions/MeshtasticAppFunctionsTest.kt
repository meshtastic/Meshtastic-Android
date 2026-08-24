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
package org.meshtastic.app.ai.appfunctions

import androidx.appfunctions.AppFunctionContext
import androidx.appfunctions.AppFunctionElementNotFoundException
import androidx.appfunctions.AppFunctionInvalidArgumentException
import androidx.appfunctions.AppFunctionNotSupportedException
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.mock
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.meshtastic.core.data.ai.AiFunctionProvider
import org.meshtastic.core.data.ai.ChannelSummary
import org.meshtastic.core.data.ai.ContactUnread
import org.meshtastic.core.data.ai.DeviceStatus
import org.meshtastic.core.data.ai.GetChannelInfoResult
import org.meshtastic.core.data.ai.GetDeviceStatusResult
import org.meshtastic.core.data.ai.GetMeshMetricsResult
import org.meshtastic.core.data.ai.GetNodeDetailsResult
import org.meshtastic.core.data.ai.GetNodeListResult
import org.meshtastic.core.data.ai.GetRecentMessagesResult
import org.meshtastic.core.data.ai.GetUnreadSummaryResult
import org.meshtastic.core.data.ai.MeshMetrics
import org.meshtastic.core.data.ai.MeshStatusResult
import org.meshtastic.core.data.ai.MessageSummary
import org.meshtastic.core.data.ai.NodeDetails
import org.meshtastic.core.data.ai.NodeSummary
import org.meshtastic.core.data.ai.SendMessageResult
import org.meshtastic.core.data.ai.UnreadSummary
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class MeshtasticAppFunctionsTest {

    private val provider: AiFunctionProvider = mock(MockMode.autofill)
    private val context: AppFunctionContext = mock(MockMode.autofill)
    private val appFunctions = MeshtasticAppFunctions(provider)

    @Test
    fun sendMessage_success() = runTest {
        everySuspend { provider.sendMessage("Hello", "Alice", null) } returns
            SendMessageResult.Success(messageId = 1234, channel = "Primary", timestamp = 1700000000L)

        val response = appFunctions.sendMessage(context, "Hello", "Alice", null)

        assertEquals(1234, response.messageId)
        assertEquals("Primary", response.channel)
        assertEquals(1700000000L, response.timestamp)
    }

    @Test
    fun sendMessage_ambiguousName() = runTest {
        everySuspend { provider.sendMessage("Hello", "Al", null) } returns
            SendMessageResult.AmbiguousName(listOf("Alice", "Albert"))

        val exception =
            assertFailsWith<AppFunctionInvalidArgumentException> {
                appFunctions.sendMessage(context, "Hello", "Al", null)
            }
        assertTrue(exception.message!!.contains("Multiple nodes match that name"))
    }

    @Test
    fun sendMessage_notConnected() = runTest {
        everySuspend { provider.sendMessage("Hello", "Alice", null) } returns
            SendMessageResult.NotConnected("Not connected")

        assertFailsWith<AppFunctionNotSupportedException> { appFunctions.sendMessage(context, "Hello", "Alice", null) }
    }

    @Test
    fun getMeshStatus_success() = runTest {
        everySuspend { provider.getMeshStatus() } returns
            MeshStatusResult(
                connectionState = "CONNECTED",
                onlineNodeCount = 5,
                totalNodeCount = 10,
                localBatteryLevel = 88,
                localNodeName = "MyNode",
            )

        val response = appFunctions.getMeshStatus(context)

        assertEquals("CONNECTED", response.connectionState)
        assertEquals(5, response.onlineNodeCount)
        assertEquals(10, response.totalNodeCount)
        assertEquals(88, response.localBatteryLevel)
        assertEquals("MyNode", response.localNodeName)
    }

    @Test
    fun getNodeList_success() = runTest {
        val nodes =
            listOf(
                NodeSummary(id = "1", name = "Alice", batteryLevel = 90, lastHeard = 1700000000L, isOnline = true),
                NodeSummary(id = "2", name = "Bob", batteryLevel = null, lastHeard = 1600000000L, isOnline = false),
            )
        everySuspend { provider.getNodeList() } returns GetNodeListResult.Success(nodes)

        val response = appFunctions.getNodeList(context)

        assertEquals(2, response.nodes.size)
        assertEquals("1", response.nodes[0].id)
        assertEquals("Alice", response.nodes[0].name)
        assertEquals(90, response.nodes[0].batteryLevel)
        assertTrue(response.nodes[0].isOnline)
        assertEquals("Bob", response.nodes[1].name)
        assertEquals(null, response.nodes[1].batteryLevel)
    }

    @Test
    fun getChannelInfo_success() = runTest {
        val channels =
            listOf(
                ChannelSummary(
                    index = 0,
                    name = "Primary",
                    isPrimary = true,
                    uplinkEnabled = true,
                    downlinkEnabled = true,
                ),
                ChannelSummary(
                    index = 1,
                    name = "Secondary",
                    isPrimary = false,
                    uplinkEnabled = true,
                    downlinkEnabled = false,
                ),
            )
        everySuspend { provider.getChannelInfo() } returns GetChannelInfoResult.Success(channels)

        val response = appFunctions.getChannelInfo(context)

        assertEquals(2, response.channels.size)
        assertEquals("Primary", response.channels[0].name)
        assertTrue(response.channels[0].isPrimary)
        assertTrue(response.channels[0].uplinkEnabled)
        assertEquals("Secondary", response.channels[1].name)
    }

    @Test
    fun getDeviceStatus_success() = runTest {
        val device =
            DeviceStatus(
                model = "T-Beam",
                firmwareVersion = "2.3.15",
                batteryLevel = 100,
                chargingStatus = "NOT_CHARGING",
                deviceName = "MyDevice",
                isActive = true,
            )
        everySuspend { provider.getDeviceStatus() } returns GetDeviceStatusResult.Success(device)

        val response = appFunctions.getDeviceStatus(context)

        assertEquals("T-Beam", response.model)
        assertEquals("2.3.15", response.firmwareVersion)
        assertEquals(100, response.batteryLevel)
        assertEquals("NOT_CHARGING", response.chargingStatus)
        assertEquals("MyDevice", response.deviceName)
        assertTrue(response.isActive)
    }

    @Test
    fun getNodeDetails_success() = runTest {
        val nodeDetails =
            NodeDetails(
                id = "!abc12345",
                userId = "abc12345",
                name = "TestNode",
                batteryLevel = 75,
                voltage = 3.9f,
                hardwareModel = "T-Echo",
                firmwareVersion = "2.3.15",
                snr = 5.5f,
                rssi = -90,
                hopsAway = 1,
                channel = 0,
                lastHeard = 1700000000L,
                userRole = "CLIENT",
                isLicensed = false,
                latitude = 45.0,
                longitude = -90.0,
            )
        everySuspend { provider.getNodeDetails("!abc12345") } returns GetNodeDetailsResult.Success(nodeDetails)

        val response = appFunctions.getNodeDetails(context, "!abc12345")

        assertEquals("!abc12345", response.id)
        assertEquals("TestNode", response.name)
        assertEquals(75, response.batteryLevel)
        assertEquals(3.9f, response.voltage)
        assertEquals(45.0, response.latitude)
    }

    @Test
    fun getNodeDetails_notFound() = runTest {
        everySuspend { provider.getNodeDetails("!unknown") } returns GetNodeDetailsResult.NotFound("Node not found")

        assertFailsWith<AppFunctionElementNotFoundException> { appFunctions.getNodeDetails(context, "!unknown") }
    }

    @Test
    fun getMeshMetrics_success() = runTest {
        val metrics =
            MeshMetrics(
                totalNodeCount = 12,
                onlineNodeCount = 4,
                averageBatteryLevel = 82,
                meshHealthScore = 95,
                mostRecentPacketTime = 1700000000L,
                meshUptimeSeconds = 3600L,
                channelUtilizationPercent = 5,
            )
        everySuspend { provider.getMeshMetrics() } returns GetMeshMetricsResult.Success(metrics)

        val response = appFunctions.getMeshMetrics(context)

        assertEquals(12, response.totalNodeCount)
        assertEquals(4, response.onlineNodeCount)
        assertEquals(82, response.averageBatteryLevel)
        assertEquals(95, response.meshHealthScore)
    }

    @Test
    fun getRecentMessages_success() = runTest {
        val messages =
            listOf(
                MessageSummary(
                    senderName = "Alice",
                    text = "Hi",
                    contactName = "Alice",
                    receivedTime = 1700000000L,
                    fromLocal = false,
                    read = true,
                ),
            )
        everySuspend { provider.getRecentMessages(null, 10) } returns GetRecentMessagesResult.Success(messages)

        val response = appFunctions.getRecentMessages(context, null, 10)

        assertEquals(1, response.messages.size)
        assertEquals("Alice", response.messages[0].senderName)
        assertEquals("Hi", response.messages[0].text)
    }

    @Test
    fun getUnreadSummary_success() = runTest {
        val summary =
            UnreadSummary(
                totalUnreadCount = 3,
                contacts =
                listOf(
                    ContactUnread(
                        name = "Alice",
                        unreadCount = 2,
                        lastMessagePreview = "Hi",
                        lastMessageTime = 1700000000L,
                    ),
                ),
            )
        everySuspend { provider.getUnreadSummary() } returns GetUnreadSummaryResult.Success(summary)

        val response = appFunctions.getUnreadSummary(context)

        assertEquals(3, response.totalUnreadCount)
        assertEquals(1, response.contacts.size)
        assertEquals("Alice", response.contacts[0].name)
        assertEquals(2, response.contacts[0].unreadCount)
    }
}
