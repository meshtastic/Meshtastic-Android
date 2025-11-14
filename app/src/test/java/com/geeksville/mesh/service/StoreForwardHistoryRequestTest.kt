/*
 * Copyright (c) 2025 Meshtastic LLC
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

package com.geeksville.mesh.service

import org.junit.Assert.assertEquals
import org.junit.Test
import org.meshtastic.proto.StoreAndForwardProtos

class StoreForwardHistoryRequestTest {

    @Test
    fun `buildStoreForwardHistoryRequest copies positive parameters`() {
        val request =
            MeshService.buildStoreForwardHistoryRequest(
                lastRequest = 42,
                historyReturnWindow = 15,
                historyReturnMax = 25,
            )

        assertEquals(StoreAndForwardProtos.StoreAndForward.RequestResponse.CLIENT_HISTORY, request.rr)
        assertEquals(42, request.history.lastRequest)
        assertEquals(15, request.history.window)
        assertEquals(25, request.history.historyMessages)
    }

    @Test
    fun `buildStoreForwardHistoryRequest omits non-positive parameters`() {
        val request =
            MeshService.buildStoreForwardHistoryRequest(lastRequest = 0, historyReturnWindow = -1, historyReturnMax = 0)

        assertEquals(StoreAndForwardProtos.StoreAndForward.RequestResponse.CLIENT_HISTORY, request.rr)
        assertEquals(0, request.history.lastRequest)
        assertEquals(0, request.history.window)
        assertEquals(0, request.history.historyMessages)
    }

    @Test
    fun `resolveHistoryRequestParameters uses config values when positive`() {
        val (window, max) = MeshService.resolveHistoryRequestParameters(window = 30, max = 10)

        assertEquals(30, window)
        assertEquals(10, max)
    }

    @Test
    fun `resolveHistoryRequestParameters falls back to defaults when non-positive`() {
        val (window, max) = MeshService.resolveHistoryRequestParameters(window = 0, max = -5)

        assertEquals(1440, window)
        assertEquals(100, max)
    }
}
