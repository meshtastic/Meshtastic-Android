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
package org.meshtastic.core.repository

import org.meshtastic.proto.ClientNotification
import org.meshtastic.proto.DuplicatedPublicKey
import org.meshtastic.proto.LogRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ClientNotificationIdentityTest {

    @Test
    fun `identity is stable across reply_id and time on an otherwise identical repeat`() {
        val first = ClientNotification.Builder().also { wb ->wb.message = "Location sharing is disabled"; wb.reply_id = 100; wb.time = 1_000}.build()
        val second = ClientNotification.Builder().also { wb ->wb.message = "Location sharing is disabled"; wb.reply_id = 200; wb.time = 2_000}.build()

        assertEquals(first.notificationId(), second.notificationId())
    }

    @Test
    fun `identity differs when the message differs`() {
        val first = ClientNotification.Builder().also { wb ->wb.message = "Location sharing is disabled"; wb.reply_id = 100}.build()
        val second = ClientNotification.Builder().also { wb ->wb.message = "Quota exceeded"; wb.reply_id = 100}.build()

        assertNotEquals(first.notificationId(), second.notificationId())
    }

    @Test
    fun `identity differs when the payload variant differs`() {
        val generic = ClientNotification.Builder().also { wb ->wb.message = "Compromised keys detected"; wb.level = LogRecord.Level.WARNING}.build()
        val structured =
            ClientNotification.Builder().also { wb ->
            wb.message = "Compromised keys detected"
            wb.level = LogRecord.Level.WARNING
            wb.duplicated_public_key = DuplicatedPublicKey.Builder().build()
            }.build()

        assertNotEquals(generic.notificationId(), structured.notificationId())
    }
}
