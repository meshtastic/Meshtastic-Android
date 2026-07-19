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

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.repository.Notification
import org.meshtastic.core.repository.NotificationPrefs
import org.meshtastic.desktop.DesktopNotificationManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopNotificationManagerTest {

    /** Fake [NativeNotificationSender] that records dispatched notifications and allows controlling success/failure. */
    private class FakeNativeSender(var shouldSucceed: Boolean = true) : NativeNotificationSender {
        val sent = mutableListOf<Notification>()

        override fun send(notification: Notification): Boolean {
            sent.add(notification)
            return shouldSucceed
        }
    }

    /** Simple [NotificationPrefs] with all categories enabled by default. */
    private class FakeNotificationPrefs(
        messages: Boolean = true,
        nodeEvents: Boolean = true,
        lowBattery: Boolean = true,
    ) : NotificationPrefs {
        override val messagesEnabled = MutableStateFlow(messages)
        override val nodeEventsEnabled = MutableStateFlow(nodeEvents)
        override val nodeEventsAutoDisabledForEvent = MutableStateFlow(false)
        override val lowBatteryEnabled = MutableStateFlow(lowBattery)

        override fun setMessagesEnabled(enabled: Boolean) {
            messagesEnabled.value = enabled
        }

        override fun setNodeEventsEnabled(enabled: Boolean) {
            nodeEventsEnabled.value = enabled
        }

        override fun setNodeEventsAutoDisabledForEvent(disabled: Boolean) {
            nodeEventsAutoDisabledForEvent.value = disabled
        }

        override fun setLowBatteryEnabled(enabled: Boolean) {
            lowBatteryEnabled.value = enabled
        }

        override val geofenceAlertOptIns = MutableStateFlow<Set<Int>>(emptySet())

        override fun setGeofenceAlertOptIn(waypointId: Int, enabled: Boolean) {
            geofenceAlertOptIns.value =
                geofenceAlertOptIns.value.toMutableSet().apply { if (enabled) add(waypointId) else remove(waypointId) }
        }
    }

    @Test
    fun `dispatch sends to native sender and reports success when enabled`() = runTest {
        val sender = FakeNativeSender()
        val manager = DesktopNotificationManager(FakeNotificationPrefs(), sender)

        val dispatched = manager.dispatch(Notification(title = "Test", message = "Hello"))

        assertTrue(dispatched)
        assertEquals(1, sender.sent.size)
        assertEquals("Test", sender.sent[0].title)
    }

    @Test
    fun `dispatch reports false and skips native sender when preference disabled`() = runTest {
        val sender = FakeNativeSender()
        val manager = DesktopNotificationManager(FakeNotificationPrefs(messages = false), sender)

        val dispatched =
            manager.dispatch(Notification(title = "Msg", message = "Hi", category = Notification.Category.Message))

        assertFalse(dispatched)
        assertEquals(0, sender.sent.size, "Message notification should have been suppressed")
    }

    @Test
    fun `alerts are always dispatched even when messages disabled`() = runTest {
        val sender = FakeNativeSender()
        val manager = DesktopNotificationManager(FakeNotificationPrefs(messages = false), sender)

        val dispatched =
            manager.dispatch(
                Notification(title = "Alert", message = "Important", category = Notification.Category.Alert),
            )

        assertTrue(dispatched)
        assertEquals(1, sender.sent.size)
    }

    @Test
    fun `fallback emitted and dispatch still reports accepted when native sender fails`() = runTest {
        val sender = FakeNativeSender(shouldSucceed = false)
        val manager = DesktopNotificationManager(FakeNotificationPrefs(), sender)
        var fallback: androidx.compose.ui.window.Notification? = null

        // UNDISPATCHED guarantees the collector subscribes before dispatch runs.
        val collector = launch(start = CoroutineStart.UNDISPATCHED) { fallback = manager.fallbackNotifications.first() }

        val dispatched = manager.dispatch(Notification(title = "Fallback", message = "Test"))
        collector.join()

        assertTrue(dispatched, "Tray fallback acceptance should count as delivery-accepted")
        assertNotNull(fallback, "Expected fallback notification to be emitted")
        assertEquals("Fallback", fallback!!.title)
    }

    @Test
    fun `no fallback when native sender succeeds`() = runTest {
        val sender = FakeNativeSender(shouldSucceed = true)
        val manager = DesktopNotificationManager(FakeNotificationPrefs(), sender)
        var fallback: androidx.compose.ui.window.Notification? = null

        val collector = launch(start = CoroutineStart.UNDISPATCHED) { fallback = manager.fallbackNotifications.first() }

        val dispatched = manager.dispatch(Notification(title = "Success", message = "Test"))

        assertTrue(dispatched)
        assertNull(fallback, "Should not emit fallback when native sender succeeds")
        collector.cancel()
    }
}
