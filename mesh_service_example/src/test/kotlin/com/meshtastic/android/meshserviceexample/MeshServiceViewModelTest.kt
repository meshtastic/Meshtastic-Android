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
package com.meshtastic.android.meshserviceexample

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.meshtastic.core.service.testing.FakeIMeshService

/** Unit tests for [MeshServiceViewModel] using the [FakeIMeshService] test harness. */
class MeshServiceViewModelTest {

    @Test
    fun `test service connection updates status`() {
        val viewModel = MeshServiceViewModel()
        val fakeService = FakeIMeshService()

        viewModel.onServiceConnected(fakeService)

        assertTrue(viewModel.serviceConnectionStatus.value)
        assertEquals("fake_id", viewModel.myId.value)
        assertEquals("CONNECTED", viewModel.connectionState.value)
    }

    @Test
    fun `test service disconnection updates status`() {
        val viewModel = MeshServiceViewModel()
        viewModel.onServiceConnected(FakeIMeshService())

        viewModel.onServiceDisconnected()

        assertEquals(false, viewModel.serviceConnectionStatus.value)
    }
}
