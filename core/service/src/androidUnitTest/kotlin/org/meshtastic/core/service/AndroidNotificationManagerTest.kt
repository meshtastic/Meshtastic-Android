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
package org.meshtastic.core.service

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Test
import org.meshtastic.core.repository.Notification
import org.meshtastic.core.repository.NotificationPrefs
import android.app.NotificationManager as SystemNotificationManager

class AndroidNotificationManagerTest {

    private lateinit var context: Context
    private lateinit var notificationManager: SystemNotificationManager
    private lateinit var prefs: NotificationPrefs
    private lateinit var androidNotificationManager: AndroidNotificationManager

    private val messagesEnabled = MutableStateFlow(true)
    private val nodeEventsEnabled = MutableStateFlow(true)
    private val lowBatteryEnabled = MutableStateFlow(true)

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        notificationManager = mockk(relaxed = true)
        prefs = mockk {
            every { messagesEnabled } returns this@AndroidNotificationManagerTest.messagesEnabled
            every { nodeEventsEnabled } returns this@AndroidNotificationManagerTest.nodeEventsEnabled
            every { lowBatteryEnabled } returns this@AndroidNotificationManagerTest.lowBatteryEnabled
        }

        every { context.getSystemService(Context.NOTIFICATION_SERVICE) } returns notificationManager
        every { context.packageName } returns "org.meshtastic.test"

        // Mocking initChannels to avoid getString calls during initialization for now if possible
        // but it's called in init block.
        androidNotificationManager = AndroidNotificationManager(context, prefs)
    }

    @Test
    fun `dispatch notifies when enabled`() {
        val notification = Notification("Title", "Message", category = Notification.Category.Message)

        androidNotificationManager.dispatch(notification)

        verify { notificationManager.notify(any(), any()) }
    }

    @Test
    fun `dispatch does not notify when disabled`() {
        messagesEnabled.value = false
        val notification = Notification("Title", "Message", category = Notification.Category.Message)

        androidNotificationManager.dispatch(notification)

        verify(exactly = 0) { notificationManager.notify(any(), any()) }
    }
}
