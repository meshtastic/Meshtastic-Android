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

import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode.Companion.exactly
import dev.mokkery.verifySuspend
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.common.database.DatabaseManager
import org.meshtastic.core.repository.AdminEditScope
import org.meshtastic.core.repository.CommandSender
import org.meshtastic.core.repository.MeshDataHandler
import org.meshtastic.core.repository.MeshLocationManager
import org.meshtastic.core.repository.MeshMessageProcessor
import org.meshtastic.core.repository.MeshPrefs
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.NotificationManager
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.PlatformAnalytics
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.repository.UiPrefs
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RadioControllerRestoreTest {
    private class Fixture {
        val commandSender: CommandSender = mock(MockMode.autofill)
        val nodeManager: NodeManager = mock(MockMode.autofill)
        val radioInterfaceService: RadioInterfaceService = mock(MockMode.autofill)
        val meshPrefs: MeshPrefs = mock(MockMode.autofill)
        val databaseManager: DatabaseManager = mock(MockMode.autofill)

        private val serviceRepository: ServiceRepository = ServiceRepositoryImpl()
        private val nodeRepository: NodeRepository = mock(MockMode.autofill)
        private val locationManager: MeshLocationManager = mock(MockMode.autofill)
        private val packetRepository: PacketRepository = mock(MockMode.autofill)
        private val dataHandler: MeshDataHandler = mock(MockMode.autofill)
        private val analytics: PlatformAnalytics = mock(MockMode.autofill)
        private val uiPrefs: UiPrefs = mock(MockMode.autofill)
        private val notificationManager: NotificationManager = mock(MockMode.autofill)
        private val messageProcessor: MeshMessageProcessor = mock(MockMode.autofill)
        private val radioConfigRepository: RadioConfigRepository = mock(MockMode.autofill)

        fun create(
            scope: CoroutineScope,
            deviceAddress: MutableStateFlow<String?> = MutableStateFlow(null),
        ): RadioControllerImpl {
            every { nodeManager.myNodeNum } returns MutableStateFlow(1234)
            every { nodeManager.myDeviceId } returns MutableStateFlow(null)
            every { nodeManager.connectionIdentity } returns MutableStateFlow(null)
            every { radioInterfaceService.sessionGeneration } returns MutableStateFlow(0L)
            every { radioInterfaceService.activeSession } returns MutableStateFlow(null)
            every { meshPrefs.deviceAddress } returns deviceAddress
            every { radioInterfaceService.getDeviceAddress() } calls { deviceAddress.value }
            return RadioControllerImpl(
                serviceRepository = serviceRepository,
                nodeRepository = nodeRepository,
                commandSender = commandSender,
                nodeManager = nodeManager,
                radioInterfaceService = radioInterfaceService,
                locationManager = locationManager,
                packetRepository = lazy { packetRepository },
                dataHandler = lazy { dataHandler },
                analytics = analytics,
                meshPrefs = meshPrefs,
                uiPrefs = uiPrefs,
                databaseManager = databaseManager,
                notificationManager = notificationManager,
                messageProcessor = lazy { messageProcessor },
                radioConfigRepository = radioConfigRepository,
                scope = scope,
            )
        }
    }

    @Test
    fun restoreLocalConfigurationRejectsStaleDeviceOwnershipBeforeWriting() = runTest {
        val fixture = Fixture()
        val controller = fixture.create(backgroundScope)
        every { fixture.radioInterfaceService.getDeviceAddress() } returns "x:CURRENT"

        val restored =
            controller.restoreLocalConfiguration(
                expectedDeviceAddress = "x:STALE",
                config = Config(lora = Config.LoRaConfig(use_preset = true)),
            )

        assertFalse(restored)
        verifySuspend(exactly(0)) { fixture.commandSender.sendAdmin(any(), any(), any(), any()) }
    }

    @Test
    fun restoreLocalConfigurationRejectsMissingDeviceOwnershipBeforeWriting() = runTest {
        val fixture = Fixture()
        val controller = fixture.create(backgroundScope)

        val restored =
            controller.restoreLocalConfiguration(
                expectedDeviceAddress = null,
                config = Config(lora = Config.LoRaConfig(use_preset = true)),
            )

        assertFalse(restored)
        verifySuspend(exactly(0)) { fixture.commandSender.sendAdmin(any(), any(), any(), any()) }
    }

    @Test
    fun restoreLocalConfigurationUsesOneEditTransactionInChannelThenConfigOrder() = runTest {
        val primaryChannel = Channel(index = 0, role = Channel.Role.PRIMARY, settings = ChannelSettings())
        val config = Config(lora = Config.LoRaConfig(use_preset = true))
        val operations = mutableListOf<String>()
        val editScope = mock<AdminEditScope>(MockMode.autofill)
        everySuspend { editScope.setChannel(any()) } calls { operations += "channel" }
        everySuspend { editScope.setConfig(any()) } calls { operations += "config" }
        var transactions = 0

        val restored =
            restoreLocalConfigurationIfOwned(
                expectedDeviceAddress = "x:CURRENT",
                currentDeviceAddress = "x:CURRENT",
                config = config,
                primaryChannel = primaryChannel,
                editLocalSettings = { block ->
                    transactions++
                    editScope.block()
                },
            )

        assertTrue(restored)
        assertEquals(1, transactions)
        assertEquals(listOf("channel", "config"), operations)
        verifySuspend(exactly(1)) { editScope.setChannel(primaryChannel) }
        verifySuspend(exactly(1)) { editScope.setConfig(config) }
    }

    @Test
    fun restoreLocalConfigurationWaitsForDeviceSelectionAndRejectsThePreviousDevice() = runTest {
        val fixture = Fixture()
        val selectedAddress = MutableStateFlow<String?>("x:OLD")
        val controller = fixture.create(backgroundScope, selectedAddress)
        val switchStarted = CompletableDeferred<Unit>()
        val releaseSwitch = CompletableDeferred<Unit>()
        everySuspend { fixture.databaseManager.switchActiveDatabase("x:NEW") } calls
            {
                switchStarted.complete(Unit)
                releaseSwitch.await()
            }
        every { fixture.meshPrefs.setDeviceAddress("x:NEW") } calls { selectedAddress.value = "x:NEW" }
        every { fixture.radioInterfaceService.setDeviceAddress("x:NEW") } calls
            {
                selectedAddress.value = "x:NEW"
                true
            }

        val selection = launch { controller.setDeviceAddress("x:NEW") }
        switchStarted.await()
        val restore = async {
            controller.restoreLocalConfiguration(
                expectedDeviceAddress = "x:OLD",
                config = Config(lora = Config.LoRaConfig(use_preset = true)),
            )
        }
        runCurrent()

        assertFalse(restore.isCompleted, "restore must wait behind the in-flight device selection")
        verifySuspend(exactly(0)) { fixture.commandSender.sendAdmin(any(), any(), any(), any()) }

        releaseSwitch.complete(Unit)
        selection.join()

        assertFalse(restore.await())
        verifySuspend(exactly(0)) { fixture.commandSender.sendAdmin(any(), any(), any(), any()) }
    }
}
