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
package com.geeksville.mesh.repository.radio

import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.proto.MeshProtos

class TCPInterfaceTest {

    private val service: RadioInterfaceService = mockk(relaxed = true)
    private val dispatchers: CoroutineDispatchers = mockk(relaxed = true)

    @Test
    fun `keepAlive generates correct heartbeat bytes`() = runTest {
        val address = "192.168.1.1:4403"
        // We need a subclass to capture handleSendToRadio or sendBytes
        val tcpInterface =
            object : TCPInterface(service, dispatchers, address) {
                var capturedBytes: ByteArray? = null

                override fun handleSendToRadio(p: ByteArray) {
                    capturedBytes = p
                }

                // Override connect to prevent it from starting automatically in init
                override fun connect() {}
            }

        tcpInterface.keepAlive()

        val expectedHeartbeat =
            MeshProtos.ToRadio.newBuilder()
                .setHeartbeat(MeshProtos.Heartbeat.getDefaultInstance())
                .build()
                .toByteArray()

        assertArrayEquals("Heartbeat bytes should match", expectedHeartbeat, tcpInterface.capturedBytes)
    }

    @Test
    fun `sendBytes does not crash when outStream is null`() = runTest {
        val address = "192.168.1.1:4403"
        val tcpInterface = object : TCPInterface(service, dispatchers, address) {
            override fun connect() {}
        }
        
        // This should not throw UninitializedPropertyAccessException
        tcpInterface.sendBytes(byteArrayOf(1, 2, 3))
    }

    @Test
    fun `flushBytes does not crash when outStream is null`() = runTest {
        val address = "192.168.1.1:4403"
        val tcpInterface = object : TCPInterface(service, dispatchers, address) {
            override fun connect() {}
        }
        
        // This should not throw UninitializedPropertyAccessException
        tcpInterface.flushBytes()
    }
}
