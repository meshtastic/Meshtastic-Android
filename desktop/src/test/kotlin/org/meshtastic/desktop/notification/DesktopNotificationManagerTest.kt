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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.meshtastic.core.repository.Notification
import org.meshtastic.core.repository.NotificationPrefs
import org.meshtastic.desktop.DesktopNotificationManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

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
        override val lowBatteryEnabled = MutableStateFlow(lowBattery)

        override fun setMessagesEnabled(enabled: Boolean) {
            messagesEnabled.value = enabled
        }

        override fun setNodeEventsEnabled(enabled: Boolean) {
            nodeEventsEnabled.value = enabled
        }

        override fun setLowBatteryEnabled(enabled: Boolean) {
            lowBatteryEnabled.value = enabled
        }
    }

    @Test
    fun `dispatch sends to native sender when enabled`() {
        val sender = FakeNativeSender()
        val manager = DesktopNotificationManager(FakeNotificationPrefs(), sender)

        manager.dispatch(Notification(title = "Test", message = "Hello"))

        Thread.sleep(ASYNC_WAIT_MS)
        assertEquals(1, sender.sent.size)
        assertEquals("Test", sender.sent[0].title)
    }

    @Test
    fun `dispatch respects disabled message preference`() {
        val sender = FakeNativeSender()
        val manager = DesktopNotificationManager(FakeNotificationPrefs(messages = false), sender)

        manager.dispatch(Notification(title = "Msg", message = "Hi", category = Notification.Category.Message))

        Thread.sleep(ASYNC_WAIT_MS)
        assertEquals(0, sender.sent.size, "Message notification should have been suppressed")
    }

    @Test
    fun `alerts are always dispatched even when messages disabled`() {
        val sender = FakeNativeSender()
        val manager = DesktopNotificationManager(FakeNotificationPrefs(messages = false), sender)

        manager.dispatch(Notification(title = "Alert", message = "Important", category = Notification.Category.Alert))

        Thread.sleep(ASYNC_WAIT_MS)
        assertEquals(1, sender.sent.size)
    }

    @Test
    fun `fallback emitted when native sender fails`() {
        val sender = FakeNativeSender(shouldSucceed = false)
        val manager = DesktopNotificationManager(FakeNotificationPrefs(), sender)
        var fallback: androidx.compose.ui.window.Notification? = null

        // Collect on a real thread since dispatch uses Dispatchers.IO
        val job =
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
                fallback = manager.fallbackNotifications.first()
            }

        // Give the collector coroutine time to subscribe before dispatching
        Thread.sleep(SUBSCRIBE_WAIT_MS)
        manager.dispatch(Notification(title = "Fallback", message = "Test"))

        // Block the test thread briefly to let the IO dispatcher process
        Thread.sleep(ASYNC_WAIT_MS)
        assertNotNull(fallback, "Expected fallback notification to be emitted")
        assertEquals("Fallback", fallback!!.title)
        job.cancel()
    }

    @Test
    fun `no fallback when native sender succeeds`() {
        val sender = FakeNativeSender(shouldSucceed = true)
        val manager = DesktopNotificationManager(FakeNotificationPrefs(), sender)
        var fallback: androidx.compose.ui.window.Notification? = null

        val job =
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
                fallback = manager.fallbackNotifications.first()
            }

        manager.dispatch(Notification(title = "Success", message = "Test"))

        Thread.sleep(ASYNC_WAIT_MS)
        assertNull(fallback, "Should not emit fallback when native sender succeeds")
        job.cancel()
    }

    companion object {
        private const val ASYNC_WAIT_MS = 300L
        private const val SUBSCRIBE_WAIT_MS = 100L
    }
}
