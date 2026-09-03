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
package org.meshtastic.core.ui.component

import org.meshtastic.core.navigation.NodeDetailRoute
import org.meshtastic.core.navigation.NodesRoute
import org.meshtastic.core.repository.DataPair
import org.meshtastic.core.repository.PlatformAnalytics
import kotlin.test.Test
import kotlin.test.assertEquals

class ScreenViewTrackerTest {

    @Test
    fun `switching screens stops the old view before starting the new one`() {
        val analytics = RecordingPlatformAnalytics()
        val tracker = ScreenViewTracker(analytics)

        tracker.onCurrentKeyChanged(NodesRoute.Nodes)
        tracker.onCurrentKeyChanged(NodeDetailRoute.DeviceMetrics(destNum = 7))

        assertEquals(
            listOf(
                "start:org.meshtastic.core.navigation.NodesRoute.Nodes",
                "stop:org.meshtastic.core.navigation.NodesRoute.Nodes",
                "start:org.meshtastic.core.navigation.NodeDetailRoute.DeviceMetrics",
            ),
            analytics.events,
        )
    }

    @Test
    fun `disposing stops the active view once`() {
        val analytics = RecordingPlatformAnalytics()
        val tracker = ScreenViewTracker(analytics)

        tracker.onCurrentKeyChanged(NodesRoute.Nodes)
        tracker.dispose()
        tracker.dispose()

        assertEquals(
            listOf(
                "start:org.meshtastic.core.navigation.NodesRoute.Nodes",
                "stop:org.meshtastic.core.navigation.NodesRoute.Nodes",
            ),
            analytics.events,
        )
    }

    @Test
    fun `re-emitting the same route does not duplicate datadog view events`() {
        val analytics = RecordingPlatformAnalytics()
        val tracker = ScreenViewTracker(analytics)

        tracker.onCurrentKeyChanged(NodeDetailRoute.DeviceMetrics(destNum = 1))
        tracker.onCurrentKeyChanged(NodeDetailRoute.DeviceMetrics(destNum = 2))

        assertEquals(
            listOf("start:org.meshtastic.core.navigation.NodeDetailRoute.DeviceMetrics"),
            analytics.events,
        )
    }
}

private class RecordingPlatformAnalytics : PlatformAnalytics {
    val events = mutableListOf<String>()

    override fun track(event: String, vararg properties: DataPair) = Unit

    override fun setDeviceAttributes(firmwareVersion: String, model: String) = Unit

    override fun startScreenView(key: String, name: String) {
        events += "start:$key"
    }

    override fun stopScreenView(key: String) {
        events += "stop:$key"
    }

    override val isPlatformServicesAvailable: Boolean = true
}
