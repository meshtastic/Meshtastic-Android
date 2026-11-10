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
package org.meshtastic.core.datastore

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.datastore.di.CoreLocalConfigDataStore
import org.meshtastic.proto.Config
import org.meshtastic.proto.Config.TransportConfig.Transport
import org.meshtastic.proto.LocalConfig
import kotlin.test.Test
import kotlin.test.assertEquals

class LocalConfigDataSourceTest {

    private class InMemoryLocalConfigStore : CoreLocalConfigDataStore {
        override val data = MutableStateFlow(LocalConfig.Builder().build())

        override suspend fun updateData(transform: suspend (t: LocalConfig) -> LocalConfig) =
            transform(data.value).also { data.value = it }
    }

    @Test
    fun `the transport section is stored and leaves the other sections alone`() = runTest {
        val dataSource = LocalConfigDataSource(InMemoryLocalConfigStore())
        val network = Config.NetworkConfig.Builder().also { it.wifi_enabled = true }.build()
        val transport =
            Config.TransportConfig.Builder()
                .also { it.enabled = listOf(Transport.TRANSPORT_LORA, Transport.TRANSPORT_MULTICAST_UDP) }
                .build()

        dataSource.setLocalConfig(Config.Builder().also { it.network = network }.build())
        dataSource.setLocalConfig(Config.Builder().also { it.transport = transport }.build())

        val stored = dataSource.localConfigFlow.first()
        assertEquals(transport, stored.transport)
        assertEquals(network, stored.network)
    }
}
