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
package org.meshtastic.feature.widget

import kotlinx.coroutines.test.runTest
import org.meshtastic.core.model.TelemetryType
import org.meshtastic.core.repository.LocalNodeUnavailableException
import org.meshtastic.core.repository.PacketQueueRejectedException
import kotlin.test.Test
import kotlin.test.assertEquals

class RefreshLocalStatsActionTest {

    @Test
    fun `local node loss during request is handled as best effort`() = runTest {
        var attempts = 0

        runTelemetryRequestBestEffort(TelemetryType.LOCAL_STATS) {
            attempts++
            throw LocalNodeUnavailableException("Widget telemetry refresh")
        }

        assertEquals(1, attempts)
    }

    @Test
    fun `queue rejection during request is handled as best effort`() = runTest {
        var attempts = 0

        runTelemetryRequestBestEffort(TelemetryType.DEVICE) {
            attempts++
            throw PacketQueueRejectedException("queue closed")
        }

        assertEquals(1, attempts)
    }
}
