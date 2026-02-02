/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package com.geeksville.mesh.repository.radio

import com.geeksville.mesh.service.Fakes
import io.mockk.every
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Test
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.proto.Heartbeat
import org.meshtastic.proto.ToRadio

class TCPInterfaceTest {

    @Test
    fun testKeepAlive() {
        val fakes = Fakes()
        val testDispatcher = UnconfinedTestDispatcher()
        val testScope = CoroutineScope(testDispatcher + Job())
        every { fakes.service.serviceScope } returns testScope

        val dispatchers = CoroutineDispatchers(io = testDispatcher, main = testDispatcher, default = testDispatcher)
        val tcpIf =
            object : TCPInterface(fakes.service, dispatchers, "127.0.0.1") {
                var lastSent: ByteArray? = null

                override fun handleSendToRadio(p: ByteArray) {
                    lastSent = p
                }
            }

        tcpIf.keepAlive()

        val expected = ToRadio(heartbeat = Heartbeat()).encode()
        assertEquals(expected.toList(), tcpIf.lastSent?.toList())
    }
}
