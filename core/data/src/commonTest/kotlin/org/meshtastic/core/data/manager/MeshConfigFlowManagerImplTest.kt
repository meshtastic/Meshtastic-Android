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

import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.encodeUtf8
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.NotificationPrefs
import org.meshtastic.core.repository.PlatformAnalytics
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioSessionContext
import org.meshtastic.core.repository.RadioSessionLease
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.proto.DeviceMetadata
import org.meshtastic.proto.FileInfo
import org.meshtastic.proto.HardwareModel
import org.meshtastic.proto.ModuleConfig
import org.meshtastic.proto.NodeInfo
import org.meshtastic.sdk.ConfigBundle
import org.meshtastic.sdk.ConnectionState
import org.meshtastic.sdk.NodeId
import org.meshtastic.sdk.RadioSessionEvent
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.meshtastic.proto.MyNodeInfo as ProtoMyNodeInfo

@OptIn(ExperimentalCoroutinesApi::class)
class MeshConfigFlowManagerImplTest {

    private val nodeManager = mock<NodeManager>(MockMode.autofill)
    private val nodeRepository = mock<NodeRepository>(MockMode.autofill)
    private val radioConfigRepository = mock<RadioConfigRepository>(MockMode.autofill)
    private val serviceRepository = mock<ServiceRepository>(MockMode.autofill)
    private val analytics = mock<PlatformAnalytics>(MockMode.autofill)
    private val notificationPrefs = mock<NotificationPrefs>(MockMode.autofill)
    private val radioInterfaceService = mock<RadioInterfaceService>(MockMode.autofill)

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var manager: MeshConfigFlowManagerImpl

    private val myNodeNum = 12345
    private val activeSession = RadioSessionContext(generation = 0L, address = "tcp:test-node")
    private val activeSessionFlow = MutableStateFlow<RadioSessionContext?>(activeSession)

    private val protoMyNodeInfo =
        ProtoMyNodeInfo.Builder()
            .also { wb ->
                wb.my_node_num = myNodeNum
                wb.min_app_version = 30000
                wb.device_id = "test-device".encodeUtf8()
                wb.pio_env = ""
            }
            .build()

    private val metadata =
        DeviceMetadata.Builder()
            .also { wb ->
                wb.firmware_version = "2.6.0"
                wb.hw_model = HardwareModel.HELTEC_V3
                wb.hasWifi = false
            }
            .build()

    @BeforeTest
    fun setUp() {
        every { nodeManager.nodeDBbyNodeNum } returns emptyMap()
        every { nodeManager.myNodeNum } returns MutableStateFlow(null)
        activeSessionFlow.value = activeSession
        every { radioInterfaceService.activeSession } returns activeSessionFlow
        every { radioInterfaceService.isSessionActive(any()) } calls
            {
                activeSessionFlow.value == it.args[0] as RadioSessionContext
            }
        every { radioInterfaceService.runIfSessionActive(any(), any()) } calls
            {
                val session = it.args[0] as RadioSessionContext

                @Suppress("UNCHECKED_CAST")
                val block = it.args[1] as () -> Unit
                if (activeSessionFlow.value == session) {
                    block()
                    true
                } else {
                    false
                }
            }
        everySuspend { radioInterfaceService.runWithSessionLease(any(), any()) } calls
            {
                val session = it.args[0] as RadioSessionContext

                @Suppress("UNCHECKED_CAST")
                val block = it.args[1] as (suspend (RadioSessionLease) -> Unit)
                if (activeSessionFlow.value == session) {
                    block(
                        object : RadioSessionLease {
                            override val session = session

                            override fun isCurrent() = true
                        },
                    )
                    true
                } else {
                    false
                }
            }
        every { notificationPrefs.nodeEventsAutoDisabledForEvent } returns MutableStateFlow(false)
        every { notificationPrefs.nodeEventsEnabled } returns MutableStateFlow(true)
        // autofill returns null for List<Int>, which would NPE the non-null return; individual
        // tests override this stub where they exercise install failures or migration results.
        everySuspend { nodeRepository.installConfig(any(), any()) } returns emptyList()

        manager =
            MeshConfigFlowManagerImpl(
                nodeManager = nodeManager,
                nodeRepository = nodeRepository,
                radioConfigRepository = radioConfigRepository,
                serviceStateWriter = serviceRepository,
                analytics = analytics,
                notificationPrefs = notificationPrefs,
                radioInterfaceService = radioInterfaceService,
            )
    }

    private fun snapshot(
        nodes: List<NodeInfo> = emptyList(),
        moduleConfigs: List<ModuleConfig> = emptyList(),
        files: List<FileInfo> = emptyList(),
        myInfo: ProtoMyNodeInfo? = protoMyNodeInfo,
    ) = RadioSessionEvent.Ready(
        sessionId = 1L,
        myInfo = myInfo,
        configBundle =
        ConfigBundle(
            myInfo = protoMyNodeInfo,
            metadata = metadata,
            configs = emptyList(),
            moduleConfigs = moduleConfigs,
        ),
        channels = emptyList(),
        nodes = nodes.associateBy { NodeId(it.num) },
        files = files,
    )

    @Test
    fun `SDK snapshot projects identity and capabilities`() = testScope.runTest {
        assertTrue(manager.installRadioSnapshot(activeSession, snapshot()))
        verify { nodeManager.setMyNodeNum(myNodeNum) }
        verify { nodeManager.setMyDeviceId(protoMyNodeInfo.device_id.hex()) }
        verify { nodeManager.setFirmwareVersion(metadata.firmware_version, activeSession) }
        verify { notificationPrefs.applyEventFirmwareNodeEventDefault(false) }
        verifySuspend { nodeRepository.insertMetadata(myNodeNum, metadata) }
        verify { serviceRepository.setConnectionState(ConnectionState.Connected) }
    }

    @Test
    fun `SDK snapshot installs the manifest after clearing the old one`() = testScope.runTest {
        val file = FileInfo.Builder().also { it.file_name = "firmware.bin" }.build()
        val calls = mutableListOf<String>()
        everySuspend { radioConfigRepository.clearFileManifest() } calls
            {
                calls.add("clear files")
            }
        everySuspend { radioConfigRepository.addFileInfo(file) } calls
            {
                calls.add("install file")
            }

        assertTrue(manager.installRadioSnapshot(activeSession, snapshot(files = listOf(file))))
        assertEquals(listOf("clear files", "install file"), calls)
    }

    @Test
    fun `SDK snapshot applies trusted migrations before publishing readiness`() = testScope.runTest {
        val callOrder = mutableListOf<String>()
        everySuspend { nodeRepository.installConfig(any(), any()) } calls
            {
                callOrder.add("install")
                listOf(456)
            }
        every { nodeManager.applyTrustedIdentityMigrations(listOf(456)) } calls
            {
                callOrder.add("migrate")
            }
        every { nodeManager.setNodeDbReady(true) } calls
            {
                callOrder.add("ready")
            }
        assertTrue(manager.installRadioSnapshot(activeSession, snapshot()))
        assertEquals(listOf("install", "migrate", "ready"), callOrder)
    }

    @Test
    fun `SDK snapshot applies the local node status before installing the node list`() = testScope.runTest {
        val status =
            ModuleConfig.Builder()
                .also {
                    it.statusmessage =
                        ModuleConfig.StatusMessageConfig.Builder().also { s -> s.node_status = "On duty" }.build()
                }
                .build()
        val callOrder = mutableListOf<String>()
        everySuspend { nodeManager.updateNodeStatusAndPersist(any(), any()) } calls
            { call ->
                callOrder.add("status ${call.args[0]} ${call.args[1]}")
            }
        everySuspend { nodeRepository.installConfig(any(), any()) } calls
            {
                callOrder.add("install")
                emptyList()
            }

        assertTrue(manager.installRadioSnapshot(activeSession, snapshot(moduleConfigs = listOf(status))))
        assertEquals(listOf("status $myNodeNum On duty", "install"), callOrder)
    }

    @Test
    fun `session revocation during a snapshot stops remaining persistence and readiness`() = testScope.runTest {
        val firstNode = NodeInfo.Builder().also { it.num = 100 }.build()
        val secondNode = NodeInfo.Builder().also { it.num = 200 }.build()
        every { nodeManager.installNodeInfo(firstNode) } calls { activeSessionFlow.value = null }

        assertFalse(manager.installRadioSnapshot(activeSession, snapshot(listOf(firstNode, secondNode))))

        verify(VerifyMode.exactly(1)) { nodeManager.installNodeInfo(any()) }
        verifySuspend(VerifyMode.exactly(0)) { nodeRepository.installConfig(any(), any()) }
        verify(VerifyMode.exactly(0)) { serviceRepository.setConnectionState(ConnectionState.Connected) }
    }

    @Test
    fun `a stale session cannot project identity or configuration`() = testScope.runTest {
        activeSessionFlow.value = activeSession.copy(generation = 2)
        assertFalse(manager.installRadioSnapshot(activeSession, snapshot()))
        verify(VerifyMode.exactly(0)) { nodeManager.setMyNodeNum(any()) }
        verifySuspend(VerifyMode.exactly(0)) { radioConfigRepository.clearLocalConfig() }
    }

    @Test
    fun `a snapshot without my_info installs nothing and reports not installed`() = testScope.runTest {
        assertFalse(manager.installRadioSnapshot(activeSession, snapshot(myInfo = null)))
        verify(VerifyMode.exactly(0)) { nodeManager.setMyNodeNum(any()) }
        verifySuspend(VerifyMode.exactly(0)) { radioConfigRepository.clearLocalConfig() }
        verify(VerifyMode.exactly(0)) { serviceRepository.setConnectionState(ConnectionState.Connected) }
    }

    @Test
    fun `refreshNodeInfo delegates to the SDK for the active session`() {
        every { radioInterfaceService.requestNodeDbRefresh(activeSession) } returns true
        assertTrue(manager.refreshNodeInfo())
        verify { radioInterfaceService.requestNodeDbRefresh(activeSession) }
    }

    @Test
    fun `SDK node refresh reinstalls the list without clearing config or publishing readiness again`() =
        testScope.runTest {
            assertTrue(manager.installRadioSnapshot(activeSession, snapshot()))
            manager.installRefreshedNodes(activeSession, emptyList())

            verifySuspend(VerifyMode.exactly(2)) { nodeRepository.installConfig(any(), any()) }
            verifySuspend(VerifyMode.exactly(1)) { radioConfigRepository.clearLocalConfig() }
            verify(VerifyMode.exactly(1)) { serviceRepository.setConnectionState(ConnectionState.Connected) }
        }
}
