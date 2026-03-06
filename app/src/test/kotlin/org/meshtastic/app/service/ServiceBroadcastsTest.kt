/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package org.meshtastic.app.service

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.repository.ServiceRepository
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class ServiceBroadcastsTest {

    private lateinit var context: Context
    private val serviceRepository: ServiceRepository = mockk(relaxed = true)
    private lateinit var broadcasts: ServiceBroadcasts

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        broadcasts = ServiceBroadcasts(context, serviceRepository)
    }

    @Test
    fun `broadcastConnection sends uppercase state string for ATAK`() {
        every { serviceRepository.connectionState } returns MutableStateFlow(ConnectionState.Connected)

        broadcasts.broadcastConnection()

        val shadowApp = shadowOf(context as Application)
        val intent = shadowApp.broadcastIntents.find { it.action == ACTION_MESH_CONNECTED }
        assertEquals("CONNECTED", intent?.getStringExtra(EXTRA_CONNECTED))
    }

    @Test
    fun `broadcastConnection sends legacy connection intent`() {
        every { serviceRepository.connectionState } returns MutableStateFlow(ConnectionState.Connected)

        broadcasts.broadcastConnection()

        val shadowApp = shadowOf(context as Application)
        val intent = shadowApp.broadcastIntents.find { it.action == ACTION_CONNECTION_CHANGED }
        assertEquals("CONNECTED", intent?.getStringExtra(EXTRA_CONNECTED))
        assertEquals(true, intent?.getBooleanExtra("connected", false))
    }
}
