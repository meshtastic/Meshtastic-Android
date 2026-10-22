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
package org.meshtastic.feature.widget

import android.app.Application
import androidx.datastore.preferences.core.mutablePreferencesOf
import org.junit.runner.RunWith
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.Node
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Robolectric because distance formatting goes through the platform's ICU unit formatter. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class NodeTrackerWidgetStateTest {

    private val ourNode = testNode(OUR_NUM, latitudeI = 476_000_000, longitudeI = -1_223_000_000)
    private val tracked =
        testNode(
            TRACKED_NUM,
            lastHeard = HEARD,
            battery = 76,
            snr = 9.5f,
            rssi = -85,
            latitudeI = 476_100_000,
            longitudeI = -1_223_000_000,
        )

    private fun inputs(vararg extra: Pair<Int, Node>, connected: Boolean = true) = NodeTrackerInputs(
        nodes = mapOf(OUR_NUM to ourNode, TRACKED_NUM to tracked) + extra,
        ourNode = ourNode,
        connectionState = if (connected) ConnectionState.Connected else ConnectionState.Disconnected,
    )

    @Test
    fun `no configured node or one missing from the node DB is not found`() {
        assertEquals(NodeTrackerWidgetState.NodeNotFound, nodeTrackerState(null, inputs()))
        assertEquals(NodeTrackerWidgetState.NodeNotFound, nodeTrackerState(0x999, inputs()))
    }

    @Test
    fun `a tracked node carries its readings`() {
        val state = assertIs<NodeTrackerWidgetState.Tracking>(nodeTrackerState(TRACKED_NUM, inputs(), HEARD - 1))

        assertEquals("N$TRACKED_NUM", state.shortName)
        assertEquals(76, state.batteryLevel)
        assertEquals(9.5f, state.snr)
        assertEquals(-85, state.rssi)
        assertEquals(0, state.hopsAway)
        assertFalse(state.isStale)
    }

    @Test
    fun `a node last heard before the online window is stale`() {
        val state = assertIs<NodeTrackerWidgetState.Tracking>(nodeTrackerState(TRACKED_NUM, inputs(), HEARD))

        assertTrue(state.isStale)
    }

    @Test
    fun `distance shows only while our radio is connected`() {
        val connected = assertIs<NodeTrackerWidgetState.Tracking>(nodeTrackerState(TRACKED_NUM, inputs()))
        val disconnected =
            assertIs<NodeTrackerWidgetState.Tracking>(nodeTrackerState(TRACKED_NUM, inputs(connected = false)))

        assertNotNull(connected.distance)
        assertNull(disconnected.distance)
    }

    @Test
    fun `signal is shown only for a direct node heard over RF`() {
        val relayed = testNode(0x31, hopsAway = 2, snr = 4f, rssi = -100)
        val mqtt = testNode(0x32, hopsAway = 0, snr = 4f, rssi = -100, viaMqtt = true)
        val nodes = inputs(0x31 to relayed, 0x32 to mqtt)

        val relayedState = assertIs<NodeTrackerWidgetState.Tracking>(nodeTrackerState(0x31, nodes))
        val mqttState = assertIs<NodeTrackerWidgetState.Tracking>(nodeTrackerState(0x32, nodes))

        assertNull(relayedState.snr)
        assertNull(relayedState.rssi)
        assertEquals(2, relayedState.hopsAway)
        assertNull(mqttState.snr)
    }

    @Test
    fun `missing battery and unknown hops are absent rather than zero`() {
        val bare = testNode(0x41, hopsAway = -1)
        val state = assertIs<NodeTrackerWidgetState.Tracking>(nodeTrackerState(0x41, inputs(0x41 to bare)))

        assertNull(state.batteryLevel)
        assertNull(state.hopsAway)
        assertNull(state.distance)
    }

    @Test
    fun `an empty node DB keeps the last reading on screen dimmed`() {
        val live = assertIs<NodeTrackerWidgetState.Tracking>(nodeTrackerState(TRACKED_NUM, inputs(), HEARD - 1))
        val noDb = NodeTrackerInputs(connectionState = ConnectionState.Disconnected)

        val state =
            assertIs<NodeTrackerWidgetState.Tracking>(
                nodeTrackerState(TRACKED_NUM, noDb, HEARD - 1, lastKnown = live.asLastKnown()),
            )

        assertEquals(live.copy(distance = null, isStale = true), state)
        assertTrue(state.isStale)
        assertNull(state.distance)
    }

    @Test
    fun `an empty node DB without a reading for this node is not found`() {
        val other = assertIs<NodeTrackerWidgetState.Tracking>(nodeTrackerState(OUR_NUM, inputs())).asLastKnown()
        val noDb = NodeTrackerInputs()

        assertEquals(NodeTrackerWidgetState.NodeNotFound, nodeTrackerState(TRACKED_NUM, noDb))
        assertEquals(NodeTrackerWidgetState.NodeNotFound, nodeTrackerState(TRACKED_NUM, noDb, lastKnown = other))
    }

    @Test
    fun `a populated node DB without the node is not found despite a last reading`() {
        val live = assertIs<NodeTrackerWidgetState.Tracking>(nodeTrackerState(TRACKED_NUM, inputs()))
        val otherRadio = NodeTrackerInputs(nodes = mapOf(OUR_NUM to ourNode), ourNode = ourNode)

        assertEquals(
            NodeTrackerWidgetState.NodeNotFound,
            nodeTrackerState(TRACKED_NUM, otherRadio, lastKnown = live.asLastKnown()),
        )
    }

    @Test
    fun `the last reading survives a round trip through Glance state`() {
        val live = assertIs<NodeTrackerWidgetState.Tracking>(nodeTrackerState(TRACKED_NUM, inputs(), HEARD - 1))
        val bareNode = testNode(0x41, hopsAway = -1)
        val bare = assertIs<NodeTrackerWidgetState.Tracking>(nodeTrackerState(0x41, inputs(0x41 to bareNode)))
        val prefs = mutablePreferencesOf()

        assertNull(prefs.lastKnownTracking())
        prefs.storeLastKnown(live.asLastKnown())
        assertEquals(live.asLastKnown(), prefs.lastKnownTracking())
        prefs.storeLastKnown(bare.asLastKnown())
        assertEquals(bare.asLastKnown(), prefs.lastKnownTracking())
    }

    private companion object {
        const val OUR_NUM = 0x10
        const val TRACKED_NUM = 0x20
        const val HEARD = 1_700_000_000
    }
}
