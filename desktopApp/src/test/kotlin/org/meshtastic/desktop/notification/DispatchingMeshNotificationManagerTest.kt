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
package org.meshtastic.desktop.notification

import kotlinx.coroutines.test.runTest
import org.meshtastic.core.model.Node
import org.meshtastic.core.navigation.NotificationDeepLinks
import org.meshtastic.core.repository.Notification
import org.meshtastic.core.repository.NotificationManager
import org.meshtastic.core.repository.notificationId
import org.meshtastic.core.service.DispatchingMeshNotificationManager
import org.meshtastic.proto.ClientNotification
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Desktop and web share this manager; it runs here because its strings need the skiko runtime only this module has. */
class DispatchingMeshNotificationManagerTest {

    private class FakeNotificationManager(var accepts: Boolean = true) : NotificationManager {
        val dispatched = mutableListOf<Notification>()
        val cancelled = mutableListOf<String>()

        override suspend fun dispatch(notification: Notification): Boolean {
            if (accepts) dispatched.add(notification)
            return accepts
        }

        override fun cancel(category: Notification.Category, id: Int) {
            cancelled.add(Notification.tagFor(category, id))
        }

        override fun cancelAll() {}
    }

    private val notificationManager = FakeNotificationManager()
    private val manager = DispatchingMeshNotificationManager(notificationManager)

    @Test
    fun `reactions and waypoints dispatch in their own categories and slots`() = runTest {
        manager.updateReactionNotification("c", "Ann", "+1", isBroadcast = false, channelName = null, isSilent = false)
        manager.updateWaypointNotification("c", "Ann", "Camp", waypointId = 3, isSilent = false)

        assertEquals(
            listOf(Notification.Category.Reaction, Notification.Category.Waypoint),
            notificationManager.dispatched.map { it.category },
        )
        assertNotEquals(notificationManager.dispatched[0].tag, notificationManager.dispatched[1].tag)
    }

    @Test
    fun `each notification opens the destination Android opens`() = runTest {
        manager.updateMessageNotification("0!abcd", "Ann", "hi", isBroadcast = false, channelName = null)
        manager.updateReactionNotification("0!abcd", "Ann", "+1", isBroadcast = false, channelName = null)
        manager.showAlertNotification(contactKey = "0!abcd", name = "Ann", alert = "bell")
        manager.updateWaypointNotification("0!abcd", "Ann", "Camp", waypointId = 3)
        manager.showNewNodeSeenNotification(Node(num = 7), title = "New node")
        manager.notifyLowBattery(Node(num = 7), isRemote = true)

        assertEquals(
            listOf(
                NotificationDeepLinks.conversation("0!abcd"),
                NotificationDeepLinks.conversation("0!abcd"),
                NotificationDeepLinks.conversation("0!abcd"),
                NotificationDeepLinks.waypoint(3),
                NotificationDeepLinks.nodeDetail(7),
                NotificationDeepLinks.nodeDetail(7),
            ),
            notificationManager.dispatched.map { it.deepLink },
        )
    }

    @Test
    fun `critical alerts dispatch in the alert category`() = runTest {
        manager.showAlertNotification(contactKey = "contact-1", name = "Alert", alert = "Something happened")

        val dispatched = notificationManager.dispatched.single()
        assertEquals("Alert", dispatched.title)
        assertEquals("Something happened", dispatched.message)
        assertEquals(Notification.Category.Alert, dispatched.category)
    }

    @Test
    fun `client notifications keep their title and severity under a stable id`() = runTest {
        val clientNotification = ClientNotification.Builder().also { wb -> wb.message = "Duplicate key" }.build()

        manager.showClientNotification(clientNotification, title = "Key conflict", severity = Notification.Type.Warning)
        manager.clearClientNotification(clientNotification)

        val dispatched = notificationManager.dispatched.single()
        assertEquals("Key conflict", dispatched.title)
        assertEquals(Notification.Type.Warning, dispatched.type)
        assertEquals(Notification.Category.Client, dispatched.category)
        assertEquals(clientNotification.notificationId(), dispatched.id)
        assertFalse(dispatched.onlyAlertOnce)
        assertEquals(listOf(dispatched.tag), notificationManager.cancelled)
    }

    @Test
    fun `a low-battery warning shows once per episode`() = runTest {
        manager.notifyLowBattery(Node(num = 7), isRemote = false)
        manager.notifyLowBattery(Node(num = 7), isRemote = false)
        assertEquals(1, notificationManager.dispatched.size)

        manager.cancelLowBatteryNotification(7)
        manager.notifyLowBattery(Node(num = 7), isRemote = false)
        assertEquals(2, notificationManager.dispatched.size)

        manager.clearNotifications()
        manager.notifyLowBattery(Node(num = 7), isRemote = false)
        assertEquals(3, notificationManager.dispatched.size)
    }

    @Test
    fun `a low-battery warning that was not shown is tried again`() = runTest {
        notificationManager.accepts = false
        manager.notifyLowBattery(Node(num = 7), isRemote = false)

        notificationManager.accepts = true
        manager.notifyLowBattery(Node(num = 7), isRemote = false)

        assertEquals(1, notificationManager.dispatched.size)
    }

    @Test
    fun `cancelling a new-node notice leaves the same node's low-battery warning`() = runTest {
        manager.showNewNodeSeenNotification(Node(num = 7), title = "New node seen: N7")
        manager.cancelNewNodeNotification(7)

        assertEquals(7, notificationManager.dispatched.single().id)
        assertEquals("New node seen: N7", notificationManager.dispatched.single().title)
        assertEquals(listOf(Notification.tagFor(Notification.Category.NodeEvent, 7)), notificationManager.cancelled)
        assertNotEquals(
            Notification.tagFor(Notification.Category.Battery, 7),
            Notification.tagFor(Notification.Category.NodeEvent, 7),
        )
    }

    @Test
    fun `dismissing a conversation clears its message and reaction`() = runTest {
        manager.cancelMessageNotification("c")

        assertEquals(
            listOf(
                Notification.tagFor(Notification.Category.Message, "c".hashCode()),
                Notification.tagFor(Notification.Category.Reaction, "c".hashCode()),
            ),
            notificationManager.cancelled,
        )
    }

    @Test
    fun `reconnect-blocked is never shown`() = runTest {
        assertFalse(manager.showReconnectBlockedNotification("title", "message"))
        assertTrue(notificationManager.dispatched.isEmpty())
    }
}
