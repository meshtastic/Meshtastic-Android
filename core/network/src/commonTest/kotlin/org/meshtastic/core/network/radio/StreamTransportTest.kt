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
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verify.VerifyMode
import io.kotest.property.Arb
import io.kotest.property.arbitrary.byte
import io.kotest.property.arbitrary.byteArray
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.network.transport.StreamFrameCodec
import org.meshtastic.core.repository.RadioTransportCallback
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StreamTransportTest {

    private val callback: RadioTransportCallback = mock(MockMode.autofill)
    private lateinit var fakeStream: FakeStreamTransport

    class FakeStreamTransport(callback: RadioTransportCallback, scope: CoroutineScope) :
        StreamTransport(callback, scope) {
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

        fun queue(payload: ByteArray, writer: suspend (ByteArray) -> Unit, onCompletion: () -> Unit): Boolean =
            queueFramedSend(payload, writer = writer, onCompletion = onCompletion)

        public override fun connect() = super.connect()
    }

    @Test
    fun `handleSendToRadio property test`() = runTest {
        fakeStream = FakeStreamTransport(callback, backgroundScope)

        checkAll(Arb.byteArray(Arb.int(0, 512), Arb.byte())) { payload ->
            assertTrue(fakeStream.handleSendToRadio(payload))
            testScheduler.runCurrent()
        }
    }

    @Test
    fun `send is rejected after the transport scope stops`() {
        val stoppedScope = TestScope()
        val transport = FakeStreamTransport(callback, stoppedScope)
        stoppedScope.cancel()

        assertFalse(transport.handleSendToRadio(byteArrayOf(1, 2, 3)))
        assertTrue(transport.sentBytes.isEmpty())
    }

    @Test
    fun `rejected framed send releases its completion owner exactly once`() {
        val stoppedScope = TestScope()
        val transport = FakeStreamTransport(callback, stoppedScope)
        var completions = 0
        stoppedScope.cancel()

        val accepted = transport.queue(byteArrayOf(1), writer = {}, onCompletion = { completions++ })

        assertFalse(accepted)
        assertEquals(1, completions)
    }

    @Test
    fun `concurrent framed sends preserve FIFO without interleaving writes`() = runTest {
        val transport = FakeStreamTransport(callback, backgroundScope)
        val firstWriteStarted = CompletableDeferred<Unit>()
        val releaseFirstWrite = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        var firstWrites = 0
        var completions = 0

        assertTrue(
            transport.queue(
                payload = byteArrayOf(1),
                writer = {
                    events += "first"
                    if (firstWrites++ == 0) {
                        firstWriteStarted.complete(Unit)
                        releaseFirstWrite.await()
                    }
                },
                onCompletion = { completions++ },
            ),
        )
        assertTrue(transport.queue(payload = byteArrayOf(2), writer = { events += "second" }) { completions++ })

        firstWriteStarted.await()
        assertEquals(listOf("first"), events)
        releaseFirstWrite.complete(Unit)
        testScheduler.runCurrent()

        // StreamFrameCodec invokes the writer once for the frame header and once for the body of each payload.
        assertEquals(listOf("first", "first", "second", "second"), events)
        assertEquals(2, completions)
    }

    @Test
    fun `framed send queue reports backpressure at its capacity`() = runTest {
        val transport = FakeStreamTransport(callback, backgroundScope)
        val firstWriteStarted = CompletableDeferred<Unit>()
        val releaseFirstWrite = CompletableDeferred<Unit>()
        var acceptedCompletions = 0
        var rejectedCompletions = 0

        assertTrue(
            transport.queue(
                payload = byteArrayOf(0),
                writer = {
                    firstWriteStarted.complete(Unit)
                    releaseFirstWrite.await()
                },
                onCompletion = {},
            ),
        )
        firstWriteStarted.await()

        val completionsByIndex = IntArray(StreamTransport.MAX_PENDING_SENDS + 1)
        // The worker has dequeued the blocked first send, so the bounded channel has exactly MAX_PENDING_SENDS slots.
        val queuedAdmissions =
            List(StreamTransport.MAX_PENDING_SENDS + 1) { index ->
                transport.queue(byteArrayOf(index.toByte()), writer = {}) { completionsByIndex[index]++ }
            }
        queuedAdmissions.forEachIndexed { index, accepted ->
            if (accepted) {
                acceptedCompletions += completionsByIndex[index]
            } else {
                rejectedCompletions += completionsByIndex[index]
            }
        }

        assertEquals(StreamTransport.MAX_PENDING_SENDS, queuedAdmissions.count { it })
        assertFalse(queuedAdmissions.last(), "the first send beyond capacity must be rejected")
        assertEquals(1, rejectedCompletions, "a capacity-rejected send must release its owner exactly once")
        assertEquals(0, acceptedCompletions, "admitted sends must stay queued while the worker is blocked")
        releaseFirstWrite.complete(Unit)
        testScheduler.runCurrent()

        val admittedCompletions =
            queuedAdmissions
                .withIndex()
                .filter { (_, accepted) -> accepted }
                .sumOf { (index, _) -> completionsByIndex[index] }
        assertEquals(StreamTransport.MAX_PENDING_SENDS, admittedCompletions)
        assertEquals(1, completionsByIndex.last(), "the rejected send must release its owner exactly once")
    }

    @Test
    fun `close lets an active framed send finish before returning`() = runTest {
        val transport = FakeStreamTransport(callback, backgroundScope)
        val writeStarted = CompletableDeferred<Unit>()
        val releaseWrite = CompletableDeferred<Unit>()
        var completions = 0

        assertTrue(
            transport.queue(
                payload = byteArrayOf(1),
                writer = {
                    writeStarted.complete(Unit)
                    releaseWrite.await()
                },
                onCompletion = { completions++ },
            ),
        )
        writeStarted.await()

        val closeJob = async { transport.close() }
        testScheduler.runCurrent()

        assertFalse(closeJob.isCompleted, "close must not cancel an active frame while it can still drain")
        releaseWrite.complete(Unit)
        closeJob.await()

        assertEquals(1, completions)
    }

    @Test
    fun `close completes ownership when the worker never started`() = runTest {
        val transport = FakeStreamTransport(callback, backgroundScope)
        var completions = 0

        assertTrue(transport.queue(byteArrayOf(1), writer = {}, onCompletion = { completions++ }))
        assertTrue(transport.queue(byteArrayOf(2), writer = {}, onCompletion = { completions++ }))

        transport.close()

        assertEquals(2, completions, "shutdown must release owners even before the worker starts")
    }

    @Test
    fun `per-send cancellation does not stop the framed-send worker`() = runTest {
        val transport = FakeStreamTransport(callback, backgroundScope)
        var completions = 0
        val laterWrites = mutableListOf<ByteArray>()

        assertTrue(
            transport.queue(
                payload = byteArrayOf(1),
                writer = { throw CancellationException("cancel this send only") },
                onCompletion = { completions++ },
            ),
        )
        assertTrue(
            transport.queue(payload = byteArrayOf(2), writer = { laterWrites += it }, onCompletion = { completions++ }),
        )

        testScheduler.runCurrent()

        assertTrue(laterWrites.isNotEmpty(), "a cancelled send must not permanently stop the worker")
        assertEquals(2, completions)
    }

    @Test
    fun `readChar property test`() = runTest {
        fakeStream = FakeStreamTransport(callback, backgroundScope)

        checkAll(Arb.byteArray(Arb.int(0, 100), Arb.byte())) { data ->
            data.forEach { fakeStream.feed(it) }
            // Ensure no crash
        }
    }

    @Test
    fun `connect sends wake bytes`() = runTest {
        fakeStream = FakeStreamTransport(callback, backgroundScope)
        fakeStream.connect()

        assertTrue(fakeStream.sentBytes.isNotEmpty())
        assertTrue(fakeStream.sentBytes[0].contentEquals(StreamFrameCodec.WAKE_BYTES))
        verify { callback.onConnect() }
        fakeStream.close()
    }

    @Test
    fun `close does not emit disconnect callback`() = runTest {
        fakeStream = FakeStreamTransport(callback, backgroundScope)
        fakeStream.connect()

        fakeStream.close()

        verify(mode = VerifyMode.not) {
            callback.onDisconnect(isPermanent = any(), errorMessage = any(), reason = any())
        }
    }

    @Test
    fun `close completes ownership for in-flight and queued framed sends`() = runTest {
        val transport = FakeStreamTransport(callback, backgroundScope)
        val firstWriteStarted = CompletableDeferred<Unit>()
        var completions = 0

        assertTrue(
            transport.queue(
                payload = byteArrayOf(1),
                writer = {
                    firstWriteStarted.complete(Unit)
                    awaitCancellation()
                },
                onCompletion = { completions++ },
            ),
        )
        assertTrue(transport.queue(byteArrayOf(2), writer = {}, onCompletion = { completions++ }))
        firstWriteStarted.await()

        transport.close()

        assertEquals(2, completions, "shutdown must release every accepted framed-send owner exactly once")
    }
}
