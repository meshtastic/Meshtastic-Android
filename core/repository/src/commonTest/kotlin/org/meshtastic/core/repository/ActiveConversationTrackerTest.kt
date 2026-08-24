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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ActiveConversationTrackerTest {

    private val tracker = ActiveConversationTracker()

    @Test
    fun `defaults to alerting`() {
        assertEquals(MessageAnnouncement.Alert, tracker.announcementFor("0^all"))
    }

    @Test
    fun `suppresses only the conversation on screen`() {
        tracker.setAppForeground(true)
        tracker.setActive("0^all")

        assertEquals(MessageAnnouncement.Suppress, tracker.announcementFor("0^all"))
        assertEquals(MessageAnnouncement.Silent, tracker.announcementFor("0!abcdef01"))
    }

    @Test
    fun `backgrounding alerts even while a key is still set`() {
        tracker.setActive("0^all")
        tracker.setAppForeground(false)

        assertEquals(MessageAnnouncement.Alert, tracker.announcementFor("0^all"))
    }

    @Test
    fun `clearing a stale key does not erase the conversation that replaced it`() {
        tracker.setAppForeground(true)
        tracker.setActive("0^all")
        // Navigating A -> B: B resumes before A pauses.
        tracker.setActive("0!abcdef01")
        tracker.clearActive("0^all")

        assertEquals("0!abcdef01", tracker.activeContactKey.value)
        assertEquals(MessageAnnouncement.Suppress, tracker.announcementFor("0!abcdef01"))
    }

    @Test
    fun `clearing the current key clears it`() {
        tracker.setActive("0^all")
        tracker.clearActive("0^all")

        assertNull(tracker.activeContactKey.value)
    }
}
