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
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.meshtastic.core.repository.Notification
import org.meshtastic.core.repository.notificationId
import org.meshtastic.proto.ClientNotification
import org.meshtastic.proto.DuplicatedPublicKey
import org.meshtastic.proto.KeyVerificationFinal
import org.meshtastic.proto.KeyVerificationNumberInform
import org.meshtastic.proto.KeyVerificationNumberRequest
import org.meshtastic.proto.LogRecord
import org.meshtastic.proto.LowEntropyKey
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
    fun `dispatch removes legacy node channel and creates canonical node channel`() = runTest {
        createChannel("NodeEvent")

        val manager = AndroidNotificationManager(context)
        manager.dispatch(Notification(title = "Node", message = "Seen", category = Notification.Category.NodeEvent))

        assertNull(systemNotificationManager.getNotificationChannel("NodeEvent"))
        assertNotNull(systemNotificationManager.getNotificationChannel(NotificationChannels.NEW_NODES))
    }

    @Test
    fun `dispatch routes node event notifications to canonical new nodes channel`() = runTest {
        val manager = AndroidNotificationManager(context)

        manager.dispatch(Notification(title = "Node", message = "Seen", category = Notification.Category.NodeEvent))

        val posted = shadowOf(systemNotificationManager).allNotifications.last()
        assertEquals(NotificationChannels.NEW_NODES, posted.channelId)
    }

    @Test
    fun `dispatch reports false when its notification channel is disabled`() = runTest {
        createChannel(NotificationChannels.NEW_NODES, NotificationManager.IMPORTANCE_NONE)
        val manager = AndroidNotificationManager(context)

        val dispatched =
            manager.dispatch(Notification(title = "Node", message = "Seen", category = Notification.Category.NodeEvent))

        assertFalse(dispatched)
        assertEquals(0, shadowOf(systemNotificationManager).allNotifications.size)
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
    fun `dispatch routes all categories to canonical channels`() = runTest {
        val manager = AndroidNotificationManager(context)

        assertDispatchesToChannel(manager, Notification.Category.Message, NotificationChannels.MESSAGES)
        assertDispatchesToChannel(manager, Notification.Category.NodeEvent, NotificationChannels.NEW_NODES)
        assertDispatchesToChannel(manager, Notification.Category.Battery, NotificationChannels.LOW_BATTERY)
        assertDispatchesToChannel(manager, Notification.Category.Alert, NotificationChannels.ALERTS)
        assertDispatchesToChannel(manager, Notification.Category.Service, NotificationChannels.SERVICE)
    }

    @Test
    fun `dispatch attaches deep-link PendingIntent when deepLinkUri is set`() = runTest {
        registerStubMainActivity()
        val manager = AndroidNotificationManager(context)
        val deepLink = "meshtastic://meshtastic/nodes/1234"

        manager.dispatch(
            Notification(
                title = "New node",
                message = "Long Name",
                category = Notification.Category.NodeEvent,
                id = 1234,
                deepLinkUri = deepLink,
            ),
        )

        val posted = shadowOf(systemNotificationManager).allNotifications.last()
        val pendingIntent =
            requireNotNull(posted.contentIntent) { "Expected contentIntent to be set when deepLinkUri is provided" }
        val shadowPendingIntent = shadowOf(pendingIntent)
        val savedIntent = shadowPendingIntent.savedIntent
        assertEquals(android.content.Intent.ACTION_VIEW, savedIntent.action)
        assertEquals(deepLink, savedIntent.data?.toString())
        assertEquals("org.meshtastic.app.MainActivity", savedIntent.component?.className)
    }

    @Test
    fun `dispatch leaves contentIntent unset when deepLinkUri is null`() = runTest {
        val manager = AndroidNotificationManager(context)

        manager.dispatch(Notification(title = "Plain", message = "No tap", category = Notification.Category.NodeEvent))

        val posted = shadowOf(systemNotificationManager).allNotifications.last()
        assertNull(posted.contentIntent)
    }

    @Test
    fun `dispatch uses provided notification id as system id`() = runTest {
        val manager = AndroidNotificationManager(context)
        val explicitId = 4242

        manager.dispatch(
            Notification(
                title = "With id",
                message = "explicit",
                category = Notification.Category.NodeEvent,
                id = explicitId,
            ),
        )

        // Cancellation by the same id should remove the posted notification.
        assertEquals(1, shadowOf(systemNotificationManager).allNotifications.size)
        manager.cancel(explicitId)
        assertEquals(0, shadowOf(systemNotificationManager).allNotifications.size)
    }

    @Test
    fun `client notification identity cancels the notification posted with that identity`() = runTest {
        val manager = AndroidNotificationManager(context)
        val clientNotification = ClientNotification(message = "Protected position advisory", reply_id = 123)
        val id = clientNotification.notificationId()

        manager.dispatch(
            Notification(
                title = "Client notification",
                message = clientNotification.message,
                category = Notification.Category.Alert,
                id = id,
            ),
        )

        assertEquals(1, shadowOf(systemNotificationManager).allNotifications.size)
        manager.cancel(clientNotification.notificationId())
        assertEquals(0, shadowOf(systemNotificationManager).allNotifications.size)
    }

    @Test
    fun `exact protected position advisory is recognized`() {
        val manager = AndroidNotificationManager(context)

        assertTrue(manager.suppressClientNotificationModal(protectedPositionAdvisory(replyId = 123, time = 1_000)))
    }

    @Test
    fun `protected position advisory predicate rejects every near miss`() {
        val manager = AndroidNotificationManager(context)
        val advisory = protectedPositionAdvisory(replyId = 123, time = 1_000)
        val nearMisses =
            listOf(
                advisory.copy(message = "Location sharing is disabled"),
                advisory.copy(level = LogRecord.Level.INFO),
                advisory.copy(reply_id = 0),
                advisory.copy(reply_id = null),
                advisory.copy(key_verification_number_inform = KeyVerificationNumberInform()),
                advisory.copy(key_verification_number_request = KeyVerificationNumberRequest()),
                advisory.copy(key_verification_final = KeyVerificationFinal()),
                advisory.copy(duplicated_public_key = DuplicatedPublicKey()),
                advisory.copy(low_entropy_key = LowEntropyKey()),
                advisory.copy(message = "Rebooting to WiFi OTA"),
            )

        nearMisses.forEach { notification ->
            assertFalse(manager.suppressClientNotificationModal(notification), notification.toString())
        }
    }

    @Test
    fun `protected position advisories replace one stable only-alert-once system notification`() = runTest {
        val manager = AndroidNotificationManager(context)
        val first = protectedPositionAdvisory(replyId = 123, time = 1_000)
        val second = protectedPositionAdvisory(replyId = 456, time = 2_000)

        listOf(first, second).forEach { advisory ->
            manager.dispatchClientNotification(
                Notification(
                    title = "Client notification",
                    message = advisory.message,
                    category = Notification.Category.Alert,
                    id = advisory.notificationId(),
                ),
                advisory,
            )
        }

        val posted = shadowOf(systemNotificationManager).allNotifications.single()
        assertTrue(posted.flags and android.app.Notification.FLAG_ONLY_ALERT_ONCE != 0)
        val active = systemNotificationManager.activeNotifications.single()
        assertEquals(first.notificationId(), active.id)
    }

    @Test
    fun `repeated generic client notifications with the same message coalesce into one tray entry`() = runTest {
        val manager = AndroidNotificationManager(context)
        // A near-miss of the protected-position predicate (message differs slightly), so it takes the plain
        // dispatch path — but reply_id/time still change on every firmware reply, exactly like the real advisory.
        val first = ClientNotification(message = "Location sharing is disabled", reply_id = 100, time = 1_000)
        val second = ClientNotification(message = "Location sharing is disabled", reply_id = 200, time = 2_000)

        listOf(first, second).forEach { cn ->
            manager.dispatchClientNotification(
                Notification(
                    title = "Client notification",
                    message = cn.message,
                    category = Notification.Category.Alert,
                    id = cn.notificationId(),
                ),
                cn,
            )
        }

        assertEquals(1, shadowOf(systemNotificationManager).allNotifications.size)
    }

    @Test
    fun `generic client notification does not enable only-alert-once`() = runTest {
        val manager = AndroidNotificationManager(context)
        val clientNotification = ClientNotification(message = "Generic warning", reply_id = 123)

        manager.dispatchClientNotification(
            Notification(
                title = "Client notification",
                message = clientNotification.message,
                category = Notification.Category.Alert,
                id = clientNotification.notificationId(),
            ),
            clientNotification,
        )

        val posted = shadowOf(systemNotificationManager).allNotifications.single()
        assertEquals(0, posted.flags and android.app.Notification.FLAG_ONLY_ALERT_ONCE)
    }

    private suspend fun assertDispatchesToChannel(
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

    private fun createChannel(id: String, importance: Int = NotificationManager.IMPORTANCE_DEFAULT) {
        systemNotificationManager.createNotificationChannel(NotificationChannel(id, id, importance))
    }

    /**
     * Registers a stub `org.meshtastic.app.MainActivity` with the Robolectric `PackageManager` so that
     * `TaskStackBuilder.addNextIntentWithParentStack` does not throw `NameNotFoundException` when resolving the
     * activity that hosts deep-link intents. The real activity lives in `:androidApp`, which is intentionally not on
     * `:core:service`'s test classpath.
     */
    private fun registerStubMainActivity() {
        val componentName = android.content.ComponentName(context, "org.meshtastic.app.MainActivity")
        val activityInfo =
            android.content.pm.ActivityInfo().apply {
                name = componentName.className
                packageName = componentName.packageName
                exported = true
            }
        shadowOf(context.packageManager).addOrUpdateActivity(activityInfo)
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

    private fun protectedPositionAdvisory(replyId: Int, time: Int) = ClientNotification(
        message = "Location sharing is disabled on this channel",
        reply_id = replyId,
        time = time,
        level = LogRecord.Level.WARNING,
    )
}
