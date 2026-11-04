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
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.meshtastic.core.repository.LiveUpdateMode
import org.meshtastic.core.repository.LiveUpdatePrefs
import org.meshtastic.core.testing.FakeLiveUpdatePrefs
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LiveUpdateStopReceiverTest {
    private val prefs = FakeLiveUpdatePrefs()
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        startKoin { modules(module { single<LiveUpdatePrefs> { prefs } }) }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `the stop action turns a running live update off`() {
        prefs.setMode(LiveUpdateMode.TrackNode(42))

        LiveUpdateStopReceiver().onReceive(context, Intent(LiveUpdateStopReceiver.ACTION_STOP))

        assertEquals(LiveUpdateMode.Off, prefs.mode.value)
    }

    @Test
    fun `any other action leaves the live update running`() {
        prefs.setMode(LiveUpdateMode.MeshStatus)

        LiveUpdateStopReceiver().onReceive(context, Intent("org.meshtastic.app.SOMETHING_ELSE"))

        assertEquals(LiveUpdateMode.MeshStatus, prefs.mode.value)
    }
}
