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
package org.meshtastic.core.data.manager

import kotlinx.coroutines.test.TestScope
import org.meshtastic.core.testing.FakeRadioInterfaceService
import org.meshtastic.sdk.InMemoryStorageProvider
import org.meshtastic.sdk.RadioClient
import org.meshtastic.sdk.TransportIdentity
import org.meshtastic.sdk.testing.FakeRadioTransport

/** A real SDK client handshaken over the SDK's scripted transport, published as [radio]'s active session client. */
internal class SdkClientFixture(
    val transport: FakeRadioTransport,
    val client: RadioClient,
    val radio: FakeRadioInterfaceService,
)

internal suspend fun TestScope.sdkClientFixture(nodeNum: Int): SdkClientFixture {
    val transport = FakeRadioTransport(TransportIdentity("fake:a1"), autoHandshake = true, nodeNum = nodeNum)
    val client =
        RadioClient.Builder()
            .transport(transport)
            .storage(InMemoryStorageProvider())
            .coroutineContext(backgroundScope.coroutineContext)
            .autoSyncTimeOnConnect(false)
            .build()
    client.connect()
    val radio =
        FakeRadioInterfaceService(backgroundScope).apply {
            setDeviceAddress("tselected")
            connect()
            this.client = client
        }
    return SdkClientFixture(transport, client, radio)
}
