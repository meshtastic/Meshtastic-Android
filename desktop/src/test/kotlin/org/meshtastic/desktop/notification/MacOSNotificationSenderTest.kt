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

    private val sender = MacOSNotificationSender()

    @Test
    fun `command starts with osascript`() {
        val notification = Notification(title = "Hi", message = "There")
        val cmd = sender.buildCommand(notification)
        assertEquals("osascript", cmd[0])
    }

    @Test
    fun `non-silent notification includes sound name`() {
        val notification = Notification(title = "Hi", message = "There", isSilent = false)
        val cmd = sender.buildCommand(notification)
        val script = cmd.filter { it.contains("display notification") }.joinToString(" ")
        assertTrue(script.contains("sound name \"default\""), "Expected sound name in: $script")
    }

    @Test
    fun `silent notification omits sound name`() {
        val notification = Notification(title = "Quiet", message = "Shhh", isSilent = true)
        val cmd = sender.buildCommand(notification)
        val script = cmd.filter { it.contains("display notification") }.joinToString(" ")
        assertFalse(script.contains("sound name"), "Expected no sound name in: $script")
    }

    @Test
    fun `title and message passed as positional args after double dash`() {
        val notification = Notification(title = "My Title", message = "My Message")
        val cmd = sender.buildCommand(notification)
        val dashDashIdx = cmd.indexOf("--")
        assertTrue(dashDashIdx > 0, "Expected '--' separator in command")
        assertEquals("My Title", cmd[dashDashIdx + 1])
        assertEquals("My Message", cmd[dashDashIdx + 2])
    }

    @Test
    fun `special characters are not interpolated into script`() {
        val notification = Notification(title = "It's \"tricky\" & <bad>", message = "'; drop table;")
        val cmd = sender.buildCommand(notification)
        // Script lines should not contain the user content — only argv references
        val scriptLines = cmd.filter { it.contains("notifTitle") || it.contains("notifMessage") }
        for (line in scriptLines) {
            assertFalse(line.contains("tricky"), "User content leaked into script: $line")
            assertFalse(line.contains("drop table"), "User content leaked into script: $line")
        }
        // But args should contain the raw content
        val dashDashIdx = cmd.indexOf("--")
        assertEquals("It's \"tricky\" & <bad>", cmd[dashDashIdx + 1])
        assertEquals("'; drop table;", cmd[dashDashIdx + 2])
    }

    @Test
    fun `battery category becomes subtitle`() {
        val notification = Notification(title = "Bat", message = "Low", category = Notification.Category.Battery)
        val cmd = sender.buildCommand(notification)
        val dashDashIdx = cmd.indexOf("--")
        assertEquals("Low Battery", cmd[dashDashIdx + 3])
    }
}
