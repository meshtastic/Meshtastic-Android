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
import org.meshtastic.core.repository.MeshServiceNotifications
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MarkAsReadReceiverTest {

    private lateinit var context: Context
    private lateinit var notifications: RecordingNotifications

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        notifications = RecordingNotifications(mutableListOf())
        val dispatcher = UnconfinedTestDispatcher()
        startKoin {
            modules(
                module {
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
    fun `markAsRead action invokes markConversationRead`() {
        val contactKey = "0!deadbeef"
        val intent =
            Intent(context, MarkAsReadReceiver::class.java).apply {
                action = MarkAsReadReceiver.MARK_AS_READ_ACTION
                putExtra(MarkAsReadReceiver.CONTACT_KEY, contactKey)
            }

        MarkAsReadReceiver().onReceive(context, intent)

        assertEquals(listOf(contactKey), notifications.markReadCalls)
    }

    @Test
    fun `missing contactKey does not invoke markConversationRead`() {
        val intent =
            Intent(context, MarkAsReadReceiver::class.java).apply { action = MarkAsReadReceiver.MARK_AS_READ_ACTION }

        MarkAsReadReceiver().onReceive(context, intent)

        assertEquals(emptyList(), notifications.markReadCalls)
    }

    @Test
    fun `wrong action is ignored`() {
        val intent =
            Intent(context, MarkAsReadReceiver::class.java).apply {
                action = "some.other.ACTION"
                putExtra(MarkAsReadReceiver.CONTACT_KEY, "0!abcd")
            }

        MarkAsReadReceiver().onReceive(context, intent)

        assertEquals(emptyList(), notifications.markReadCalls)
    }
}
