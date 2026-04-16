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

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class RadioTransportTest {

    @Test
    fun `RadioTransport can be implemented`() = runTest {
        var sentData: ByteArray? = null
        var closed = false
        var keepAliveCalled = false

        val transport =
            object : RadioTransport {
                override fun handleSendToRadio(p: ByteArray) {
                    sentData = p
                }

                override fun keepAlive() {
                    keepAliveCalled = true
                }

                override suspend fun close() {
                    closed = true
                }
            }

        val testData = byteArrayOf(1, 2, 3)
        transport.handleSendToRadio(testData)
        transport.keepAlive()
        transport.close()

        assertTrue(sentData!!.contentEquals(testData))
        assertTrue(keepAliveCalled)
        assertTrue(closed)
    }
}
