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

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.common.state.RadioOperation
import org.meshtastic.core.common.state.RadioOperationLock
import org.meshtastic.core.model.Node
import org.meshtastic.core.repository.LiveUpdateMode
import org.meshtastic.core.testing.FakeLiveUpdatePrefs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LiveUpdateGuardTest {
    private val tracked = Node(num = 42)
    private val radio = "x11:22:33:44:55:66"

    private val prefs = FakeLiveUpdatePrefs(LiveUpdateMode.TrackNode(tracked.num))
    private val nodes = MutableStateFlow<Map<Int, Node>>(emptyMap())
    private val address = MutableStateFlow<String?>(null)
    private val lock = RadioOperationLock()

    private fun guard() = LiveUpdateGuard(prefs, { nodes }, address, lock)

    @Test
    fun `a node not yet loaded keeps tracking`() {
        assertFalse(trackedNodeEnds(node = null, seenBefore = false))
    }

    @Test
    fun `a node that disappears after being seen ends tracking`() {
        assertTrue(trackedNodeEnds(node = null, seenBefore = true))
    }

    @Test
    fun `an ignored node ends tracking`() {
        assertTrue(trackedNodeEnds(node = tracked.copy(isIgnored = true), seenBefore = false))
    }

    @Test
    fun `tracking reverts to off when the node is deleted`() = runTest(UnconfinedTestDispatcher()) {
        backgroundScope.launch { guard().run() }

        nodes.value = mapOf(tracked.num to tracked)
        assertEquals(LiveUpdateMode.TrackNode(tracked.num), prefs.mode.value)

        nodes.value = emptyMap()
        assertEquals(LiveUpdateMode.Off, prefs.mode.value)
    }

    @Test
    fun `tracking reverts to off when the node is ignored`() = runTest(UnconfinedTestDispatcher()) {
        backgroundScope.launch { guard().run() }

        nodes.value = mapOf(tracked.num to tracked.copy(isIgnored = true))
        assertEquals(LiveUpdateMode.Off, prefs.mode.value)
    }

    @Test
    fun `a user disconnect ends the live update`() = runTest(UnconfinedTestDispatcher()) {
        prefs.setMode(LiveUpdateMode.MeshStatus)
        address.value = radio
        backgroundScope.launch { guard().run() }

        address.value = "n"
        assertEquals(LiveUpdateMode.Off, prefs.mode.value)
    }

    @Test
    fun `a radio switch ends the live update`() {
        assertTrue(selectionEndsLiveUpdate(previous = radio, current = "t10.0.2.2:4403", firmwareBusy = false))
    }

    @Test
    fun `selecting a radio from none does not end the live update`() {
        assertFalse(selectionEndsLiveUpdate(previous = null, current = radio, firmwareBusy = false))
        assertFalse(selectionEndsLiveUpdate(previous = "n", current = radio, firmwareBusy = false))
    }

    @Test
    fun `the firmware flow deselecting the radio keeps the live update`() = runTest(UnconfinedTestDispatcher()) {
        prefs.setMode(LiveUpdateMode.MeshStatus)
        address.value = radio
        backgroundScope.launch { guard().run() }

        val lease = lock.acquire(RadioOperation.FirmwareUpdate)
        address.value = "n"
        address.value = radio
        lock.release(lease)

        assertEquals(LiveUpdateMode.MeshStatus, prefs.mode.value)
    }
}
