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
import org.meshtastic.core.repository.RadioController
import org.meshtastic.core.repository.usecase.SendMessageUseCase
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConversationActionServiceTest {

    private val sendMessageUseCase: SendMessageUseCase = mock(MockMode.autofill)
    private val packetRepository: PacketRepository = mock(MockMode.autofill)
    private val radioController: RadioController = mock(MockMode.autofill)
    private val notifications: MeshNotificationManager = mock(MockMode.autofill)
    private val contactKey = "0!12345678"

    @Before
    fun setUp() {
        startKoin {
            modules(
                module {
                    single { sendMessageUseCase }
                    single { packetRepository }
                    single { radioController }
                    single { notifications }
                    // Unconfined so each action completes inside startCommand.
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

    private fun run(intent: Intent) {
        Robolectric.buildService(ConversationActionService::class.java, intent).create().startCommand(0, 1)
    }

    private fun intent(action: String) =
        Intent(ApplicationProvider.getApplicationContext(), ConversationActionService::class.java)
            .setAction(action)
            .putExtra(ConversationActionService.EXTRA_CONTACT_KEY, contactKey)

    private fun replyIntent(text: String): Intent {
        val intent = intent(ConversationActionService.ACTION_REPLY)
        val results = Bundle().apply { putCharSequence(ConversationActionService.KEY_TEXT_REPLY, text) }
        RemoteInput.addResultsToIntent(
            arrayOf(RemoteInput.Builder(ConversationActionService.KEY_TEXT_REPLY).build()),
            intent,
            results,
        )
        return intent
    }

    @Test
    fun `a reply is sent, marks the conversation read and refreshes it in place`() {
        run(replyIntent("hello back"))

        verifySuspend { sendMessageUseCase.invoke("hello back", contactKey, null) }
        verifySuspend { packetRepository.clearUnreadCount(contactKey, any()) }
        verifySuspend { notifications.refreshConversationAfterReply(contactKey) }
        verifySuspend(VerifyMode.exactly(0)) { notifications.cancelMessageNotification(any()) }
    }

    @Test
    fun `a failed reply still dismisses so the reply spinner resolves`() {
        everySuspend { sendMessageUseCase.invoke(any(), any(), any()) } throws RuntimeException("radio down")

        run(replyIntent("hi"))

        verifySuspend(VerifyMode.exactly(0)) { packetRepository.clearUnreadCount(any(), any()) }
        verifySuspend { notifications.cancelMessageNotification(contactKey) }
    }

    @Test
    fun `a reply without text sends nothing`() {
        run(intent(ConversationActionService.ACTION_REPLY))

        verifySuspend(VerifyMode.exactly(0)) { sendMessageUseCase.invoke(any(), any(), any()) }
    }

    @Test
    fun `mark as read clears the unread count and dismisses the conversation`() {
        run(intent(ConversationActionService.ACTION_MARK_AS_READ))

        verifySuspend { packetRepository.clearUnreadCount(contactKey, any()) }
        verifySuspend { notifications.cancelMessageNotification(contactKey) }
    }

    @Test
    fun `a thumbs-up is sent and shown in the conversation`() {
        run(
            intent(ConversationActionService.ACTION_REACT)
                .putExtra(ConversationActionService.EXTRA_REPLY_ID, 42)
                .putExtra(ConversationActionService.EXTRA_EMOJI, "👍"),
        )

        verifySuspend { radioController.sendReaction("👍", 42, contactKey) }
        verifySuspend { notifications.refreshConversationAfterReply(contactKey) }
    }

    @Test
    fun `a failed thumbs-up leaves the conversation in the tray`() {
        everySuspend { radioController.sendReaction(any(), any(), any()) } throws RuntimeException("radio down")

        run(
            intent(ConversationActionService.ACTION_REACT)
                .putExtra(ConversationActionService.EXTRA_REPLY_ID, 42)
                .putExtra(ConversationActionService.EXTRA_EMOJI, "👍"),
        )

        verifySuspend(VerifyMode.exactly(0)) { notifications.cancelMessageNotification(any()) }
        verifySuspend(VerifyMode.exactly(0)) { notifications.refreshConversationAfterReply(any()) }
    }
}
