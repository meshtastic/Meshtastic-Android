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
package org.meshtastic.feature.node.detail

import androidx.compose.material3.SnackbarDuration
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.mock
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.repository.PacketQueueRejectedException
import org.meshtastic.core.repository.RadioController
import org.meshtastic.core.ui.util.SnackbarManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CommonNodeRequestActionsTest {
    private val radioController: RadioController = mock()
    private val snackbarManager = RecordingSnackbarManager()
    private val actions = CommonNodeRequestActions(radioController, snackbarManager)

    private class RecordingSnackbarManager : SnackbarManager() {
        val messages = mutableListOf<String>()

        override fun showSnackbar(
            message: String,
            actionLabel: String?,
            withDismissAction: Boolean,
            duration: SnackbarDuration,
            onAction: (() -> Unit)?,
        ) {
            messages += message
        }
    }

    @Test
    fun `traceroute queue rejection is surfaced without starting the request timer`() = runTest {
        val rejection = PacketQueueRejectedException("Traceroute request")
        every { radioController.generatePacketId() } returns 42
        everySuspend { radioController.requestTraceroute(42, 1234) } throws rejection

        actions.requestTraceroute(destNum = 1234, longName = "Test Node")

        assertNull(actions.lastTracerouteTime.value)
        assertEquals(listOf(checkNotNull(rejection.message)), snackbarManager.messages)
    }

    @Test
    fun `neighbor queue rejection is surfaced without starting the request timer`() = runTest {
        val rejection = PacketQueueRejectedException("Neighbor info request")
        every { radioController.generatePacketId() } returns 43
        everySuspend { radioController.requestNeighborInfo(43, 1234) } throws rejection

        actions.requestNeighborInfo(destNum = 1234, longName = "Test Node")

        assertEquals(emptyMap(), actions.lastRequestNeighborTimes.value)
        assertEquals(listOf(checkNotNull(rejection.message)), snackbarManager.messages)
    }
}
