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
import androidx.test.core.app.ApplicationProvider
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
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
import org.meshtastic.core.model.service.ServiceAction
import org.meshtastic.core.repository.MeshServiceNotifications
import org.meshtastic.core.repository.ServiceRepository
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReactionReceiverTest {

    private lateinit var context: Context
    private lateinit var notifications: RecordingNotifications
    private lateinit var serviceRepository: ServiceRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        notifications = RecordingNotifications(mutableListOf())
        serviceRepository = mock(MockMode.autofill)
        val dispatcher = UnconfinedTestDispatcher()
        startKoin {
            modules(
                module {
                    single<ServiceRepository> { serviceRepository }
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
    fun `reaction dispatches ServiceAction and marks conversation read`() {
        val contactKey = "0!cafebabe"
        val emoji = "👍"
        val replyId = 42
        everySuspend { serviceRepository.onServiceAction(any()) } returns Unit

        val intent =
            Intent(context, ReactionReceiver::class.java).apply {
                action = ReactionReceiver.REACT_ACTION
                putExtra(ReactionReceiver.EXTRA_CONTACT_KEY, contactKey)
                putExtra(ReactionReceiver.EXTRA_EMOJI, emoji)
                putExtra(ReactionReceiver.EXTRA_REPLY_ID, replyId)
            }

        ReactionReceiver().onReceive(context, intent)

        verifySuspend(VerifyMode.exactly(1)) {
            serviceRepository.onServiceAction(ServiceAction.Reaction(emoji, replyId, contactKey))
        }
        assertEquals(listOf(contactKey), notifications.markReadCalls)
    }

    @Test
    fun `reaction does not markRead when ServiceAction dispatch throws`() {
        val contactKey = "0!feedface"
        val throwingRepo = mock<ServiceRepository>(MockMode.autofill)
        everySuspend { throwingRepo.onServiceAction(any()) } calls { throw IllegalStateException("boom") }
        stopKoin()
        val dispatcher = UnconfinedTestDispatcher()
        startKoin {
            modules(
                module {
                    single<ServiceRepository> { throwingRepo }
                    single<MeshServiceNotifications> { notifications }
                    single { CoroutineDispatchers(io = dispatcher, main = dispatcher, default = dispatcher) }
                },
            )
        }

        val intent =
            Intent(context, ReactionReceiver::class.java).apply {
                action = ReactionReceiver.REACT_ACTION
                putExtra(ReactionReceiver.EXTRA_CONTACT_KEY, contactKey)
                putExtra(ReactionReceiver.EXTRA_REACTION, "🎉")
                putExtra(ReactionReceiver.EXTRA_PACKET_ID, 7)
            }

        ReactionReceiver().onReceive(context, intent)

        assertEquals(emptyList(), notifications.markReadCalls)
    }

    @Test
    fun `reaction without contactKey is dropped`() {
        val intent =
            Intent(context, ReactionReceiver::class.java).apply {
                action = ReactionReceiver.REACT_ACTION
                putExtra(ReactionReceiver.EXTRA_EMOJI, "👍")
            }

        ReactionReceiver().onReceive(context, intent)

        assertEquals(emptyList(), notifications.markReadCalls)
    }

    @Test
    fun `wrong action is ignored`() {
        val intent =
            Intent(context, ReactionReceiver::class.java).apply {
                action = "other.ACTION"
                putExtra(ReactionReceiver.EXTRA_CONTACT_KEY, "0!abcd")
                putExtra(ReactionReceiver.EXTRA_EMOJI, "👍")
            }

        ReactionReceiver().onReceive(context, intent)

        assertEquals(emptyList(), notifications.markReadCalls)
    }
}
