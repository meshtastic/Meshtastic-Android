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
package org.meshtastic.core.ui.component

import org.meshtastic.core.model.Node
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CurrentRadioNodePresentationTest {

    private val measuredZeroNode = Node(num = 1, lastHeard = Int.MAX_VALUE, hopsAway = 0, snr = 0f, rssi = 0)

    @Test
    fun completedSnapshotAbsenceMasksOnlyCurrentRadioObservations() {
        val presentation = measuredZeroNode.currentRadioPresentation(isInCurrentRadioNodeSnapshot = false)

        assertTrue(presentation.isSavedOnPhoneOnly)
        assertFalse(presentation.isOnline)
        assertNull(presentation.lastHeard)
        assertNull(presentation.hopsAway)
        assertNull(presentation.snr)
        assertNull(presentation.rssi)
    }

    @Test
    fun completedSnapshotMembershipPreservesMeasuredZeros() {
        val presentation = measuredZeroNode.currentRadioPresentation(isInCurrentRadioNodeSnapshot = true)

        assertFalse(presentation.isSavedOnPhoneOnly)
        assertTrue(presentation.isOnline)
        assertEquals(Int.MAX_VALUE, presentation.lastHeard)
        assertEquals(0, presentation.hopsAway)
        assertEquals(0f, presentation.snr)
        assertEquals(0, presentation.rssi)
    }

    @Test
    fun unknownSnapshotPreservesExistingPresentation() {
        val presentation = measuredZeroNode.currentRadioPresentation(isInCurrentRadioNodeSnapshot = null)

        assertFalse(presentation.isSavedOnPhoneOnly)
        assertTrue(presentation.isOnline)
        assertEquals(Int.MAX_VALUE, presentation.lastHeard)
        assertEquals(0, presentation.hopsAway)
        assertEquals(0f, presentation.snr)
        assertEquals(0, presentation.rssi)
    }
}
