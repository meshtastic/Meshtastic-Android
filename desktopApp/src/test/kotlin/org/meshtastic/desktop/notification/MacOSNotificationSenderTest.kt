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

import org.meshtastic.core.repository.Notification
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MacOSNotificationSenderTest {

    @Test
    fun `returns false when bridge unavailable`() {
        val bridge = FakeBridge(available = false)
        val sender = MacOSNotificationSender(bridge)

        val result = sender.send(Notification(title = "Hi", message = "There"))

        assertFalse(result)
        assertEquals(0, bridge.authorizationCalls)
        assertEquals(0, bridge.postCalls.size)
    }

    @Test
    fun `requests authorization once then posts notification`() {
        val bridge = FakeBridge(available = true)
        val sender = MacOSNotificationSender(bridge)

        val first =
            sender.send(Notification(title = "One", message = "First", category = Notification.Category.Battery))
        val second =
            sender.send(Notification(title = "Two", message = "Second", category = Notification.Category.Alert))

        assertTrue(first)
        assertTrue(second)
        assertEquals(1, bridge.authorizationCalls)
        assertEquals(MacOSNotificationSender.DEFAULT_AUTHORIZATION_OPTIONS, bridge.lastAuthorizationOptions)
        assertEquals(2, bridge.postCalls.size)
        assertEquals("Low Battery", bridge.postCalls[0].subtitle)
        assertEquals("Alert", bridge.postCalls[1].subtitle)
    }

    @Test
    fun `silent notification disables sound`() {
        val bridge = FakeBridge(available = true)
        val sender = MacOSNotificationSender(bridge)

        sender.send(Notification(title = "Quiet", message = "Shhh", isSilent = true))

        assertEquals(1, bridge.postCalls.size)
        assertFalse(bridge.postCalls[0].playSound)
    }

    @Test
    fun `non-silent notification enables sound`() {
        val bridge = FakeBridge(available = true)
        val sender = MacOSNotificationSender(bridge)

        sender.send(Notification(title = "Loud", message = "Ping", isSilent = false))

        assertEquals(1, bridge.postCalls.size)
        assertTrue(bridge.postCalls[0].playSound)
    }

    private class FakeBridge(private val available: Boolean) : MacNotificationBridge {
        override val isAvailable: Boolean
            get() = available

        var authorizationCalls: Int = 0
        var lastAuthorizationOptions: Long? = null
        val postCalls = mutableListOf<PostCall>()

        override fun requestAuthorization(options: Long): Boolean {
            authorizationCalls += 1
            lastAuthorizationOptions = options
            return true
        }

        override fun post(title: String, message: String, subtitle: String, playSound: Boolean): Boolean {
            postCalls += PostCall(title = title, message = message, subtitle = subtitle, playSound = playSound)
            return true
        }
    }

    private data class PostCall(val title: String, val message: String, val subtitle: String, val playSound: Boolean)
}
