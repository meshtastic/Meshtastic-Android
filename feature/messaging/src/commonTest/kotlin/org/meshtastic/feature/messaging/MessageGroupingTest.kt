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
package org.meshtastic.feature.messaging

import org.meshtastic.core.model.Message
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.model.Node
import org.meshtastic.core.ui.component.preview.NodePreviewParameterProvider
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the conversation-block rules: messages merge into one visual group only when they are from the same sender AND
 * within [GROUPING_WINDOW_MILLIS] of each other. The group header carries the run's only timestamp, so a significant
 * time gap must start a new block even for the same sender.
 */
class MessageGroupingTest {

    private val minnie = NodePreviewParameterProvider().minnieMouse
    private val mickey = NodePreviewParameterProvider().mickeyMouse

    private fun message(fromLocal: Boolean, node: Node, receivedTime: Long) = Message(
        text = "test",
        time = "10:00",
        fromLocal = fromLocal,
        status = MessageStatus.RECEIVED,
        snr = 2.5f,
        rssi = 90,
        hopsAway = 0,
        uuid = receivedTime,
        receivedTime = receivedTime,
        node = node,
        read = true,
        routingError = 0,
        packetId = 1,
        emojis = listOf(),
        replyId = null,
        viaMqtt = false,
    )

    @Test
    fun sameSenderWithinWindow_groups() {
        val older = message(fromLocal = false, node = minnie, receivedTime = 0L)
        val newer = message(fromLocal = false, node = minnie, receivedTime = GROUPING_WINDOW_MILLIS)
        assertTrue(isSameGroup(older, newer))
    }

    @Test
    fun sameSenderBeyondWindow_startsNewBlock() {
        val older = message(fromLocal = false, node = minnie, receivedTime = 0L)
        val newer = message(fromLocal = false, node = minnie, receivedTime = GROUPING_WINDOW_MILLIS + 1)
        assertFalse(isSameGroup(older, newer))
    }

    @Test
    fun differentSendersWithinWindow_doNotGroup() {
        val older = message(fromLocal = false, node = minnie, receivedTime = 0L)
        val newer = message(fromLocal = false, node = mickey, receivedTime = 1_000L)
        assertFalse(isSameGroup(older, newer))
    }

    @Test
    fun localAndRemoteWithinWindow_doNotGroup() {
        val older = message(fromLocal = true, node = mickey, receivedTime = 0L)
        val newer = message(fromLocal = false, node = mickey, receivedTime = 1_000L)
        assertFalse(isSameGroup(older, newer))
    }

    @Test
    fun localRunWithinWindow_groups() {
        val older = message(fromLocal = true, node = mickey, receivedTime = 0L)
        val newer = message(fromLocal = true, node = mickey, receivedTime = 1_000L)
        assertTrue(isSameGroup(older, newer))
    }

    @Test
    fun localRunBeyondWindow_startsNewBlock() {
        val older = message(fromLocal = true, node = mickey, receivedTime = 0L)
        val newer = message(fromLocal = true, node = mickey, receivedTime = GROUPING_WINDOW_MILLIS + 1)
        assertFalse(isSameGroup(older, newer))
    }

    @Test
    fun outOfOrderTimestampsWithinWindow_stillGroup() {
        // Mesh delivery can reorder; grouping uses the absolute gap.
        val older = message(fromLocal = false, node = minnie, receivedTime = 5_000L)
        val newer = message(fromLocal = false, node = minnie, receivedTime = 1_000L)
        assertTrue(isSameGroup(older, newer))
    }
}
