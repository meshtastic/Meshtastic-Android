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

import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.model.getStringResFrom
import org.meshtastic.core.repository.RadioController
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.UiText
import org.meshtastic.core.resources.request_failed_from
import org.meshtastic.core.resources.traceroute
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.Routing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RequestNakWatcherTest {
    private val packets = MutableSharedFlow<MeshPacket>(extraBufferCapacity = 8)
    private val resolved = mutableListOf<UiText>()
    private val radioController =
        mock<RadioController>(MockMode.autofill).also { every { it.generatePacketId() } returns REQUEST_ID }

    private fun TestScope.watcher() = testNakWatcher(
        radioController = radioController,
        packets = packets,
        resolveUiText = {
            resolved += it
            ""
        },
        scope = backgroundScope,
    )

    @Test
    fun `a nak to the request shows why it failed`() = runTest {
        var sentId = 0
        watcher().watch(Res.string.traceroute, "Test Node") { sentId = it }
        packets.emit(routingNakPacket(REQUEST_ID, from = 1234, error = Routing.Error.NOT_AUTHORIZED))
        runCurrent()

        assertEquals(REQUEST_ID, sentId)
        val text = resolved.single() as UiText.Resource
        assertEquals(Res.string.request_failed_from, text.res)
        assertEquals(
            listOf(Res.string.traceroute, "Test Node", getStringResFrom(Routing.Error.NOT_AUTHORIZED.value)),
            text.args.toList(),
        )
    }

    @Test
    fun `a nak to another request or a plain ack shows nothing`() = runTest {
        watcher().watch(Res.string.traceroute, "Test Node") {}
        packets.emit(routingNakPacket(REQUEST_ID + 1, from = 1234, error = Routing.Error.NOT_AUTHORIZED))
        packets.emit(routingNakPacket(REQUEST_ID, from = 1234, error = Routing.Error.NONE))
        runCurrent()

        assertTrue(resolved.isEmpty())
    }

    @Test
    fun `a failed send stops watching`() = runTest {
        assertFailsWith<IllegalStateException> {
            watcher().watch(Res.string.traceroute, "Test Node") { error("send failed") }
        }
        packets.emit(routingNakPacket(REQUEST_ID, from = 1234, error = Routing.Error.NOT_AUTHORIZED))
        runCurrent()

        assertTrue(resolved.isEmpty())
    }

    private companion object {
        const val REQUEST_ID = 0x4242
    }
}
