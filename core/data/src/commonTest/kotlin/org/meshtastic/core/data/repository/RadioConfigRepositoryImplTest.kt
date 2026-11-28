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
package org.meshtastic.core.data.repository

import app.cash.turbine.test
import dev.mokkery.MockMode
import dev.mokkery.mock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.data.datasource.SwitchingChannelSetDataSource
import org.meshtastic.core.datastore.LocalConfigDataSource
import org.meshtastic.core.datastore.ModuleConfigDataSource
import org.meshtastic.core.datastore.di.CoreLocalConfigDataStore
import org.meshtastic.core.datastore.di.CoreModuleConfigDataStore
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.testing.FakeDatabaseProvider
import org.meshtastic.core.testing.FakeRadioInterfaceService
import org.meshtastic.proto.DeviceUIConfig
import org.meshtastic.proto.FileInfo
import org.meshtastic.proto.FromRadio
import org.meshtastic.proto.LoRaRegionPresetMap
import org.meshtastic.sdk.InMemoryStorageProvider
import org.meshtastic.sdk.RadioClient
import org.meshtastic.sdk.TransportIdentity
import org.meshtastic.sdk.testing.FakeRadioTransport
import org.meshtastic.sdk.testing.toFrame
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class RadioConfigRepositoryImplTest {

    private val nodeDB = mock<NodeRepository>(MockMode.autofill)

    // These data sources are concrete wrappers -- construct them with test doubles. Channels are now stored per-device
    // via SwitchingChannelSetDataSource (DatabaseProvider), the others still wrap a DataStore<T>. None are touched by
    // the session-derived flows under test here. The channel source needs a real
    // provider rather than an autofill mock: it composes its DAO flow at construction, and a mock returns null for the
    // non-null Flow.
    private val dbProvider = FakeDatabaseProvider()

    @AfterTest fun tearDown() = dbProvider.close()

    private val channelSetDataSource =
        SwitchingChannelSetDataSource(
            dbProvider,
            CoroutineDispatchers(
                main = Dispatchers.Unconfined,
                io = Dispatchers.Unconfined,
                default = Dispatchers.Unconfined,
            ),
        )
    private val localConfigDataSource = LocalConfigDataSource(mock<CoreLocalConfigDataStore>(MockMode.autofill))
    private val moduleConfigDataSource = ModuleConfigDataSource(mock<CoreModuleConfigDataStore>(MockMode.autofill))

    private fun createRepository(radio: RadioInterfaceService = FakeRadioInterfaceService()) =
        RadioConfigRepositoryImpl(nodeDB, channelSetDataSource, localConfigDataSource, moduleConfigDataSource, radio)

    private suspend fun TestScope.radioWithSdkBundle(): FakeRadioInterfaceService {
        val frames =
            listOf(
                FromRadio.Builder().also { it.deviceuiConfig = SENT_UI_CONFIG }.build().toFrame(),
                FromRadio.Builder().also { it.region_presets = SENT_PRESETS }.build().toFrame(),
                FromRadio.Builder().also { it.fileInfo = SENT_FILE }.build().toFrame(),
            )
        val transport =
            FakeRadioTransport(
                identity = TransportIdentity("fake:cfg"),
                frames = frames,
                autoHandshake = true,
                autoQueueStatus = true,
                nodeNum = 1,
            )
        val client =
            RadioClient.Builder()
                .transport(transport)
                .storage(InMemoryStorageProvider())
                .autoSyncTimeOnConnect(false)
                .coroutineContext(backgroundScope.coroutineContext)
                .rpcTimeout(60.seconds)
                .sendAckTimeout(60.seconds)
                .build()
        client.connect()
        runCurrent()
        return FakeRadioInterfaceService(backgroundScope).also { radio ->
            radio.client = client
            radio.setDeviceAddress("tselected")
            radio.connect()
        }
    }

    @Test
    fun `device UI config and region presets come from the active session's SDK bundle`() = runTest {
        val repository = createRepository(radioWithSdkBundle())

        assertEquals(SENT_UI_CONFIG, repository.deviceUIConfigFlow.first())
        assertEquals(SENT_PRESETS, repository.loraRegionPresetMapFlow.first())
    }

    @Test
    fun `the flows are null again once the session ends`() = runTest {
        val radio = radioWithSdkBundle()
        val repository = createRepository(radio)

        repository.deviceUIConfigFlow.test {
            assertEquals(SENT_UI_CONFIG, awaitItem())
            radio.disconnect()
            assertNull(awaitItem())
        }
    }

    @Test
    fun `the file manifest follows the session and empties when it ends`() = runTest {
        val radio = radioWithSdkBundle()
        val repository = createRepository(radio)

        repository.fileManifestFlow.test {
            assertEquals(listOf(SENT_FILE), awaitItem())
            radio.disconnect()
            assertEquals(emptyList<FileInfo>(), awaitItem())
        }
    }

    private companion object {
        val SENT_UI_CONFIG: DeviceUIConfig = DeviceUIConfig.Builder().also { it.screen_brightness = 77 }.build()
        val SENT_PRESETS: LoRaRegionPresetMap = LoRaRegionPresetMap.Builder().build()
        val SENT_FILE: FileInfo =
            FileInfo.Builder()
                .also {
                    it.file_name = "a.uf2"
                    it.size_bytes = 10
                }
                .build()
    }
}
