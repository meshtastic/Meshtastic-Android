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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.meshtastic.core.repository.Notification
import org.meshtastic.core.repository.NotificationManager
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopMeshNotificationManagerTest {

    /** Records everything dispatched so the async bridge can be asserted deterministically. */
    private class FakeNotificationManager : NotificationManager {
        val dispatched = mutableListOf<Notification>()

        override suspend fun dispatch(notification: Notification): Boolean {
            dispatched.add(notification)
            return true
        }

        override fun cancel(id: Int) {}

        override fun cancelAll() {}
    }

    @Test
    fun `showAlertNotification dispatches on the injected scope`() {
        val notificationManager = FakeNotificationManager()
        // UnconfinedTestDispatcher runs the launched dispatch eagerly, so the fire-and-forget bridge is observable
        // synchronously — no virtual-time advance needed.
        val scope = CoroutineScope(UnconfinedTestDispatcher())
        val manager = DesktopMeshNotificationManager(notificationManager, scope = scope)

        manager.showAlertNotification(contactKey = "contact-1", name = "Alert", alert = "Something happened")

        val dispatched = notificationManager.dispatched.single()
        assertEquals("Alert", dispatched.title)
        assertEquals("Something happened", dispatched.message)
        assertEquals(Notification.Category.Alert, dispatched.category)
        assertEquals("contact-1", dispatched.contactKey)

        scope.cancel()
    }
}
