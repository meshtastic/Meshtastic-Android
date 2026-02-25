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
package com.geeksville.mesh.widget

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.meshtastic.core.data.repository.NodeRepository
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.model.util.onlineTimeThreshold
import org.meshtastic.core.resources.getStringSuspend
import org.meshtastic.core.service.ConnectionState
import org.meshtastic.core.service.ServiceRepository
import org.meshtastic.proto.DeviceMetrics
import org.meshtastic.proto.LocalStats
import org.meshtastic.proto.User
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class LocalStatsWidgetStateProviderTest {

    private val connectionStateFlow = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    private val nodeDbFlow = MutableStateFlow<Map<Int, Node>>(emptyMap())
    private val localStatsFlow = MutableStateFlow(LocalStats())
    private val ourNodeInfoFlow = MutableStateFlow<Node?>(null)

    // Using mocks but with a very explicit setup to avoid Kover interference
    private lateinit var nodeRepository: NodeRepository
    private lateinit var serviceRepository: ServiceRepository

    @Before
    fun setUp() {
        serviceRepository = mockk(relaxed = true)
        nodeRepository = mockk(relaxed = true)

        every { serviceRepository.connectionState } returns connectionStateFlow
        every { nodeRepository.nodeDBbyNum } returns nodeDbFlow
        every { nodeRepository.localStats } returns localStatsFlow
        every { nodeRepository.ourNodeInfo } returns ourNodeInfoFlow

        mockkStatic("org.meshtastic.core.resources.ContextExtKt")
        mockkStatic("org.meshtastic.core.model.util.TimeUtilsKt")

        coEvery { getStringSuspend(any()) } returns "Mock String"
        coEvery { getStringSuspend(any(), *anyVararg()) } returns "Mock Formatted String"
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `initial state reflects disconnected status`() = runTest {
        val provider = LocalStatsWidgetStateProvider(nodeRepository, serviceRepository)
        val state = provider.state.first()
        assertEquals(ConnectionState.Disconnected, state.connectionState)
        assertFalse(state.showContent)
    }

    @Test
    fun `connected state shows content and maps node info`() = runTest {
        connectionStateFlow.value = ConnectionState.Connected
        ourNodeInfoFlow.value =
            Node(
                num = 123,
                user = User(short_name = "ABC"),
                deviceMetrics = DeviceMetrics(battery_level = 85, channel_utilization = 12.5f),
            )

        val provider = LocalStatsWidgetStateProvider(nodeRepository, serviceRepository)
        val state =
            provider.state.first { (it.connectionState == ConnectionState.Connected) && (it.nodeShortName == "ABC") }

        assertTrue(state.showContent)
        assertEquals("ABC", state.nodeShortName)
        assertEquals("85%", state.batteryValue)
    }

    @Test
    fun `node count and update timestamp are populated`() = runTest {
        connectionStateFlow.value = ConnectionState.Connected
        nodeDbFlow.value = mapOf(1 to Node(num = 1, lastHeard = 1000))
        every { onlineTimeThreshold() } returns 0

        val provider = LocalStatsWidgetStateProvider(nodeRepository, serviceRepository)
        val state = provider.state.first { it.nodeCountText == "1/1" }

        assertEquals("1/1", state.nodeCountText)
        assertEquals("Mock Formatted String", state.updatedText)
    }
}
