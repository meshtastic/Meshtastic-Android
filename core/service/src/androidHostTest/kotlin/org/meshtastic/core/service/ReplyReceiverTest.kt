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

import android.content.Intent
import android.os.Bundle
import androidx.core.app.RemoteInput
import androidx.test.core.app.ApplicationProvider
import dev.mokkery.MockMode
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.repository.MeshNotificationManager
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.usecase.SendMessageUseCase
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReplyReceiverTest {

    private val sendMessageUseCase: SendMessageUseCase = mock(MockMode.autofill)
    private val notificationManager: MeshNotificationManager = mock(MockMode.autofill)
    private val packetRepository: PacketRepository = mock(MockMode.autofill)

    @Before
    fun setUp() {
        startKoin {
            modules(
                module {
                    single { sendMessageUseCase }
                    single { notificationManager }
                    single { packetRepository }
                    // Unconfined so the receiver's launched coroutine completes before onReceive returns
                    single {
                        CoroutineDispatchers(
                            io = Dispatchers.Unconfined,
                            main = Dispatchers.Unconfined,
                            default = Dispatchers.Unconfined,
                        )
                    }
                },
            )
        }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    private fun replyIntent(contactKey: String, text: String): Intent {
        val intent = Intent(ReplyReceiver.REPLY_ACTION).putExtra(ReplyReceiver.CONTACT_KEY, contactKey)
        val results = Bundle().apply { putCharSequence(ReplyReceiver.KEY_TEXT_REPLY, text) }
        RemoteInput.addResultsToIntent(
            arrayOf(RemoteInput.Builder(ReplyReceiver.KEY_TEXT_REPLY).build()),
            intent,
            results,
        )
        return intent
    }

    @Test
    fun `reply goes through SendMessageUseCase and marks conversation read`() {
        val contactKey = "0!12345678"

        ReplyReceiver().onReceive(ApplicationProvider.getApplicationContext(), replyIntent(contactKey, "hello back"))

        verifySuspend { sendMessageUseCase.invoke("hello back", contactKey, null) }
        verifySuspend { packetRepository.clearUnreadCount(contactKey, any()) }
        verify { notificationManager.cancelMessageNotification(contactKey) }
    }

    @Test
    fun `notification is cancelled even when the send fails`() {
        val contactKey = "0!12345678"
        everySuspend { sendMessageUseCase.invoke(any(), any(), any()) } throws RuntimeException("radio down")

        ReplyReceiver().onReceive(ApplicationProvider.getApplicationContext(), replyIntent(contactKey, "hi"))

        verifySuspend(mode = VerifyMode.exactly(0)) { packetRepository.clearUnreadCount(any(), any()) }
        verify { notificationManager.cancelMessageNotification(contactKey) }
    }

    @Test
    fun `missing RemoteInput results does not send`() {
        ReplyReceiver().onReceive(ApplicationProvider.getApplicationContext(), Intent(ReplyReceiver.REPLY_ACTION))

        verifySuspend(mode = VerifyMode.exactly(0)) { sendMessageUseCase.invoke(any(), any(), any()) }
    }
}
