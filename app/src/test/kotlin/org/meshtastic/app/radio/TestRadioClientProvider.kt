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
package org.meshtastic.app.radio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.meshtastic.sdk.RadioClient
import org.meshtastic.sdk.TransportIdentity
import org.meshtastic.sdk.testing.FakeRadioTransport
import org.meshtastic.sdk.testing.InMemoryStorageProvider
import kotlin.coroutines.CoroutineContext

/**
 * Test-only RadioClient setup using FakeRadioTransport.
 * Provides deterministic handshake and packet injection for integration tests.
 */
class TestRadioClientProvider(
    val nodeNum: Int = 1,
    coroutineContext: CoroutineContext = SupervisorJob() + Dispatchers.Default,
) {
    val transport = FakeRadioTransport(
        identity = TransportIdentity("fake:test-radio-provider"),
        autoHandshake = true,
        nodeNum = nodeNum,
    )

    val client: RadioClient = RadioClient.Builder()
        .transport(transport)
        .storage(InMemoryStorageProvider())
        .autoSyncTimeOnConnect(false)
        .coroutineContext(coroutineContext)
        .build()

    suspend fun connect() {
        client.connect()
    }

    suspend fun disconnect() {
        client.disconnect()
    }
}
