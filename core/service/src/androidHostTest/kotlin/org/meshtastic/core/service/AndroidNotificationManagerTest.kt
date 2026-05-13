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

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.meshtastic.core.repository.Notification
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AndroidNotificationManagerTest {

    private lateinit var context: Context
    private lateinit var systemNotificationManager: NotificationManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        systemNotificationManager = context.getSystemService(NotificationManager::class.java)!!
        clearManagedChannels()
        systemNotificationManager.cancelAll()
    }

    @After
    fun tearDown() {
        clearManagedChannels()
        systemNotificationManager.cancelAll()
    }

    @Test
    fun `removeLegacyCategoryChannels deletes legacy channels and keeps canonical channels`() {
        createChannel("NodeEvent")
        createChannel(NotificationChannels.NEW_NODES)

        systemNotificationManager.removeLegacyCategoryChannels()

        assertNull(systemNotificationManager.getNotificationChannel("NodeEvent"))
        assertNotNull(systemNotificationManager.getNotificationChannel(NotificationChannels.NEW_NODES))
    }

    @Test
    fun `dispatch removes legacy node channel and creates canonical node channel`() {
        createChannel("NodeEvent")

        val manager = AndroidNotificationManager(context)
        manager.dispatch(Notification(title = "Node", message = "Seen", category = Notification.Category.NodeEvent))

        assertNull(systemNotificationManager.getNotificationChannel("NodeEvent"))
        assertNotNull(systemNotificationManager.getNotificationChannel(NotificationChannels.NEW_NODES))
    }

    @Test
    fun `dispatch routes node event notifications to canonical new nodes channel`() {
        val manager = AndroidNotificationManager(context)

        manager.dispatch(Notification(title = "Node", message = "Seen", category = Notification.Category.NodeEvent))

        val posted = shadowOf(systemNotificationManager).allNotifications.last()
        assertEquals(NotificationChannels.NEW_NODES, posted.channelId)
    }

    @Test
    fun `removeLegacyCategoryChannels removes all known legacy category channels`() {
        NotificationChannels.LEGACY_CATEGORY_IDS.forEach(::createChannel)

        systemNotificationManager.removeLegacyCategoryChannels()

        NotificationChannels.LEGACY_CATEGORY_IDS.forEach { legacyId ->
            assertNull(systemNotificationManager.getNotificationChannel(legacyId))
        }
    }

    @Test
    fun `removeLegacyCategoryChannels is idempotent`() {
        createChannel("NodeEvent")

        systemNotificationManager.removeLegacyCategoryChannels()
        systemNotificationManager.removeLegacyCategoryChannels()

        assertNull(systemNotificationManager.getNotificationChannel("NodeEvent"))
    }

    @Test
    fun `dispatch routes all categories to canonical channels`() {
        val manager = AndroidNotificationManager(context)

        assertDispatchesToChannel(manager, Notification.Category.Message, NotificationChannels.MESSAGES)
        assertDispatchesToChannel(manager, Notification.Category.NodeEvent, NotificationChannels.NEW_NODES)
        assertDispatchesToChannel(manager, Notification.Category.Battery, NotificationChannels.LOW_BATTERY)
        assertDispatchesToChannel(manager, Notification.Category.Alert, NotificationChannels.ALERTS)
        assertDispatchesToChannel(manager, Notification.Category.Service, NotificationChannels.SERVICE)
    }

    private fun assertDispatchesToChannel(
        manager: AndroidNotificationManager,
        category: Notification.Category,
        expectedChannelId: String,
    ) {
        systemNotificationManager.cancelAll()
        manager.dispatch(
            Notification(title = "Title-${category.name}", message = "Message-${category.name}", category = category),
        )

        val posted = shadowOf(systemNotificationManager).allNotifications.last()
        assertEquals(expectedChannelId, posted.channelId)
    }

    private fun createChannel(id: String) {
        systemNotificationManager.createNotificationChannel(
            NotificationChannel(id, id, NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    private fun clearManagedChannels() {
        val channelIds =
            NotificationChannels.LEGACY_CATEGORY_IDS +
                listOf(
                    NotificationChannels.SERVICE,
                    NotificationChannels.MESSAGES,
                    NotificationChannels.BROADCASTS,
                    NotificationChannels.WAYPOINTS,
                    NotificationChannels.ALERTS,
                    NotificationChannels.NEW_NODES,
                    NotificationChannels.LOW_BATTERY,
                    NotificationChannels.LOW_BATTERY_REMOTE,
                    NotificationChannels.CLIENT,
                )

        channelIds.forEach { channelId -> systemNotificationManager.deleteNotificationChannel(channelId) }
    }
}
