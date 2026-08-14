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

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.model.TelemetryType
import org.meshtastic.core.repository.LocalNodeUnavailableException
import org.meshtastic.core.repository.PacketQueueRejectedException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RefreshLocalStatsActionTest {

    @Test
    fun `refresh issues no request when the node number is unknown`() = runTest {
        val requests = mutableListOf<Pair<Int, TelemetryType>>()

        requestLocalStatsRefresh(myNodeNum = null) { nodeNum, type -> requests += nodeNum to type }

        assertEquals(emptyList(), requests)
    }

    @Test
    fun `refresh attempts both telemetry requests after the first is rejected`() = runTest {
        val requests = mutableListOf<Pair<Int, TelemetryType>>()

        requestLocalStatsRefresh(myNodeNum = 123) { nodeNum, type ->
            requests += nodeNum to type
            if (type == TelemetryType.LOCAL_STATS) throw PacketQueueRejectedException("queue closed")
        }

        assertEquals(listOf(123 to TelemetryType.LOCAL_STATS, 123 to TelemetryType.DEVICE), requests)
    }

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

    @Test
    fun `unexpected failures are not swallowed`() = runTest {
        assertFailsWith<IllegalStateException> {
            runTelemetryRequestBestEffort(TelemetryType.DEVICE) { throw IllegalStateException("unexpected") }
        }
    }

    @Test
    fun `cancellation is not swallowed`() = runTest {
        assertFailsWith<CancellationException> {
            runTelemetryRequestBestEffort(TelemetryType.DEVICE) { throw CancellationException("cancelled") }
        }
    }

    @Test
    fun `successful request runs exactly once`() = runTest {
        var attempts = 0

        runTelemetryRequestBestEffort(TelemetryType.LOCAL_STATS) { attempts++ }

        assertEquals(1, attempts)
    }
}
