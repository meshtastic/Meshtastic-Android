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
package org.meshtastic.core.network.radio

import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import dev.mokkery.verify
import io.kotest.property.Arb
import io.kotest.property.arbitrary.byte
import io.kotest.property.arbitrary.byteArray
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.network.transport.StreamFrameCodec
import org.meshtastic.core.repository.RadioInterfaceService
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class StreamTransportTest {

    private val radioService: RadioInterfaceService = mock(MockMode.autofill)
    private lateinit var fakeStream: FakeStreamTransport

    class FakeStreamTransport(service: RadioInterfaceService, scope: TestScope) : StreamTransport(service, scope) {
        val sentBytes = mutableListOf<ByteArray>()

        override fun sendBytes(p: ByteArray) {
            sentBytes.add(p)
        }

        override fun flushBytes() {
            /* no-op */
        }

        override fun keepAlive() {
            /* no-op */
        }

        fun feed(b: Byte) = readChar(b)

        public override fun connect() = super.connect()
    }

    private val testScope = TestScope()

    @BeforeTest
    fun setUp() {
        every { radioService.serviceScope } returns testScope
    }

    @Test
    fun `handleSendToRadio property test`() = runTest {
        fakeStream = FakeStreamTransport(radioService, testScope)

        checkAll(Arb.byteArray(Arb.int(0, 512), Arb.byte())) { payload -> fakeStream.handleSendToRadio(payload) }
    }

    @Test
    fun `readChar property test`() = runTest {
        fakeStream = FakeStreamTransport(radioService, testScope)

        checkAll(Arb.byteArray(Arb.int(0, 100), Arb.byte())) { data ->
            data.forEach { fakeStream.feed(it) }
            // Ensure no crash
        }
    }

    @Test
    fun `connect sends wake bytes`() {
        fakeStream = FakeStreamTransport(radioService, testScope)
        fakeStream.connect()

        assertTrue(fakeStream.sentBytes.isNotEmpty())
        assertTrue(fakeStream.sentBytes[0].contentEquals(StreamFrameCodec.WAKE_BYTES))
        verify { radioService.onConnect() }
    }
}
