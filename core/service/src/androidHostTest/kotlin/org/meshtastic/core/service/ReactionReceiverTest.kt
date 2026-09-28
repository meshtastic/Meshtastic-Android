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
import androidx.test.core.app.ApplicationProvider
import dev.mokkery.MockMode
import dev.mokkery.mock
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
import org.meshtastic.core.repository.RadioController
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReactionReceiverTest {

    private val radioController: RadioController = mock(MockMode.autofill)
    private val notificationManager: MeshNotificationManager = mock(MockMode.autofill)

    @Before
    fun setUp() {
        startKoin {
            modules(
                module {
                    single { radioController }
                    single { notificationManager }
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

    @Test
    fun `a thumbs-up from the notification is sent and shown in the conversation`() {
        val contactKey = "0!12345678"
        val intent =
            Intent(ReactionReceiver.REACT_ACTION)
                .putExtra(ReactionReceiver.EXTRA_CONTACT_KEY, contactKey)
                .putExtra(ReactionReceiver.EXTRA_REPLY_ID, 42)
                .putExtra(ReactionReceiver.EXTRA_EMOJI, "👍")

        ReactionReceiver().onReceive(ApplicationProvider.getApplicationContext(), intent)

        verifySuspend { radioController.sendReaction("👍", 42, contactKey) }
        verifySuspend { notificationManager.refreshConversationAfterReply(contactKey) }
    }
}
