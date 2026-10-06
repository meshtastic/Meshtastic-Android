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
import org.meshtastic.core.repository.Notification
import org.meshtastic.core.repository.NotificationManager
import org.meshtastic.core.repository.notificationId
import org.meshtastic.proto.ClientNotification
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopMeshNotificationManagerTest {

    private class FakeNotificationManager(var accepts: Boolean = true) : NotificationManager {
        val dispatched = mutableListOf<Notification>()
        val cancelled = mutableListOf<Int>()

        override suspend fun dispatch(notification: Notification): Boolean {
            if (accepts) dispatched.add(notification)
            return accepts
        }

        override fun cancel(id: Int) {
            cancelled.add(id)
        }

        override fun cancelAll() {}
    }

    private val notificationManager = FakeNotificationManager()
    private val manager = DesktopMeshNotificationManager(notificationManager)

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
        assertEquals(listOf(clientNotification.notificationId()), notificationManager.cancelled)
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
    fun `new-node notifications cancel by node number`() = runTest {
        manager.showNewNodeSeenNotification(Node(num = 7), title = "New node seen: N7")
        manager.cancelNewNodeNotification(7)

        assertEquals(7, notificationManager.dispatched.single().id)
        assertEquals("New node seen: N7", notificationManager.dispatched.single().title)
        assertEquals(listOf(7), notificationManager.cancelled)
    }

    @Test
    fun `reconnect-blocked is never shown on desktop`() = runTest {
        assertFalse(manager.showReconnectBlockedNotification("title", "message"))
        assertTrue(notificationManager.dispatched.isEmpty())
    }
}
