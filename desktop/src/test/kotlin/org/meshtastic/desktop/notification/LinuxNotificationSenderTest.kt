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
import kotlin.test.assertTrue

/**
 * Tests for [LinuxNotificationSender].
 *
 * These are integration-ish tests that verify the JNA-based sender can be instantiated. On CI or environments without
 * libnotify, the sender gracefully reports [LinuxNotificationSender.isAvailable] = false and `send()` returns false. On
 * a Linux dev machine with libnotify installed, these tests will actually display notifications.
 */
class LinuxNotificationSenderTest {

    private val sender = LinuxNotificationSender(appName = "MeshtasticTest")

    @Test
    fun `sender initializes without crashing`() {
        // Just verifying construction doesn't throw — isAvailable depends on the host having libnotify
        // This is a smoke test; the actual JNA call is validated by the availability check
        @Suppress("ktlint:standard:backing-property-naming")
        val available = sender.isAvailable
        available.toString() // use the val to satisfy lint
    }

    @Test
    fun `send returns false when libnotify unavailable`() {
        if (sender.isAvailable) return // skip on systems with libnotify — would actually show a notification
        val result = sender.send(Notification(title = "Test", message = "Hello"))
        assertTrue(!result, "Expected send() to return false when libnotify is not available")
    }

    @Test
    fun `send with all notification types does not crash`() {
        if (!sender.isAvailable) return // skip on systems without libnotify

        for (type in Notification.Type.entries) {
            val result = sender.send(Notification(title = "Type: $type", message = "Testing $type", type = type))
            assertTrue(result, "Expected send() to succeed for type $type")
        }
    }

    @Test
    fun `send with all categories does not crash`() {
        if (!sender.isAvailable) return // skip on systems without libnotify

        for (category in Notification.Category.entries) {
            val result =
                sender.send(Notification(title = "Cat: $category", message = "Testing $category", category = category))
            assertTrue(result, "Expected send() to succeed for category $category")
        }
    }

    @Test
    fun `silent notification does not crash`() {
        if (!sender.isAvailable) return
        val result = sender.send(Notification(title = "Silent", message = "Shhh", isSilent = true))
        assertTrue(result, "Expected silent send() to succeed")
    }
}
