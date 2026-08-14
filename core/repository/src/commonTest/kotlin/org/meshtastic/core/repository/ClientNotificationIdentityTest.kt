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
        val first = ClientNotification(message = "Location sharing is disabled", reply_id = 100, time = 1_000)
        val second = ClientNotification(message = "Location sharing is disabled", reply_id = 200, time = 2_000)

        assertEquals(first.notificationId(), second.notificationId())
    }

    @Test
    fun `identity differs when the message differs`() {
        val first = ClientNotification(message = "Location sharing is disabled", reply_id = 100)
        val second = ClientNotification(message = "Quota exceeded", reply_id = 100)

        assertNotEquals(first.notificationId(), second.notificationId())
    }

    @Test
    fun `identity differs when the payload variant differs`() {
        val generic = ClientNotification(message = "Compromised keys detected", level = LogRecord.Level.WARNING)
        val structured =
            ClientNotification(
                message = "Compromised keys detected",
                level = LogRecord.Level.WARNING,
                duplicated_public_key = DuplicatedPublicKey(),
            )

        assertNotEquals(generic.notificationId(), structured.notificationId())
    }
}
