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

import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import org.meshtastic.core.repository.Notification
import org.meshtastic.core.repository.NotificationManager

class NotificationManagerTest {

    @Test
    fun `dispatch calls implementation`() {
        val manager = mockk<NotificationManager>(relaxed = true)
        val notification = Notification("Title", "Message")

        manager.dispatch(notification)

        verify { manager.dispatch(notification) }
    }
}
