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
package org.meshtastic.core.data.manager

import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode.Companion.exactly
import dev.mokkery.verifySuspend
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.Node
import org.meshtastic.core.repository.MeshNotificationManager
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.NotificationPrefs
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.proto.Position
import org.meshtastic.proto.Waypoint
import kotlin.test.Test

/**
 * Covers the crossing-decision logic deterministically via virtual time. The positive notification *dispatch* (R9) is
 * NOT asserted here: it is gated behind `getStringSuspend` (compose-resources), which does not resolve in the plain-JVM
 * test runner, so any test reaching it hangs. Coverage is instead structured around negatives that still drive the full
 * decision path right up to the notify entry point:
 * - [favoritesOnlySuppressesNonFavoriteCrossing] drives a real outside→inside ENTER and is stopped only by the gate,
 *   proving the snapshot/worker/store/enter-branch all work.
 * - [enterOnlyWaypointDoesNotNotifyOnExit] proves the EXIT branch is gated by `notify_on_exit`. The actual dispatch
 *   (contactKey/channel/deep-link) is verified by code review + manual run.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GeofenceMonitorTest {

    private val sender = 9
    private val myNodeNum = 1

    // Geofence centred at (10, 20), 1 km radius.
    private val centerLatI = 100_000_000
    private val centerLonI = 200_000_000
    private val inside = Position(latitude_i = centerLatI, longitude_i = centerLonI) // distance 0
    private val outside = Position(latitude_i = 110_000_000, longitude_i = centerLonI) // ~111 km north

    private fun waypoint(enter: Boolean = true, exit: Boolean = false, favoritesOnly: Boolean = false) = Waypoint(
        id = 42,
        latitude_i = centerLatI,
        longitude_i = centerLonI,
        name = "Base",
        geofence_radius = 1000,
        notify_on_enter = enter,
        notify_on_exit = exit,
        notify_favorites_only = favoritesOnly,
    )

    private data class Mocks(
        val packetRepository: PacketRepository,
        val nodeManager: NodeManager,
        val notifications: MeshNotificationManager,
        val notificationPrefs: NotificationPrefs,
    )

    private fun mocks(
        wp: Waypoint,
        senderIsFavorite: Boolean = false,
        createdByLocal: Boolean = true,
        optedInIds: Set<Int> = emptySet(),
    ): Mocks {
        val packetRepository: PacketRepository = mock(MockMode.autofill)
        val nodeManager: NodeManager = mock(MockMode.autofill)
        val notifications: MeshNotificationManager = mock(MockMode.autofill)
        val notificationPrefs: NotificationPrefs = mock(MockMode.autofill)
        // from defaults to ^local (locally created); a remote creator uses another node's hex id.
        val packet =
            DataPacket(to = "!abcdabcd", channel = 0, waypoint = wp).also {
                if (!createdByLocal) it.from = "!0000000$sender"
            }
        every { packetRepository.getWaypoints() } returns flowOf(listOf(packet))
        every { nodeManager.myNodeNum } returns MutableStateFlow(myNodeNum)
        every { nodeManager.nodeDBbyNodeNum } returns mapOf(sender to Node(num = sender, isFavorite = senderIsFavorite))
        every { notificationPrefs.geofenceAlertOptIns } returns MutableStateFlow(optedInIds)
        return Mocks(packetRepository, nodeManager, notifications, notificationPrefs)
    }

    private fun expectNoNotification(
        wp: Waypoint,
        senderIsFavorite: Boolean = false,
        createdByLocal: Boolean = true,
        optedInIds: Set<Int> = emptySet(),
        drive: (GeofenceMonitor) -> Unit,
    ) = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val m = mocks(wp, senderIsFavorite, createdByLocal, optedInIds)
        val monitor =
            GeofenceMonitor(
                lazy { m.packetRepository },
                m.nodeManager,
                m.notifications,
                GeofenceCrossingStore(),
                m.notificationPrefs,
                scope,
            )
        scope.advanceUntilIdle() // collect the active-geofence snapshot

        drive(monitor)
        scope.advanceUntilIdle()

        verifySuspend(exactly(0)) { m.notifications.updateWaypointNotification(any(), any(), any(), any(), any()) }
        scope.cancel() // stop the serial worker so runTest sees no leaked coroutine
    }

    @Test
    fun firstSightingInsideDoesNotNotify() =
        expectNoNotification(waypoint()) { m -> m.onPositionReceived(sender, myNodeNum, inside) }

    @Test
    fun favoritesOnlySuppressesNonFavoriteCrossing() =
        expectNoNotification(waypoint(favoritesOnly = true), senderIsFavorite = false) { m ->
            m.onPositionReceived(sender, myNodeNum, outside) // baseline
            m.onPositionReceived(sender, myNodeNum, inside) // genuine ENTER, but sender is not a favorite
        }

    @Test
    fun ownPositionIsNeverEvaluated() = expectNoNotification(waypoint()) { m ->
        m.onPositionReceived(myNodeNum, myNodeNum, inside) // sender == self
    }

    @Test
    fun remoteCreatedGeofenceDoesNotNotify() = expectNoNotification(waypoint(), createdByLocal = false) { m ->
        m.onPositionReceived(sender, myNodeNum, outside) // baseline
        m.onPositionReceived(sender, myNodeNum, inside) // genuine ENTER, but we didn't create this geofence
    }

    @Test
    fun enterOnlyWaypointDoesNotNotifyOnExit() = expectNoNotification(waypoint(enter = true, exit = false)) { m ->
        m.onPositionReceived(sender, myNodeNum, inside) // baseline inside
        m.onPositionReceived(sender, myNodeNum, outside) // EXIT, but notify_on_exit is false
    }
}
