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

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.app.RemoteInput
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.RadioController
import org.meshtastic.core.repository.MeshServiceNotifications
import org.meshtastic.core.testing.FakeRadioController
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReplyReceiverTest {

    private lateinit var context: Context
    private lateinit var radioController: FakeRadioController
    private lateinit var notifications: RecordingNotifications
    private val callLog = mutableListOf<String>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        radioController = RecordingRadioController(callLog)
        notifications = RecordingNotifications(callLog)
        val dispatcher = UnconfinedTestDispatcher()
        startKoin {
            modules(
                module {
                    single<RadioController> { radioController }
                    single<MeshServiceNotifications> { notifications }
                    single { CoroutineDispatchers(io = dispatcher, main = dispatcher, default = dispatcher) }
                },
            )
        }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `reply sends DataPacket and marks conversation read in order`() {
        val contactKey = "2!abcd1234"
        val replyText = "hello world"

        val intent =
            Intent(context, ReplyReceiver::class.java).apply {
                action = ReplyReceiver.REPLY_ACTION
                putExtra(ReplyReceiver.CONTACT_KEY, contactKey)
            }
        val results = Bundle().apply { putCharSequence(ReplyReceiver.KEY_TEXT_REPLY, replyText) }
        RemoteInput.addResultsToIntent(
            arrayOf(RemoteInput.Builder(ReplyReceiver.KEY_TEXT_REPLY).build()),
            intent,
            results,
        )

        ReplyReceiver().onReceive(context, intent)

        assertEquals(1, radioController.sentPackets.size)
        val sent = radioController.sentPackets.first()
        assertEquals("!abcd1234", sent.to)
        assertEquals(2, sent.channel)
        assertEquals(replyText, sent.text)

        assertEquals(listOf(contactKey to replyText), notifications.appendCalls)
        assertEquals(listOf(contactKey), notifications.markReadCalls)
        assertEquals(listOf("send", "append", "markRead"), callLog)
    }
}

private class RecordingRadioController(private val callLog: MutableList<String>) : FakeRadioController() {
    override suspend fun sendMessage(packet: org.meshtastic.core.model.DataPacket) {
        callLog.add("send")
        super.sendMessage(packet)
    }
}

internal class RecordingNotifications(private val callLog: MutableList<String>) :
    org.meshtastic.core.testing.FakeMeshServiceNotifications() {
    val appendCalls = mutableListOf<Pair<String, String>>()
    val markReadCalls = mutableListOf<String>()

    override suspend fun appendOutgoingMessage(contactKey: String, text: String) {
        callLog.add("append")
        appendCalls.add(contactKey to text)
    }

    override suspend fun markConversationRead(contactKey: String) {
        callLog.add("markRead")
        markReadCalls.add(contactKey)
    }
}
