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
package org.meshtastic.feature.settings.radio

import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.capture.capture
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verify.VerifyMode.Companion.exactly
import dev.mokkery.verifySuspend
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import okio.ByteString.Companion.encodeUtf8
import org.meshtastic.core.domain.usecase.settings.AdminActionsUseCase
import org.meshtastic.core.domain.usecase.settings.ExportProfileUseCase
import org.meshtastic.core.domain.usecase.settings.ImportProfileUseCase
import org.meshtastic.core.domain.usecase.settings.ImportSecurityConfigUseCase
import org.meshtastic.core.domain.usecase.settings.InstallProfileUseCase
import org.meshtastic.core.model.DeviceHardware
import org.meshtastic.core.model.MqttProbeStatus
import org.meshtastic.core.model.MyNodeInfo
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.Position
import org.meshtastic.core.model.getStringResFrom
import org.meshtastic.core.model.util.MalformedMeshtasticUrlException
import org.meshtastic.core.repository.AdminController
import org.meshtastic.core.repository.AnalyticsPrefs
import org.meshtastic.core.repository.DeviceHardwareRepository
import org.meshtastic.core.repository.FileService
import org.meshtastic.core.repository.HomoglyphPrefs
import org.meshtastic.core.repository.LocationRepository
import org.meshtastic.core.repository.LocationService
import org.meshtastic.core.repository.MapConsentPrefs
import org.meshtastic.core.repository.MqttManager
import org.meshtastic.core.repository.NodeRestartTracker
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.RadioSessionContext
import org.meshtastic.core.repository.SecurityKeyBackupStore
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.repository.StoredSecurityKeys
import org.meshtastic.core.repository.UiPrefs
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.UiText
import org.meshtastic.core.resources.not_connected
import org.meshtastic.core.resources.timeout
import org.meshtastic.core.resources.transparent_mode_unsupported
import org.meshtastic.core.testing.FakeLockdownCoordinator
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.ui.util.SnackbarManager
import org.meshtastic.feature.settings.navigation.ConfigRoute
import org.meshtastic.feature.settings.navigation.ModuleRoute
import org.meshtastic.feature.settings.radio.component.LoraSaveTransparentMode
import org.meshtastic.feature.settings.radio.component.loRaBandwidthSelection
import org.meshtastic.feature.settings.radio.component.saveLora
import org.meshtastic.proto.AdminMessage
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config
import org.meshtastic.proto.DeviceConnectionStatus
import org.meshtastic.proto.DeviceMetadata
import org.meshtastic.proto.DeviceProfile
import org.meshtastic.proto.ExcludedModules
import org.meshtastic.proto.HamParameters
import org.meshtastic.proto.HardwareModel
import org.meshtastic.proto.LoRaPresetGroup
import org.meshtastic.proto.LoRaRegionPresetMap
import org.meshtastic.proto.LocalConfig
import org.meshtastic.proto.LocalModuleConfig
import org.meshtastic.proto.ModuleConfig
import org.meshtastic.proto.ModuleConfig.MeshBeaconConfig
import org.meshtastic.proto.Routing
import org.meshtastic.proto.User
import org.meshtastic.sdk.AdminResult
import org.meshtastic.sdk.ConnectionState
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration

@OptIn(ExperimentalCoroutinesApi::class)
class RadioConfigViewModelTest {

    @Test
    fun `route read fan-out flags match multi-request loaders`() {
        assertEquals(
            setOf(ConfigRoute.CHANNELS, ConfigRoute.LORA, ConfigRoute.NETWORK, ConfigRoute.USER),
            ConfigRoute.entries.filter(ConfigRoute::hasReadFanOut).toSet(),
        )
        assertEquals(
            setOf(ModuleRoute.CANNED_MESSAGE, ModuleRoute.EXT_NOTIFICATION, ModuleRoute.MESH_BEACON),
            ModuleRoute.entries.filter(ModuleRoute::hasReadFanOut).toSet(),
        )
    }

    @Test
    fun `USER route reads the status message config on capable firmware`() = runTest {
        val node =
            Node(
                num = 123,
                user = User.Builder().also { wb -> wb.id = "!123" }.build(),
                metadata = DeviceMetadata.Builder().also { wb -> wb.firmware_version = "2.8.0" }.build(),
            )
        nodeRepository.setNodes(listOf(node))
        viewModel = createViewModel(destNum = 123)

        viewModel.setResponseStateLoading(ConfigRoute.USER)
        advanceUntilIdle()

        verifySuspend {
            adminController.getModuleConfig(123, AdminMessage.ModuleConfigType.STATUSMESSAGE_CONFIG.value)
        }
    }

    @Test
    fun `USER route skips the status message config on firmware without the module`() = runTest {
        val node =
            Node(
                num = 123,
                user = User.Builder().also { wb -> wb.id = "!123" }.build(),
                metadata = DeviceMetadata.Builder().also { wb -> wb.firmware_version = "2.7.19" }.build(),
            )
        nodeRepository.setNodes(listOf(node))
        viewModel = createViewModel(destNum = 123)

        viewModel.setResponseStateLoading(ConfigRoute.USER)
        advanceUntilIdle()

        verifySuspend(exactly(0)) { adminController.getModuleConfig(any(), any()) }
    }

    @Test
    fun `USER route skips the status message config when the firmware compiled the module out`() = runTest {
        val metadata =
            DeviceMetadata.Builder()
                .also { wb ->
                    wb.firmware_version = "2.8.0"
                    wb.excluded_modules = ExcludedModules.STATUSMESSAGE_CONFIG.value
                }
                .build()
        val node = Node(num = 123, user = User.Builder().also { wb -> wb.id = "!123" }.build(), metadata = metadata)
        nodeRepository.setNodes(listOf(node))
        viewModel = createViewModel(destNum = 123)

        viewModel.setResponseStateLoading(ConfigRoute.USER)
        advanceUntilIdle()

        verifySuspend { adminController.getOwner(123) }
        verifySuspend(exactly(0)) { adminController.getModuleConfig(any(), any()) }
    }

    @Test
    fun `MESH_BEACON route on a remote node reads the beacon module config plus LoRa config and channel 0`() = runTest {
        val localNode = Node(num = 100, user = User.Builder().also { wb -> wb.id = "!100" }.build())
        val remoteNode =
            Node(
                num = 456,
                user = User.Builder().also { wb -> wb.id = "!456" }.build(),
                metadata = DeviceMetadata.Builder().also { wb -> wb.firmware_version = "2.8.0" }.build(),
            )
        nodeRepository.setNodes(listOf(localNode, remoteNode))
        nodeRepository.setMyNodeInfo(myNodeInfo(myNodeNum = 100))
        viewModel = createViewModel(destNum = 456)

        viewModel.setResponseStateLoading(ModuleRoute.MESH_BEACON)
        advanceUntilIdle()

        // Pins ModuleRoute.MESH_BEACON.type: the default (0) would fetch MQTT_CONFIG instead.
        verifySuspend {
            adminController.getModuleConfig(456, AdminMessage.ModuleConfigType.MESHBEACON_CONFIG.value)
        }
        verifySuspend(exactly(0)) {
            adminController.getModuleConfig(456, AdminMessage.ModuleConfigType.MQTT_CONFIG.value)
        }
        // Pins the loadModuleRoute fan-out that fills the editor's region gate and channel picker.
        verifySuspend { adminController.getChannel(456, 0) }
        verifySuspend { adminController.getConfig(456, AdminMessage.ConfigType.LORA_CONFIG.value) }
    }

    @Test
    fun `MESH_BEACON route skips the module get on firmware without the module but still reads LoRa and channel 0`() =
        runTest {
            val localNode = Node(num = 100, user = User.Builder().also { wb -> wb.id = "!100" }.build())
            val remoteNode =
                Node(
                    num = 456,
                    user = User.Builder().also { wb -> wb.id = "!456" }.build(),
                    metadata = DeviceMetadata.Builder().also { wb -> wb.firmware_version = "2.7.19" }.build(),
                )
            nodeRepository.setNodes(listOf(localNode, remoteNode))
            nodeRepository.setMyNodeInfo(myNodeInfo(myNodeNum = 100))
            viewModel = createViewModel(destNum = 456)

            viewModel.setResponseStateLoading(ModuleRoute.MESH_BEACON)
            advanceUntilIdle()

            // Pins the supportsMeshBeacon gate: firmware without the module never answers, so no get is issued. The
            // module list already hides the route on such firmware; this keeps the view model safe if reached directly.
            verifySuspend(exactly(0)) { adminController.getModuleConfig(any(), any()) }
            verifySuspend { adminController.getChannel(456, 0) }
            verifySuspend { adminController.getConfig(456, AdminMessage.ConfigType.LORA_CONFIG.value) }
        }

    @Test
    fun `MESH_BEACON remote read completes only after all three responses land`() = runTest {
        val localNode = Node(num = 100, user = User.Builder().also { wb -> wb.id = "!100" }.build())
        val remoteNode =
            Node(
                num = 456,
                user = User.Builder().also { wb -> wb.id = "!456" }.build(),
                metadata = DeviceMetadata.Builder().also { wb -> wb.firmware_version = "2.8.0" }.build(),
            )
        val beacon =
            MeshBeaconConfig.Builder()
                .also { wb ->
                    wb.broadcast_offer_region = Config.LoRaConfig.RegionCode.US
                    wb.broadcast_message = "join us"
                }
                .build()
        val channelReply = CompletableDeferred<AdminResult<Channel>>()
        val loraReply = CompletableDeferred<AdminResult<Config>>()
        val beaconReply = CompletableDeferred<AdminResult<ModuleConfig>>()
        everySuspend { adminController.getChannel(any(), any()) } calls { channelReply.await() }
        everySuspend { adminController.getConfig(any(), any()) } calls { loraReply.await() }
        everySuspend { adminController.getModuleConfig(any(), any()) } calls { beaconReply.await() }
        nodeRepository.setNodes(listOf(localNode, remoteNode))
        nodeRepository.setMyNodeInfo(myNodeInfo(myNodeNum = 100))
        viewModel = createViewModel(destNum = 456)

        viewModel.setResponseStateLoading(ModuleRoute.MESH_BEACON)
        runCurrent()
        assertEquals(3, (viewModel.radioConfigState.value.responseState as ResponseState.Loading).total)

        channelReply.complete(
            AdminResult.Success(
                Channel.Builder()
                    .also { wb ->
                        wb.index = 0
                        wb.role = Channel.Role.PRIMARY
                        wb.settings = ChannelSettings.Builder().also { wb -> wb.name = "LongFast" }.build()
                    }
                    .build(),
            ),
        )
        loraReply.complete(
            AdminResult.Success(
                Config.Builder()
                    .also { wb ->
                        wb.lora =
                            Config.LoRaConfig.Builder()
                                .also { wb -> wb.region = Config.LoRaConfig.RegionCode.US }
                                .build()
                    }
                    .build(),
            ),
        )
        runCurrent()
        assertTrue(
            viewModel.radioConfigState.value.responseState is ResponseState.Loading,
            "two of three responses must not complete the read",
        )
        assertEquals(3, (viewModel.radioConfigState.value.responseState as ResponseState.Loading).total)

        beaconReply.complete(AdminResult.Success(ModuleConfig.Builder().also { wb -> wb.mesh_beacon = beacon }.build()))
        runCurrent()

        assertEquals(ResponseState.Empty, viewModel.radioConfigState.value.responseState)
        assertEquals(beacon, viewModel.radioConfigState.value.moduleConfig.mesh_beacon)
        assertEquals(Config.LoRaConfig.RegionCode.US, viewModel.radioConfigState.value.radioConfig.lora?.region)
        // Pins the getChannel(0) half of the fan-out: the primary channel is what the offer picker renders.
        assertEquals(listOf("LongFast"), viewModel.radioConfigState.value.channelList.map { it.name })
    }

    @Test
    fun `ModuleConfigResponse carrying mesh_beacon is merged and survives a later response without it`() = runTest {
        val node = Node(num = 123, user = User.Builder().also { wb -> wb.id = "!123" }.build())
        nodeRepository.setNodes(listOf(node))
        viewModel = createViewModel()

        val beacon =
            MeshBeaconConfig.Builder()
                .also { wb ->
                    wb.broadcast_offer_region = Config.LoRaConfig.RegionCode.EU_868
                    wb.broadcast_message = "hi"
                }
                .build()
        everySuspend { adminController.getModuleConfig(any(), any()) } returns
            AdminResult.Success(ModuleConfig.Builder().also { wb -> wb.mesh_beacon = beacon }.build())
        viewModel.setResponseStateLoading(ModuleRoute.TELEMETRY)
        runCurrent()
        // Pins the mesh_beacon merge line: without it the reply is dropped and the editor renders defaults.
        assertEquals(beacon, viewModel.radioConfigState.value.moduleConfig.mesh_beacon)

        everySuspend { adminController.getModuleConfig(any(), any()) } returns
            AdminResult.Success(
                ModuleConfig.Builder()
                    .also { wb ->
                        wb.telemetry =
                            ModuleConfig.TelemetryConfig.Builder()
                                .also { wb -> wb.device_update_interval = 300 }
                                .build()
                    }
                    .build(),
            )
        viewModel.setResponseStateLoading(ModuleRoute.TELEMETRY)
        runCurrent()
        // Pins the `?: state.moduleConfig.mesh_beacon` fallback: a reply for another module keeps the beacon config.
        assertEquals(beacon, viewModel.radioConfigState.value.moduleConfig.mesh_beacon)
        assertEquals(300, viewModel.radioConfigState.value.moduleConfig.telemetry?.device_update_interval)
    }

    private val testDispatcher = UnconfinedTestDispatcher()

    private val radioConfigRepository: RadioConfigRepository = mock(MockMode.autofill)
    private val serviceRepository: ServiceRepository = mock(MockMode.autofill)
    private val nodeRepository = FakeNodeRepository()
    private val locationRepository: LocationRepository = mock(MockMode.autofill)
    private val mapConsentPrefs: MapConsentPrefs = mock(MockMode.autofill)
    private val analyticsPrefs: AnalyticsPrefs = mock(MockMode.autofill)
    private val homoglyphEncodingPrefs: HomoglyphPrefs = mock(MockMode.autofill)

    private val importProfileUseCase: ImportProfileUseCase = mock(MockMode.autofill)
    private val exportProfileUseCase: ExportProfileUseCase = mock(MockMode.autofill)
    private val importSecurityConfigUseCase: ImportSecurityConfigUseCase = mock(MockMode.autofill)
    private val installProfileUseCase: InstallProfileUseCase = mock(MockMode.autofill)
    private val adminController: AdminController = mock(MockMode.autofill)
    private val adminActionsUseCase: AdminActionsUseCase = mock(MockMode.autofill)
    private val deviceHardwareRepository: DeviceHardwareRepository = mock(MockMode.autofill)
    private val locationService: LocationService = mock(MockMode.autofill)
    private val fileService: FileService = mock(MockMode.autofill)
    private val mqttManager: MqttManager = mock(MockMode.autofill)
    private val uiPrefs: UiPrefs = mock(MockMode.autofill)
    private val securityKeyBackupStore: SecurityKeyBackupStore = mock(MockMode.autofill)
    private val snackbarManager: SnackbarManager = mock(MockMode.autofill)
    private val trackerScope = CoroutineScope(SupervisorJob())
    private val nodeRestartTracker = NodeRestartTracker(trackerScope)

    /**
     * A `viewModelScope` is not a child of `runTest`, so work still in flight when a test ends would resume on
     * `Dispatchers.Main` after [Dispatchers.resetMain] and fail an unrelated later test. Every ViewModel is tracked
     * here so [tearDown] can cancel it.
     */
    private val createdViewModels = mutableListOf<RadioConfigViewModel>()

    private lateinit var viewModel: RadioConfigViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        every { radioConfigRepository.deviceProfileFlow } returns MutableStateFlow(DeviceProfile.Builder().build())
        every { radioConfigRepository.localConfigFlow } returns MutableStateFlow(LocalConfig.Builder().build())
        every { radioConfigRepository.channelSetFlow } returns MutableStateFlow(ChannelSet.Builder().build())
        every { radioConfigRepository.moduleConfigFlow } returns MutableStateFlow(LocalModuleConfig.Builder().build())
        every { radioConfigRepository.deviceUIConfigFlow } returns MutableStateFlow(null)
        every { radioConfigRepository.fileManifestFlow } returns MutableStateFlow(emptyList())
        every { radioConfigRepository.loraRegionPresetMapFlow } returns MutableStateFlow(null)

        every { analyticsPrefs.analyticsAllowed } returns MutableStateFlow(false)
        every { homoglyphEncodingPrefs.homoglyphEncodingEnabled } returns MutableStateFlow(false)

        stubRequestsUnanswered()
        every { serviceRepository.connectionState } returns
            MutableStateFlow(org.meshtastic.sdk.ConnectionState.Connected)

        every { mqttManager.mqttConnectionState } returns
            MutableStateFlow(org.meshtastic.core.model.MqttConnectionState.Inactive)
        every { mqttManager.proxyActive } returns MutableStateFlow(false)

        every { uiPrefs.showQuickChat } returns MutableStateFlow(false)
        every { deviceHardwareRepository.observeDeviceHardware(any(), any()) } returns flowOf(null)

        viewModel = createViewModel()
    }

    @AfterTest
    fun tearDown() {
        createdViewModels.forEach { it.viewModelScope.cancel() }
        createdViewModels.clear()
        trackerScope.cancel()
        Dispatchers.resetMain()
    }

    /** Every admin request times out unless a test answers it, so only the view model's own deadline ends it. */
    private fun stubRequestsUnanswered() {
        everySuspend { adminController.setOwner(any(), any()) } returns AdminResult.Timeout
        everySuspend { adminController.setHamMode(any(), any()) } returns AdminResult.Timeout
        everySuspend { adminController.getOwner(any()) } returns AdminResult.Timeout
        everySuspend { adminController.getDeviceMetadata(any()) } returns AdminResult.Timeout
        everySuspend { adminController.setConfig(any(), any()) } returns AdminResult.Timeout
        everySuspend { adminController.getConfig(any(), any()) } returns AdminResult.Timeout
        everySuspend { adminController.setModuleConfig(any(), any()) } returns AdminResult.Timeout
        everySuspend { adminController.getModuleConfig(any(), any()) } returns AdminResult.Timeout
        everySuspend { adminController.getChannel(any(), any()) } returns AdminResult.Timeout
        everySuspend { adminController.setRemoteChannel(any(), any()) } returns AdminResult.Timeout
        everySuspend { adminController.getRingtone(any()) } returns AdminResult.Timeout
        everySuspend { adminController.getCannedMessages(any()) } returns AdminResult.Timeout
        everySuspend { adminController.getDeviceConnectionStatus(any()) } returns AdminResult.Timeout
        everySuspend { adminActionsUseCase.reboot(any()) } returns AdminResult.Timeout
        everySuspend { adminActionsUseCase.rebootToDfu(any()) } returns AdminResult.Timeout
        everySuspend { adminActionsUseCase.shutdown(any()) } returns AdminResult.Timeout
        everySuspend { adminActionsUseCase.factoryReset(any(), any()) } returns AdminResult.Timeout
        everySuspend { adminActionsUseCase.setTime(any()) } returns AdminResult.Timeout
        everySuspend { adminActionsUseCase.nodedbReset(any(), any(), any()) } returns AdminResult.Timeout
    }

    private val timeoutError = ResponseState.Error(UiText.Resource(Res.string.timeout))

    /** Holds the admin-route metadata preflight until the test completes the returned reply. */
    private fun stubAdminPreflight(): CompletableDeferred<AdminResult<DeviceMetadata>> =
        CompletableDeferred<AdminResult<DeviceMetadata>>().also { reply ->
            everySuspend { adminController.getDeviceMetadata(any()) } calls { reply.await() }
        }

    /** Keeps assertions and ViewModel Main work on the same virtual-time scheduler. */
    private fun runTest(block: suspend TestScope.() -> Unit) =
        kotlinx.coroutines.test.runTest(testDispatcher, testBody = block)

    /**
     * `UiText.resolve()` loads its string on a real thread, outside the test scheduler and past [tearDown]'s cancel; a
     * test whose action ends in a snackbar awaits it, or the load resumes on Main after `resetMain` and fails a later
     * test.
     */
    private fun snackbarShown(): CompletableDeferred<Unit> = CompletableDeferred<Unit>().also { shown ->
        every { snackbarManager.showSnackbar(any(), any(), any(), any(), any()) } calls { shown.complete(Unit) }
    }

    private fun createViewModel(destNum: Int? = null, snackbarManager: SnackbarManager = this.snackbarManager) =
        RadioConfigViewModel(
            initialDestNum = destNum,
            radioConfigRepository = radioConfigRepository,
            serviceRepository = serviceRepository,
            nodeRepository = nodeRepository,
            deviceHardwareRepository = deviceHardwareRepository,
            locationRepository = locationRepository,
            mapConsentPrefs = mapConsentPrefs,
            analyticsPrefs = analyticsPrefs,
            homoglyphEncodingPrefs = homoglyphEncodingPrefs,
            importProfileUseCase = importProfileUseCase,
            exportProfileUseCase = exportProfileUseCase,
            importSecurityConfigUseCase = importSecurityConfigUseCase,
            securityKeyBackupStore = securityKeyBackupStore,
            snackbarManager = snackbarManager,
            nodeRestartTracker = nodeRestartTracker,
            installProfileUseCase = installProfileUseCase,
            adminController = adminController,
            adminActionsUseCase = adminActionsUseCase,
            locationService = locationService,
            fileService = fileService,
            mqttManager = mqttManager,
            lockdownCoordinator = FakeLockdownCoordinator(),
            analytics = mock(MockMode.autofill),
        )
            .also { createdViewModels += it }

    @Test
    fun `a follow-up to a local save runs only after the write has its answer`() = runTest {
        nodeRepository.setMyNodeInfo(myNodeInfo(myNodeNum = 123))
        val events = mutableListOf<String>()
        everySuspend { adminController.setLocalConfigOn(any(), any()) } calls
            {
                events += "write answered"
                AdminResult.Success(Unit)
            }
        viewModel = createViewModel()

        viewModel.setLocalConfig(Config.Builder().build(), SAVE_SESSION) { events += "follow-up" }
        runCurrent()

        assertEquals(listOf("write answered", "follow-up"), events)
        verifySuspend { adminController.setLocalConfigOn(SAVE_SESSION, any()) }
    }

    @Test
    fun `a local save the session refuses runs no follow-up`() = runTest {
        nodeRepository.setMyNodeInfo(myNodeInfo(myNodeNum = 123))
        everySuspend { adminController.setLocalConfigOn(any(), any()) } returns AdminResult.NodeUnreachable
        viewModel = createViewModel()
        var followUps = 0

        viewModel.setLocalConfig(Config.Builder().build(), SAVE_SESSION) { followUps++ }
        runCurrent()

        assertEquals(0, followUps)
    }

    private class CountingTransparentMode(private val events: MutableList<String>) : LoraSaveTransparentMode {
        var session: RadioSessionContext? = SAVE_SESSION
        val readBack = CompletableDeferred<Boolean?>()
        val turnOffs = mutableListOf<RadioSessionContext>()
        val confirms = mutableListOf<RadioSessionContext>()

        override fun connectedSession(): RadioSessionContext? = session

        override fun turnOff(savedOn: RadioSessionContext) {
            turnOffs += savedOn
            events += "turn off"
        }

        override suspend fun confirmTurnedOnByUser(savedOn: RadioSessionContext): Boolean? {
            confirms += savedOn
            events += "read back"
            return readBack.await()
        }
    }

    private fun transparentLora(on: Boolean, hopLimit: Int = 3) = Config.LoRaConfig.Builder()
        .also { wb ->
            wb.hop_limit = hopLimit
            wb.transparent_mode = on
        }
        .build()

    /** A LoRa save to the connected node, whose writes the node answers as soon as they are sent. */
    private fun TestScope.saveLoraOnLocalNode(
        events: MutableList<String>,
        transparentMode: LoraSaveTransparentMode,
        reported: Config.LoRaConfig,
        saved: Config.LoRaConfig,
    ) {
        nodeRepository.setNodes(listOf(Node(num = 123, user = User.Builder().also { wb -> wb.id = "!123" }.build())))
        nodeRepository.setMyNodeInfo(myNodeInfo(myNodeNum = 123))
        everySuspend { adminController.setConfig(any(), any()) } calls
            {
                events += "write sent"
                AdminResult.Success(Unit)
            }
        everySuspend { adminController.setLocalConfigOn(any(), any()) } calls
            {
                events += "write sent on its session"
                AdminResult.Success(Unit)
            }
        viewModel = createViewModel()

        saveLora(viewModel, transparentMode, isLocal = true, reported = reported, saved = saved)
        runCurrent()
    }

    @Test
    fun `a reconnect while a local save is sent leaves its follow-up on the session the save started on`() = runTest {
        val events = mutableListOf<String>()
        val transparentMode = CountingTransparentMode(events)
        val writtenOn = mutableListOf<RadioSessionContext>()
        nodeRepository.setMyNodeInfo(myNodeInfo(myNodeNum = 123))
        everySuspend { adminController.setLocalConfigOn(any(), any()) } calls
            {
                writtenOn += it.args[0] as RadioSessionContext
                transparentMode.session = RadioSessionContext(SAVE_SESSION.generation + 1, SAVE_SESSION.address)
                AdminResult.Success(Unit)
            }
        viewModel = createViewModel()

        saveLora(
            viewModel,
            transparentMode,
            isLocal = true,
            reported = transparentLora(true),
            saved = transparentLora(false),
        )
        runCurrent()

        assertEquals(listOf(SAVE_SESSION), writtenOn)
        assertEquals(listOf(SAVE_SESSION), transparentMode.turnOffs)
    }

    @Test
    fun `a LoRa save turning transparent mode off hands its result to the notice once the write is answered`() =
        runTest {
            val events = mutableListOf<String>()
            val transparentMode = CountingTransparentMode(events)
            saveLoraOnLocalNode(
                events,
                transparentMode,
                reported = transparentLora(true),
                saved = transparentLora(false),
            )

            assertEquals(listOf("write sent on its session", "turn off"), events)
            assertEquals(listOf(SAVE_SESSION), transparentMode.turnOffs)
            assertEquals(emptyList(), transparentMode.confirms)

            advanceTimeBy(31_000)
            runCurrent()

            assertEquals(ResponseState.Empty, viewModel.radioConfigState.value.responseState)
        }

    @Test
    fun `a LoRa save turning transparent mode on succeeds only once the node reads it back on`() = runTest {
        val events = mutableListOf<String>()
        val transparentMode = CountingTransparentMode(events)
        saveLoraOnLocalNode(events, transparentMode, reported = transparentLora(false), saved = transparentLora(true))

        assertEquals(listOf("write sent on its session", "read back"), events)
        assertEquals(emptyList(), transparentMode.turnOffs)
        assertTrue(viewModel.radioConfigState.value.responseState is ResponseState.Loading)

        transparentMode.readBack.complete(true)
        runCurrent()

        assertTrue(viewModel.radioConfigState.value.responseState is ResponseState.Success)
        assertEquals(listOf(SAVE_SESSION), transparentMode.confirms)
    }

    @Test
    fun `a LoRa save turning transparent mode on that the firmware drops says it is not supported`() = runTest {
        val events = mutableListOf<String>()
        val transparentMode = CountingTransparentMode(events)
        saveLoraOnLocalNode(events, transparentMode, reported = transparentLora(false), saved = transparentLora(true))

        transparentMode.readBack.complete(false)
        runCurrent()

        assertEquals(
            ResponseState.Error(UiText.Resource(Res.string.transparent_mode_unsupported)),
            viewModel.radioConfigState.value.responseState,
        )
    }

    @Test
    fun `a LoRa save leaving transparent mode alone neither turns it off nor reads it back`() = runTest {
        val events = mutableListOf<String>()
        val transparentMode = CountingTransparentMode(events)
        saveLoraOnLocalNode(
            events,
            transparentMode,
            reported = transparentLora(true),
            saved = transparentLora(true, hopLimit = 5),
        )

        assertEquals(listOf("write sent"), events)
        assertTrue(viewModel.radioConfigState.value.responseState is ResponseState.Success)
    }

    @Test
    fun `setConfig sends the config to the destination`() = runTest {
        val node = Node(num = 123, user = User.Builder().also { wb -> wb.id = "!123" }.build())
        nodeRepository.setNodes(listOf(node))
        viewModel = createViewModel()

        val config =
            Config.Builder()
                .also { wb ->
                    wb.device =
                        Config.DeviceConfig.Builder().also { wb -> wb.role = Config.DeviceConfig.Role.ROUTER }.build()
                }
                .build()

        viewModel.setConfig(config)

        viewModel.radioConfigState.test {
            val state = awaitItem()
            assertEquals(Config.DeviceConfig.Role.ROUTER, state.radioConfig.device?.role)
            cancelAndIgnoreRemainingEvents()
        }

        verifySuspend { adminController.setConfig(123, config) }
    }

    @Test
    fun `toggleAnalyticsAllowed delegates to the prefs toggle`() {
        every { analyticsPrefs.toggleAnalyticsAllowed() } returns Unit

        viewModel.toggleAnalyticsAllowed()

        verify { analyticsPrefs.toggleAnalyticsAllowed() }
    }

    @Test
    fun `toggleHomoglyphCharactersEncodingEnabled delegates to the prefs toggle`() {
        every { homoglyphEncodingPrefs.toggleHomoglyphEncodingEnabled() } returns Unit

        viewModel.toggleHomoglyphCharactersEncodingEnabled()

        verify { homoglyphEncodingPrefs.toggleHomoglyphEncodingEnabled() }
    }

    @Test
    fun adminPreflightStoresTheDeviceMetadata() = runTest {
        val node = Node(num = 123, user = User.Builder().also { wb -> wb.id = "!123" }.build())
        nodeRepository.setNodes(listOf(node))

        val metadata = DeviceMetadata.Builder().also { wb -> wb.firmware_version = "3.0.0" }.build()
        everySuspend { adminController.getDeviceMetadata(any()) } returns AdminResult.Success(metadata)

        viewModel = createViewModel()

        viewModel.setResponseStateLoading(AdminRoute.SET_TIME)
        runCurrent()

        viewModel.radioConfigState.test {
            val state = awaitItem()
            assertEquals("3.0.0", state.metadata?.firmware_version)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `probeMqttConnection updates status for success`() = runTest {
        everySuspend { mqttManager.probe("mqtt.example.com", true, "user", "pass") }
            .calls {
                delay(1)
                MqttProbeStatus.Success(serverInfo = "client=test")
            }

        viewModel.probeMqttConnection("mqtt.example.com", true, "user", "pass")

        assertEquals(MqttProbeStatus.Probing, viewModel.mqttProbeStatus.value)

        advanceTimeBy(1)
        runCurrent()

        assertEquals(MqttProbeStatus.Success(serverInfo = "client=test"), viewModel.mqttProbeStatus.value)
        verifySuspend { mqttManager.probe("mqtt.example.com", true, "user", "pass") }
    }

    @Test
    fun `probeMqttConnection updates status for timeout`() = runTest {
        everySuspend { mqttManager.probe("mqtt.example.com", false, null, null) } returns MqttProbeStatus.Timeout(5_000)

        viewModel.probeMqttConnection("mqtt.example.com", false, null, null)
        runCurrent()

        assertEquals(MqttProbeStatus.Timeout(5_000), viewModel.mqttProbeStatus.value)
        verifySuspend { mqttManager.probe("mqtt.example.com", false, null, null) }
    }

    @Test
    fun `probeMqttConnection converts thrown exception to other status`() = runTest {
        everySuspend { mqttManager.probe("mqtt.example.com", true, null, null) }
            .calls { throw IllegalStateException("boom") }

        viewModel.probeMqttConnection("mqtt.example.com", true, null, null)
        runCurrent()

        assertEquals(MqttProbeStatus.Other(message = "boom"), viewModel.mqttProbeStatus.value)
        verifySuspend { mqttManager.probe("mqtt.example.com", true, null, null) }
    }

    @Test
    fun `setMqttProxyActive false stops the proxy without touching device config`() = runTest {
        every { mqttManager.stop() } returns Unit

        viewModel.setMqttProxyActive(false)

        verify { mqttManager.stop() }
        // Phone-local cut must not issue any device config read/write.
        verifySuspend(exactly(0)) { adminController.setModuleConfig(any(), any()) }
    }

    @Test
    fun `setMqttProxyActive true restarts proxy using current device module config`() = runTest {
        every { radioConfigRepository.moduleConfigFlow } returns
            MutableStateFlow(
                LocalModuleConfig.Builder()
                    .also { wb ->
                        wb.mqtt =
                            org.meshtastic.proto.ModuleConfig.MQTTConfig.Builder()
                                .also { wb ->
                                    wb.enabled = true
                                    wb.proxy_to_client_enabled = true
                                }
                                .build()
                    }
                    .build(),
            )
        every { mqttManager.startProxy(any(), any()) } returns Unit
        viewModel = createViewModel()
        runCurrent()

        viewModel.setMqttProxyActive(true)

        verify { mqttManager.startProxy(true, true) }
        verifySuspend(exactly(0)) { adminController.setModuleConfig(any(), any()) }
    }

    @Test
    fun `setMqttProxyActive true is a no-op start when device MQTT is disabled`() = runTest {
        every { radioConfigRepository.moduleConfigFlow } returns MutableStateFlow(LocalModuleConfig.Builder().build())
        every { mqttManager.startProxy(any(), any()) } returns Unit
        viewModel = createViewModel()
        runCurrent()

        viewModel.setMqttProxyActive(true)

        // The manager is still asked to start, but with both flags false it does nothing (its own guard).
        verify { mqttManager.startProxy(false, false) }
    }

    @Test
    fun `clearMqttProbeStatus resets probe state`() = runTest {
        everySuspend { mqttManager.probe("mqtt.example.com", false, null, null) }
            .calls {
                delay(1)
                MqttProbeStatus.Success(serverInfo = "client=test")
            }

        viewModel.probeMqttConnection("mqtt.example.com", false, null, null)
        assertEquals(MqttProbeStatus.Probing, viewModel.mqttProbeStatus.value)

        viewModel.clearMqttProbeStatus()
        assertEquals(null, viewModel.mqttProbeStatus.value)

        advanceTimeBy(1)
        runCurrent()

        assertEquals(null, viewModel.mqttProbeStatus.value)
    }

    @Test
    fun `updateChannels sends each changed channel`() = runTest {
        val node = Node(num = 123, user = User.Builder().also { wb -> wb.id = "!123" }.build())
        nodeRepository.setNodes(listOf(node))
        viewModel = createViewModel()

        val old = listOf(ChannelSettings.Builder().also { wb -> wb.name = "Old" }.build())
        val new = listOf(ChannelSettings.Builder().also { wb -> wb.name = "New" }.build())

        viewModel.updateChannels(new, old)

        verifySuspend { adminController.setRemoteChannel(123, any()) }
        assertEquals(new, viewModel.radioConfigState.value.channelList)
    }

    @Test
    fun `updateChannels writes changed channels sequentially in index order`() = runTest {
        val node = Node(num = 123, user = User.Builder().also { wb -> wb.id = "!123" }.build())
        nodeRepository.setNodes(listOf(node))
        viewModel = createViewModel()

        val channelA = ChannelSettings.Builder().also { wb -> wb.name = "A" }.build()
        val channelB = ChannelSettings.Builder().also { wb -> wb.name = "B" }.build()
        val channelC = ChannelSettings.Builder().also { wb -> wb.name = "C" }.build()
        val old = listOf(channelA, channelB, channelC)
        val new = listOf(channelA, channelC, channelB)
        val writtenIndexes = mutableListOf<Int>()
        var activeWrites = 0
        var maxConcurrentWrites = 0

        everySuspend { adminController.setRemoteChannel(any(), any()) } calls
            {
                val channel = it.args[1] as Channel
                activeWrites++
                maxConcurrentWrites = maxOf(maxConcurrentWrites, activeWrites)
                writtenIndexes.add(channel.index)
                delay(1)
                activeWrites--
                AdminResult.Timeout
            }

        viewModel.updateChannels(new, old)
        advanceUntilIdle()

        assertEquals(listOf(1, 2), writtenIndexes)
        assertEquals(1, maxConcurrentWrites)
        assertEquals(new, viewModel.radioConfigState.value.channelList)
        verifySuspend(exactly(2)) { adminController.setRemoteChannel(123, any()) }
    }

    @Test
    fun `updateChannels waits for all ordered writes before success`() = runTest {
        val node = Node(num = 123, user = User.Builder().also { wb -> wb.id = "!123" }.build())
        nodeRepository.setNodes(listOf(node))
        viewModel = createViewModel()

        val channelA = ChannelSettings.Builder().also { wb -> wb.name = "A" }.build()
        val channelB = ChannelSettings.Builder().also { wb -> wb.name = "B" }.build()
        val channelC = ChannelSettings.Builder().also { wb -> wb.name = "C" }.build()
        val old = listOf(channelA, channelB, channelC)
        val new = listOf(channelA, channelC, channelB)
        val writeReplies = mutableListOf<CompletableDeferred<AdminResult<Unit>>>()

        everySuspend { adminController.setRemoteChannel(any(), any()) } calls
            {
                CompletableDeferred<AdminResult<Unit>>().also { reply -> writeReplies += reply }.await()
            }

        viewModel.updateChannels(new, old)
        runCurrent()

        writeReplies[0].complete(AdminResult.Success(Unit))
        runCurrent()

        val midBatchState = viewModel.radioConfigState.value.responseState
        assertTrue(midBatchState is ResponseState.Loading)
        assertEquals(2, midBatchState.total)
        assertEquals(1, midBatchState.completed)

        advanceTimeBy(MANUAL_CHANNEL_WRITE_DELAY.inWholeMilliseconds)
        runCurrent()

        writeReplies[1].complete(AdminResult.Success(Unit))
        runCurrent()

        assertTrue(viewModel.radioConfigState.value.responseState is ResponseState.Success)
    }

    @Test
    fun `routing error stops remaining manual channel writes`() = runTest {
        val node = Node(num = 123, user = User.Builder().also { wb -> wb.id = "!123" }.build())
        val old =
            listOf(
                ChannelSettings.Builder().also { wb -> wb.name = "A" }.build(),
                ChannelSettings.Builder().also { wb -> wb.name = "B" }.build(),
                ChannelSettings.Builder().also { wb -> wb.name = "C" }.build(),
            )
        val new = listOf(old[0], old[2], old[1])
        val writtenIndexes = mutableListOf<Int>()
        val firstWriteReply = CompletableDeferred<AdminResult<Unit>>()

        nodeRepository.setNodes(listOf(node))
        viewModel = createViewModel()

        everySuspend { adminController.setRemoteChannel(any(), any()) } calls
            {
                val channel = it.args[1] as Channel
                writtenIndexes += channel.index
                firstWriteReply.await()
            }

        viewModel.updateChannels(new, old)
        runCurrent()
        assertEquals(listOf(1), writtenIndexes)

        firstWriteReply.complete(AdminResult.Failed(Routing.Error.MAX_RETRANSMIT))
        runCurrent()
        advanceTimeBy(MANUAL_CHANNEL_WRITE_DELAY.inWholeMilliseconds + 1)
        runCurrent()

        assertEquals(listOf(1), writtenIndexes, "a terminal response must invalidate the rest of the batch")
        assertTrue(viewModel.radioConfigState.value.responseState is ResponseState.Error)
    }

    @Test
    fun `routing error cancels manual channel batches queued behind the active batch`() = runTest {
        val node = Node(num = 123, user = User.Builder().also { wb -> wb.id = "!123" }.build())
        val old =
            listOf(
                ChannelSettings.Builder().also { wb -> wb.name = "A" }.build(),
                ChannelSettings.Builder().also { wb -> wb.name = "B" }.build(),
                ChannelSettings.Builder().also { wb -> wb.name = "C" }.build(),
            )
        val firstUpdate = listOf(old[0], old[2], old[1])
        val secondUpdate = listOf(old[0], ChannelSettings.Builder().also { wb -> wb.name = "D" }.build(), old[2])
        val writtenIndexes = mutableListOf<Int>()
        val firstWriteReply = CompletableDeferred<AdminResult<Unit>>()

        nodeRepository.setNodes(listOf(node))
        viewModel = createViewModel()

        everySuspend { adminController.setRemoteChannel(any(), any()) } calls
            {
                writtenIndexes += (it.args[1] as Channel).index
                firstWriteReply.await()
            }

        viewModel.updateChannels(firstUpdate, old)
        runCurrent()
        viewModel.updateChannels(secondUpdate, old)
        runCurrent()

        firstWriteReply.complete(AdminResult.Failed(Routing.Error.MAX_RETRANSMIT))
        runCurrent()
        advanceUntilIdle()

        assertEquals(listOf(1), writtenIndexes, "terminal invalidation must cancel active and queued channel batches")
        assertTrue(viewModel.radioConfigState.value.responseState is ResponseState.Error)
    }

    @Test
    fun updateChannelsReconcilesAppliedChannelWritesWhenAWriteIsRefused() = runTest {
        val node = Node(num = 123, user = User.Builder().also { wb -> wb.id = "!123" }.build())
        val old = fourChannelFixture()
        val (channelA, _, _, channelD) = old
        val new = listOf(channelA, channelD)
        val partiallyApplied = listOf(channelA, channelD, old[2], old[3])
        val writtenIndexes = mutableListOf<Int>()

        every { radioConfigRepository.channelSetFlow } returns
            MutableStateFlow(ChannelSet.Builder().also { wb -> wb.settings = old }.build())
        nodeRepository.setNodes(listOf(node))
        nodeRepository.setMyNodeInfo(myNodeInfo(myNodeNum = 123))
        viewModel = createViewModel()
        runCurrent()

        everySuspend { adminController.setRemoteChannel(any(), any()) } calls
            {
                val channel = it.args[1] as Channel
                writtenIndexes.add(channel.index)
                AdminResult.Failed(Routing.Error.NO_CHANNEL)
            }

        viewModel.updateChannels(new, old)
        advanceUntilIdle()

        assertEquals(listOf(1), writtenIndexes)
        assertEquals(partiallyApplied, viewModel.radioConfigState.value.channelList)
        assertTrue(viewModel.radioConfigState.value.responseState is ResponseState.Error)
        // replaceAllSettings re-keys the conversations itself, so the partial set must still reach it.
        verifySuspend { radioConfigRepository.replaceAllSettings(partiallyApplied) }
    }

    @Test
    fun `updateChannels serializes overlapping channel saves`() = runTest {
        val node = Node(num = 123, user = User.Builder().also { wb -> wb.id = "!123" }.build())
        val old = fourChannelFixture()
        val (channelA, _, channelC, channelD) = old
        val firstNew = listOf(channelA, channelD)
        val secondNew = listOf(channelA, channelC)
        val writtenChannels = mutableListOf<String>()
        var firstWrite = true

        every { radioConfigRepository.channelSetFlow } returns
            MutableStateFlow(ChannelSet.Builder().also { wb -> wb.settings = old }.build())
        nodeRepository.setNodes(listOf(node))
        viewModel = createViewModel()
        runCurrent()

        everySuspend { adminController.setRemoteChannel(any(), any()) } calls
            {
                val channel = it.args[1] as Channel
                writtenChannels.add("${channel.index}:${channel.role}:${channel.settings?.name.orEmpty()}")
                if (firstWrite) {
                    firstWrite = false
                    delay(10_000)
                }
                AdminResult.Timeout
            }

        viewModel.updateChannels(firstNew, old)
        runCurrent()
        viewModel.updateChannels(secondNew, old)
        advanceUntilIdle()

        assertEquals(listOf("1:SECONDARY:D", "2:DISABLED:", "3:DISABLED:", "1:SECONDARY:C"), writtenChannels)
        assertEquals(secondNew, viewModel.radioConfigState.value.channelList)
    }

    @Test
    fun `updateChannels does not start manual batch while another radio request is pending`() = runTest {
        val node = Node(num = 123, user = User.Builder().also { wb -> wb.id = "!123" }.build())
        nodeRepository.setNodes(listOf(node))
        viewModel = createViewModel()

        val old = listOf(ChannelSettings.Builder().also { wb -> wb.name = "Old" }.build())
        val new = listOf(ChannelSettings.Builder().also { wb -> wb.name = "New" }.build())

        val ownerReply = CompletableDeferred<AdminResult<User>>()
        everySuspend { adminController.getOwner(any()) } calls { ownerReply.await() }

        viewModel.setResponseStateLoading(ConfigRoute.USER)
        runCurrent()

        viewModel.updateChannels(new, old)
        runCurrent()

        assertEquals(ConfigRoute.USER.name, viewModel.radioConfigState.value.route)
        assertTrue(viewModel.radioConfigState.value.responseState is ResponseState.Loading)
        verifySuspend(exactly(0)) { adminController.setRemoteChannel(any(), any()) }

        advanceUntilIdle()
    }

    @Test
    fun `aborted manual batch preserves unrelated pending request`() = runTest {
        val node = Node(num = 123, user = User.Builder().also { wb -> wb.id = "!123" }.build())
        val old = fourChannelFixture()
        val (channelA, _, _, channelD) = old
        val new = listOf(channelA, channelD)
        val owner =
            User.Builder()
                .also { wb ->
                    wb.id = "!123"
                    wb.long_name = "Updated"
                }
                .build()
        val ownerReply = CompletableDeferred<AdminResult<User>>()

        nodeRepository.setNodes(listOf(node))
        nodeRepository.setMyNodeInfo(myNodeInfo(myNodeNum = 123))
        viewModel = createViewModel()

        everySuspend { adminController.getOwner(any()) } calls { ownerReply.await() }
        // The batch aborts after its writes were issued, with an owner read registered in the meantime.
        everySuspend { radioConfigRepository.replaceAllSettings(any()) } calls
            {
                viewModel.setResponseStateLoading(ConfigRoute.USER)
                throw IllegalStateException("boom")
            }

        viewModel.updateChannels(new, old)
        runCurrent()
        advanceTimeBy(2 * MANUAL_CHANNEL_WRITE_DELAY.inWholeMilliseconds)
        runCurrent()
        verifySuspend { adminController.getOwner(123) }

        ownerReply.complete(AdminResult.Success(owner))
        runCurrent()

        assertEquals(owner, viewModel.radioConfigState.value.userConfig)
    }

    @Test
    fun `applyManualChannelUpdatePlan paces writes except after final channel`() = runTest {
        val channelA =
            Channel.Builder()
                .also { wb ->
                    wb.index = 1
                    wb.role = Channel.Role.SECONDARY
                    wb.settings = ChannelSettings.Builder().also { wb -> wb.name = "A" }.build()
                }
                .build()
        val channelB =
            Channel.Builder()
                .also { wb ->
                    wb.index = 2
                    wb.role = Channel.Role.DISABLED
                    wb.settings = ChannelSettings.Builder().build()
                }
                .build()
        val channelC =
            Channel.Builder()
                .also { wb ->
                    wb.index = 3
                    wb.role = Channel.Role.DISABLED
                    wb.settings = ChannelSettings.Builder().build()
                }
                .build()
        val writtenIndexes = mutableListOf<Int>()
        val registeredRequestIds = mutableListOf<Int>()
        val delays = mutableListOf<Duration>()

        val result =
            applyManualChannelUpdatePlan(
                updatePlan = listOf(channelA, channelB, channelC),
                currentSettings = listOf(ChannelSettings.Builder().also { wb -> wb.name = "old" }.build()),
                finalSettings = listOf(ChannelSettings.Builder().also { wb -> wb.name = "new" }.build()),
                writeChannel = { channel, onRequestId ->
                    writtenIndexes.add(channel.index)
                    val id = channel.index + 100
                    onRequestId(id)
                    id
                },
                registerRequestId = { registeredRequestIds.add(it) },
                delayFn = { delays.add(it) },
            )

        assertEquals(listOf(1, 2, 3), writtenIndexes)
        assertEquals(listOf(101, 102, 103), result.packetIds)
        assertEquals(listOf(ChannelSettings.Builder().also { wb -> wb.name = "new" }.build()), result.finalSettings)
        assertEquals(listOf(101, 102, 103), registeredRequestIds)
        assertEquals(listOf(MANUAL_CHANNEL_WRITE_DELAY, MANUAL_CHANNEL_WRITE_DELAY), delays)
    }

    @Test
    fun `applyManualChannelUpdatePlan invokes onInterrupted when writeChannel fails mid-plan`() = runTest {
        val channelA =
            Channel.Builder()
                .also { wb ->
                    wb.index = 1
                    wb.role = Channel.Role.SECONDARY
                    wb.settings = ChannelSettings.Builder().also { wb -> wb.name = "A" }.build()
                }
                .build()
        val channelB =
            Channel.Builder()
                .also { wb ->
                    wb.index = 2
                    wb.role = Channel.Role.SECONDARY
                    wb.settings = ChannelSettings.Builder().also { wb -> wb.name = "B" }.build()
                }
                .build()
        val channelC =
            Channel.Builder()
                .also { wb ->
                    wb.index = 3
                    wb.role = Channel.Role.SECONDARY
                    wb.settings = ChannelSettings.Builder().also { wb -> wb.name = "C" }.build()
                }
                .build()
        val writtenIndexes = mutableListOf<Int>()

        var interrupted: InterruptedManualChannelUpdate? = null

        val error =
            assertFailsWith<IllegalStateException> {
                applyManualChannelUpdatePlan(
                    updatePlan = listOf(channelA, channelB, channelC),
                    currentSettings = listOf(ChannelSettings.Builder().also { wb -> wb.name = "old" }.build()),
                    finalSettings = listOf(ChannelSettings.Builder().also { wb -> wb.name = "new" }.build()),
                    writeChannel = { channel, onRequestId ->
                        writtenIndexes.add(channel.index)
                        if (channel.index == 2) throw IllegalStateException("boom")
                        val id = channel.index + 100
                        onRequestId(id)
                        id
                    },
                    registerRequestId = {},
                    onInterrupted = { interrupted = it },
                    delayFn = {},
                )
            }

        assertEquals("boom", error.message)
        // Channel A (index 1) completed before channel B (index 2) threw.
        assertEquals(listOf(1, 2), writtenIndexes)
        assertNotNull(interrupted)
        assertEquals(1, interrupted.appliedWriteCount)
        assertEquals("A", interrupted.appliedSettings[1].name)
    }

    @Test
    fun rebootRouteCallsUseCaseAfterMetadataPreflight() = runTest {
        val node = Node(num = 123, user = User.Builder().also { wb -> wb.id = "!123" }.build())
        nodeRepository.setNodes(listOf(node))
        val preflight = stubAdminPreflight()

        viewModel = createViewModel()

        viewModel.setResponseStateLoading(AdminRoute.REBOOT)
        runCurrent()
        // The destructive action fires only once the metadata preflight answers.
        verifySuspend(exactly(0)) { adminActionsUseCase.reboot(any()) }

        preflight.complete(AdminResult.Success(DeviceMetadata.Builder().build()))
        runCurrent()

        verifySuspend { adminActionsUseCase.reboot(123) }
    }

    @Test
    fun rebootDfuRouteCallsUseCaseAfterMetadataPreflight() = runTest {
        val node = Node(num = 123, user = User.Builder().also { wb -> wb.id = "!123" }.build())
        nodeRepository.setNodes(listOf(node))
        val preflight = stubAdminPreflight()

        viewModel = createViewModel()

        viewModel.setResponseStateLoading(AdminRoute.REBOOT_DFU)
        runCurrent()
        verifySuspend(exactly(0)) { adminActionsUseCase.rebootToDfu(any()) }

        preflight.complete(AdminResult.Success(DeviceMetadata.Builder().build()))
        runCurrent()

        verifySuspend { adminActionsUseCase.rebootToDfu(123) }
    }

    @Test
    fun `an unacknowledged DFU request settles as success instead of a timeout error`() = runTest {
        val node = Node(num = 123, user = User.Builder().also { wb -> wb.id = "!123" }.build())
        nodeRepository.setNodes(listOf(node))
        everySuspend { adminController.getDeviceMetadata(any()) } returns
            AdminResult.Success(DeviceMetadata.Builder().build())

        viewModel = createViewModel()

        viewModel.setResponseStateLoading(AdminRoute.REBOOT_DFU)
        runCurrent()
        verifySuspend { adminActionsUseCase.rebootToDfu(123) }
        assertTrue(viewModel.radioConfigState.value.responseState is ResponseState.Loading)

        advanceTimeBy(31_000)
        runCurrent()

        assertTrue(viewModel.radioConfigState.value.responseState is ResponseState.Success)
    }

    @Test
    fun `canRebootToDfu is true only for nRF52 hardware`() = runTest {
        val node =
            Node(
                num = 123,
                user =
                User.Builder()
                    .also { wb ->
                        wb.id = "!123"
                        wb.hw_model = HardwareModel.RAK4631
                    }
                    .build(),
            )
        nodeRepository.setNodes(listOf(node))
        every { deviceHardwareRepository.observeDeviceHardware(HardwareModel.RAK4631.value, any()) } returns
            flowOf(DeviceHardware(architecture = "nrf52840"))

        viewModel = createViewModel(destNum = 123)
        advanceUntilIdle()
        assertTrue(viewModel.radioConfigState.value.canRebootToDfu)

        every { deviceHardwareRepository.observeDeviceHardware(HardwareModel.RAK4631.value, any()) } returns
            flowOf(DeviceHardware(architecture = "esp32-s3"))

        viewModel = createViewModel(destNum = 123)
        advanceUntilIdle()
        assertFalse(viewModel.radioConfigState.value.canRebootToDfu)
    }

    @Test
    fun factoryResetRouteCallsUseCaseAfterMetadataPreflight() = runTest {
        val node = Node(num = 123, user = User.Builder().also { wb -> wb.id = "!123" }.build())
        nodeRepository.setNodes(listOf(node))
        val preflight = stubAdminPreflight()

        viewModel = createViewModel()

        viewModel.setResponseStateLoading(AdminRoute.FACTORY_RESET)
        runCurrent()
        verifySuspend(exactly(0)) { adminActionsUseCase.factoryReset(any(), any()) }

        preflight.complete(AdminResult.Success(DeviceMetadata.Builder().build()))
        runCurrent()

        verifySuspend { adminActionsUseCase.factoryReset(123, any()) }
    }

    @Test
    fun `NODEDB_RESET marks an expected local restart`() = runTest {
        // Firmware reboots after a nodedb reset, so a local reset must open the restart window (like FACTORY_RESET)
        // or the ensuing transport drop surfaces as a surprise disconnect instead of "restarting".
        val node = Node(num = 123, user = User.Builder().also { wb -> wb.id = "!123" }.build())
        nodeRepository.setNodes(listOf(node))
        nodeRepository.setMyNodeInfo(myNodeInfo(myNodeNum = 123))
        everySuspend { adminController.getDeviceMetadata(any()) } returns
            AdminResult.Success(DeviceMetadata.Builder().build())

        viewModel = createViewModel()
        runCurrent()

        viewModel.setResponseStateLoading(AdminRoute.NODEDB_RESET)
        runCurrent()

        verifySuspend { adminActionsUseCase.nodedbReset(123, any(), any()) }
        assertTrue(nodeRestartTracker.restartExpected.value)
    }

    @Test
    fun `manual channel batch is not completed as success by a stale restart window`() = runTest {
        // A prior local MAY_RESTART save opened the 90s restart window, but the node never actually rebooted (stayed
        // Connected, so onConnected never re-fired to close it early). A manual channel batch shares the save shape
        // (empty route + Loading); a transient drop inside the stale window must not flip the incomplete batch to
        // success and silently misreport a partial channel write.
        val node = Node(num = 123, user = User.Builder().also { wb -> wb.id = "!123" }.build())
        nodeRepository.setNodes(listOf(node))
        nodeRepository.setMyNodeInfo(myNodeInfo(myNodeNum = 123))

        val connectionFlow = MutableStateFlow<ConnectionState>(ConnectionState.Connected)
        every { serviceRepository.connectionState } returns connectionFlow
        viewModel = createViewModel()
        runCurrent()

        nodeRestartTracker.expectRestart()

        // Enqueue a manual channel batch; its write is never answered.
        viewModel.updateChannels(
            listOf(ChannelSettings.Builder().also { wb -> wb.name = "New" }.build()),
            listOf(ChannelSettings.Builder().also { wb -> wb.name = "Old" }.build()),
        )
        runCurrent()

        assertTrue(nodeRestartTracker.restartExpected.value)
        assertTrue(viewModel.radioConfigState.value.responseState is ResponseState.Loading)

        // Transient transport drop inside the stale restart window.
        connectionFlow.value = ConnectionState.Disconnected
        runCurrent()

        assertFalse(viewModel.radioConfigState.value.responseState is ResponseState.Success)
        assertTrue(viewModel.radioConfigState.value.responseState is ResponseState.Loading)
    }

    @Test
    fun `setPreserveFavorites updates state`() = runTest {
        viewModel.radioConfigState.test {
            assertEquals(false, awaitItem().nodeDbResetPreserveFavorites)
            viewModel.setPreserveFavorites(true)
            assertEquals(true, awaitItem().nodeDbResetPreserveFavorites)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setOwner sends the owner to the destination`() = runTest {
        val node = Node(num = 123, user = User.Builder().also { wb -> wb.id = "!123" }.build())
        nodeRepository.setNodes(listOf(node))
        viewModel = createViewModel()

        val user = User.Builder().also { wb -> wb.long_name = "Test User" }.build()

        viewModel.setOwner(user)

        verifySuspend { adminController.setOwner(123, user) }
    }

    @Test
    fun `saveUserConfig sends setHamMode for licensed local node`() = runTest {
        val node = Node(num = 123, user = User.Builder().also { wb -> wb.id = "!123" }.build())
        nodeRepository.setNodes(listOf(node))
        nodeRepository.setMyNodeInfo(myNodeInfo(myNodeNum = 123))
        viewModel = createViewModel()

        val user =
            User.Builder()
                .also { wb ->
                    wb.long_name = "KK7ABC"
                    wb.short_name = "KK7A"
                    wb.is_licensed = true
                }
                .build()

        viewModel.saveUserConfig(user)

        verifySuspend {
            adminController.setHamMode(
                123,
                HamParameters.Builder()
                    .also { wb ->
                        wb.call_sign = "KK7ABC"
                        wb.short_name = "KK7A"
                    }
                    .build(),
            )
        }
        verifySuspend(exactly(0)) { adminController.setOwner(any(), any()) }
    }

    @Test
    fun `saveUserConfig splits a composed ham name into call sign and long name`() = runTest {
        val node = Node(num = 123, user = User.Builder().also { wb -> wb.id = "!123" }.build())
        nodeRepository.setNodes(listOf(node))
        nodeRepository.setMyNodeInfo(myNodeInfo(myNodeNum = 123))
        viewModel = createViewModel()

        // The User form carries the composed name firmware builds; set_ham_mode wants the halves back.
        val user =
            User.Builder()
                .also { wb ->
                    wb.long_name = "KK7ABC//Attic Heltec"
                    wb.short_name = "KK7A"
                    wb.is_licensed = true
                }
                .build()

        viewModel.saveUserConfig(user)

        verifySuspend {
            adminController.setHamMode(
                123,
                HamParameters.Builder()
                    .also { wb ->
                        wb.call_sign = "KK7ABC"
                        wb.short_name = "KK7A"
                        wb.long_name = "Attic Heltec"
                    }
                    .build(),
            )
        }
    }

    @Test
    fun `saveUserConfig sends setOwner for unlicensed user`() = runTest {
        val node = Node(num = 123, user = User.Builder().also { wb -> wb.id = "!123" }.build())
        nodeRepository.setNodes(listOf(node))
        nodeRepository.setMyNodeInfo(myNodeInfo(myNodeNum = 123))
        viewModel = createViewModel()

        val user =
            User.Builder()
                .also { wb ->
                    wb.long_name = "Test User"
                    wb.short_name = "TU"
                }
                .build()

        viewModel.saveUserConfig(user)

        verifySuspend { adminController.setOwner(123, user) }
        verifySuspend(exactly(0)) { adminController.setHamMode(any(), any()) }
    }

    @Test
    fun `saveUserConfig never sends setHamMode to a remote node`() = runTest {
        val localNode = Node(num = 100, user = User.Builder().also { wb -> wb.id = "!100" }.build())
        val remoteNode = Node(num = 456, user = User.Builder().also { wb -> wb.id = "!456" }.build())
        nodeRepository.setNodes(listOf(localNode, remoteNode))
        nodeRepository.setMyNodeInfo(myNodeInfo(myNodeNum = 100))
        viewModel = createViewModel(destNum = 456)

        val user =
            User.Builder()
                .also { wb ->
                    wb.long_name = "KK7ABC"
                    wb.short_name = "KK7A"
                    wb.is_licensed = true
                }
                .build()

        viewModel.saveUserConfig(user)

        verifySuspend { adminController.setOwner(456, user) }
        verifySuspend(exactly(0)) { adminController.setHamMode(any(), any()) }
    }

    @Test
    fun `saveUserConfig routes subsequent licensed saves to setOwner`() = runTest {
        val node = Node(num = 123, user = User.Builder().also { wb -> wb.id = "!123" }.build())
        nodeRepository.setNodes(listOf(node))
        nodeRepository.setMyNodeInfo(myNodeInfo(myNodeNum = 123))
        viewModel = createViewModel()

        val user =
            User.Builder()
                .also { wb ->
                    wb.long_name = "KK7ABC"
                    wb.short_name = "KK7A"
                    wb.is_licensed = true
                }
                .build()

        // First save transitions OFF→ON and onboards via set_ham_mode.
        viewModel.saveUserConfig(user)
        // A later save while already licensed must use set_owner so other owner fields propagate.
        val edited = user.newBuilder().also { wb -> wb.short_name = "KK7B" }.build()
        viewModel.saveUserConfig(edited)

        verifySuspend(exactly(1)) { adminController.setHamMode(any(), any()) }
        verifySuspend { adminController.setOwner(123, edited) }
    }

    @Test
    fun `saveUserConfig routes licensed save to setOwner when myNodeInfo is absent`() = runTest {
        val node = Node(num = 123, user = User.Builder().also { wb -> wb.id = "!123" }.build())
        nodeRepository.setNodes(listOf(node))
        viewModel = createViewModel()

        val user =
            User.Builder()
                .also { wb ->
                    wb.long_name = "KK7ABC"
                    wb.short_name = "KK7A"
                    wb.is_licensed = true
                }
                .build()

        viewModel.saveUserConfig(user)

        verifySuspend { adminController.setOwner(123, user) }
        verifySuspend(exactly(0)) { adminController.setHamMode(any(), any()) }
    }

    @Test
    fun `setRingtone sends the ringtone to the destination`() = runTest {
        val node = Node(num = 123, user = User.Builder().also { wb -> wb.id = "!123" }.build())
        nodeRepository.setNodes(listOf(node))
        viewModel = createViewModel()

        everySuspend { adminController.setRingtone(any(), any()) } returns Unit

        viewModel.setRingtone("ringtone.mp3")

        assertEquals("ringtone.mp3", viewModel.radioConfigState.value.ringtone)
        verifySuspend { adminController.setRingtone(123, "ringtone.mp3") }
    }

    @Test
    fun `playRingtone asks the destination node to play`() = runTest {
        val node = Node(num = 123, user = User.Builder().also { wb -> wb.id = "!123" }.build())
        nodeRepository.setNodes(listOf(node))
        viewModel = createViewModel()

        everySuspend { adminController.playRingtone(any()) } returns Unit

        viewModel.playRingtone()

        verifySuspend { adminController.playRingtone(123) }
    }

    @Test
    fun `setCannedMessages sends the messages to the destination`() = runTest {
        val node = Node(num = 123, user = User.Builder().also { wb -> wb.id = "!123" }.build())
        nodeRepository.setNodes(listOf(node))
        viewModel = createViewModel()

        everySuspend { adminController.setCannedMessages(any(), any()) } returns Unit

        viewModel.setCannedMessages("Hello|World")

        assertEquals("Hello|World", viewModel.radioConfigState.value.cannedMessageMessages)
        verifySuspend { adminController.setCannedMessages(123, "Hello|World") }
    }

    @Test
    fun `setRingtone retires the pending route read without stranding loading`() = runTest {
        val localNode = Node(num = 100, user = User.Builder().also { wb -> wb.id = "!100" }.build())
        val remoteNode = Node(num = 456, user = User.Builder().also { wb -> wb.id = "!456" }.build())
        everySuspend { adminController.setRingtone(any(), any()) } returns Unit
        nodeRepository.setNodes(listOf(localNode, remoteNode))
        nodeRepository.setMyNodeInfo(myNodeInfo(myNodeNum = 100))
        viewModel = createViewModel(destNum = 456)

        viewModel.setResponseStateLoading(ModuleRoute.EXT_NOTIFICATION)
        runCurrent()
        viewModel.setRingtone("ringtone.mp3")
        runCurrent()

        assertEquals(ResponseState.Empty, viewModel.radioConfigState.value.responseState)
    }

    @Test
    fun `setCannedMessages retires the pending route read without stranding loading`() = runTest {
        val localNode = Node(num = 100, user = User.Builder().also { wb -> wb.id = "!100" }.build())
        val remoteNode = Node(num = 456, user = User.Builder().also { wb -> wb.id = "!456" }.build())
        everySuspend { adminController.setCannedMessages(any(), any()) } returns Unit
        nodeRepository.setNodes(listOf(localNode, remoteNode))
        nodeRepository.setMyNodeInfo(myNodeInfo(myNodeNum = 100))
        viewModel = createViewModel(destNum = 456)

        viewModel.setResponseStateLoading(ModuleRoute.CANNED_MESSAGE)
        runCurrent()
        viewModel.setCannedMessages("Hello|World")
        runCurrent()

        assertEquals(ResponseState.Empty, viewModel.radioConfigState.value.responseState)
    }

    @Test
    fun `destNum from SavedStateHandle resolves destNode`() = runTest {
        val node = Node(num = 456, user = User.Builder().also { wb -> wb.id = "!456" }.build())
        nodeRepository.setNodes(listOf(node))
        viewModel = createViewModel(destNum = 456)
        assertEquals(456, viewModel.destNode.value?.num)
    }

    @Test
    fun `setModuleConfig sends the module config to the destination`() = runTest {
        val node = Node(num = 123, user = User.Builder().also { wb -> wb.id = "!123" }.build())
        nodeRepository.setNodes(listOf(node))
        viewModel = createViewModel()

        val config =
            org.meshtastic.proto.ModuleConfig.Builder()
                .also { wb ->
                    wb.mqtt =
                        org.meshtastic.proto.ModuleConfig.MQTTConfig.Builder().also { wb -> wb.enabled = true }.build()
                }
                .build()

        viewModel.setModuleConfig(config)

        verifySuspend { adminController.setModuleConfig(123, config) }
        assertEquals(true, viewModel.radioConfigState.value.moduleConfig.mqtt?.enabled)
    }

    @Test
    fun `setModuleConfig merges mesh_beacon into state before the radio answers`() = runTest {
        val node = Node(num = 123, user = User.Builder().also { wb -> wb.id = "!123" }.build())
        nodeRepository.setNodes(listOf(node))
        viewModel = createViewModel()
        val beacon = MeshBeaconConfig.Builder().also { wb -> wb.broadcast_message = "join us" }.build()

        viewModel.setModuleConfig(ModuleConfig.Builder().also { wb -> wb.mesh_beacon = beacon }.build())

        // Pins the optimistic mesh_beacon merge: a second save in the same session reads this, not the radio's reply.
        assertEquals(beacon, viewModel.radioConfigState.value.moduleConfig.mesh_beacon)
    }

    @Test
    fun `setFixedPosition sends the position to the destination`() = runTest {
        val node = Node(num = 123, user = User.Builder().also { wb -> wb.id = "!123" }.build())
        nodeRepository.setNodes(listOf(node))
        viewModel = createViewModel()

        val pos = org.meshtastic.core.model.Position(latitude = 1.0, longitude = 2.0, altitude = 0)
        everySuspend { adminController.setFixedPosition(any(), any()) } returns Unit

        viewModel.setFixedPosition(pos)

        verifySuspend { adminController.setFixedPosition(123, pos) }
    }

    @Test
    fun `removeFixedPosition sends the removal position`() = runTest {
        val node = Node(num = 123, user = User.Builder().also { wb -> wb.id = "!123" }.build())
        nodeRepository.setNodes(listOf(node))
        viewModel = createViewModel()

        val sent = mutableListOf<Position>()
        everySuspend { adminController.setFixedPosition(any(), capture(sent)) } returns Unit

        viewModel.removeFixedPosition()

        verifySuspend { adminController.setFixedPosition(123, any()) }
        assertTrue(sent.single().isFixedPositionRemoval())
    }

    @Test
    fun `installProfile calls useCase`() = runTest {
        val node = Node(num = 123, user = User.Builder().also { wb -> wb.id = "!123" }.build())
        nodeRepository.setNodes(listOf(node))
        viewModel = createViewModel()

        val profile = DeviceProfile.Builder().build()
        everySuspend { installProfileUseCase(any(), any(), any(), any(), any()) } returns Unit

        viewModel.installProfile(profile)

        verifySuspend { installProfileUseCase(123, profile, any(), null, true) }
    }

    @Test
    fun `installProfile surfaces malformed channel URL in snackbar`() = runTest {
        val node = Node(num = 123, user = User.Builder().also { wb -> wb.id = "!123" }.build())
        nodeRepository.setNodes(listOf(node))
        viewModel = createViewModel()
        val profile = DeviceProfile.Builder().also { wb -> wb.channel_url = "not-a-channel-url" }.build()
        everySuspend { installProfileUseCase(any(), any(), any(), any(), any()) } calls
            {
                throw MalformedMeshtasticUrlException("bad profile")
            }
        val snackbar = snackbarShown()

        viewModel.installProfile(profile)
        snackbar.await()

        verify { snackbarManager.showSnackbar(message = "This Channel URL is invalid and can not be used") }
    }

    @Test
    fun readResponsesUpdateStateForEachResultType() = runTest {
        val node = Node(num = 123, user = User.Builder().also { wb -> wb.id = "!123" }.build())
        nodeRepository.setNodes(listOf(node))
        viewModel = createViewModel()

        // ConfigResponse
        val configResponse =
            Config.Builder()
                .also { wb -> wb.lora = Config.LoRaConfig.Builder().also { wb -> wb.hop_limit = 5 }.build() }
                .build()
        everySuspend { adminController.getConfig(any(), any()) } returns AdminResult.Success(configResponse)
        viewModel.setResponseStateLoading(ConfigRoute.DEVICE)
        runCurrent()
        assertEquals(5, viewModel.radioConfigState.value.radioConfig.lora?.hop_limit)

        // ModuleConfigResponse
        val moduleResponse =
            ModuleConfig.Builder()
                .also { wb ->
                    wb.telemetry =
                        ModuleConfig.TelemetryConfig.Builder().also { wb -> wb.device_update_interval = 300 }.build()
                }
                .build()
        everySuspend { adminController.getModuleConfig(any(), any()) } returns AdminResult.Success(moduleResponse)
        viewModel.setResponseStateLoading(ModuleRoute.TELEMETRY)
        runCurrent()
        assertEquals(300, viewModel.radioConfigState.value.moduleConfig.telemetry?.device_update_interval)

        // Owner
        val user = User.Builder().also { wb -> wb.long_name = "New Name" }.build()
        everySuspend { adminController.getOwner(any()) } returns AdminResult.Success(user)
        viewModel.setResponseStateLoading(ConfigRoute.USER)
        runCurrent()
        assertEquals("New Name", viewModel.radioConfigState.value.userConfig.long_name)

        // Ringtone
        everySuspend { adminController.getRingtone(any()) } returns AdminResult.Success("bell.mp3")
        viewModel.setResponseStateLoading(ModuleRoute.EXT_NOTIFICATION)
        runCurrent()
        assertEquals("bell.mp3", viewModel.radioConfigState.value.ringtone)

        // Error
        everySuspend { adminController.getConfig(any(), any()) } returns AdminResult.Failed(Routing.Error.NO_RESPONSE)
        viewModel.setResponseStateLoading(ConfigRoute.DEVICE)
        runCurrent()
        assertTrue(viewModel.radioConfigState.value.responseState is ResponseState.Error)
    }

    // Catches: the next-channel chain in applyChannel dropped, wrong index, or stopping early.
    @Test
    fun `channels route reads channels in order until the first disabled one`() = runTest {
        val node = Node(num = 123, user = User.Builder().also { wb -> wb.id = "!123" }.build())
        nodeRepository.setNodes(listOf(node))
        everySuspend { adminController.getChannel(any(), any()) } calls
            {
                val index = it.args[1] as Int
                AdminResult.Success(
                    Channel.Builder()
                        .also { wb ->
                            wb.index = index
                            wb.role =
                                when (index) {
                                    0 -> Channel.Role.PRIMARY
                                    1 -> Channel.Role.SECONDARY
                                    else -> Channel.Role.DISABLED
                                }
                            wb.settings =
                                ChannelSettings.Builder()
                                    .also { s -> s.name = listOf("Primary", "Second").getOrElse(index) { "" } }
                                    .build()
                        }
                        .build(),
                )
            }
        everySuspend { adminController.getConfig(any(), any()) } returns AdminResult.Success(Config.Builder().build())
        viewModel = createViewModel()

        viewModel.setResponseStateLoading(ConfigRoute.CHANNELS)
        runCurrent()

        assertEquals(listOf("Primary", "Second"), viewModel.radioConfigState.value.channelList.map { it.name })
        verifySuspend { adminController.getChannel(123, 2) }
        verifySuspend(exactly(0)) { adminController.getChannel(123, 3) }
    }

    // Catches: applyConnectionStatus writing the wrong field or being unwired.
    @Test
    fun `network route stores the connection status`() = runTest {
        val node = Node(num = 123, user = User.Builder().also { wb -> wb.id = "!123" }.build())
        nodeRepository.setNodes(listOf(node))
        val status = DeviceConnectionStatus.Builder().build()
        everySuspend { adminController.getDeviceConnectionStatus(any()) } returns AdminResult.Success(status)
        everySuspend { adminController.getConfig(any(), any()) } returns AdminResult.Success(Config.Builder().build())
        viewModel = createViewModel()

        viewModel.setResponseStateLoading(ConfigRoute.NETWORK)
        runCurrent()

        assertEquals(status, viewModel.radioConfigState.value.deviceConnectionStatus)
    }

    // Catches: applyCannedMessages unwired.
    @Test
    fun `canned message route stores the canned messages`() = runTest {
        val node = Node(num = 123, user = User.Builder().also { wb -> wb.id = "!123" }.build())
        nodeRepository.setNodes(listOf(node))
        everySuspend { adminController.getCannedMessages(any()) } returns AdminResult.Success("Hello|World")
        everySuspend { adminController.getModuleConfig(any(), any()) } returns
            AdminResult.Success(ModuleConfig.Builder().build())
        viewModel = createViewModel()

        viewModel.setResponseStateLoading(ModuleRoute.CANNED_MESSAGE)
        runCurrent()

        assertEquals("Hello|World", viewModel.radioConfigState.value.cannedMessageMessages)
    }

    // Catches: the DFU half of the deadline rule lost; a remote request never sets restartExpected, so only the DFU
    // clause can settle it as success.
    @Test
    fun `a remote DFU request with no answer settles as success at the deadline`() = runTest {
        val localNode = Node(num = 100, user = User.Builder().also { wb -> wb.id = "!100" }.build())
        val remoteNode = Node(num = 456, user = User.Builder().also { wb -> wb.id = "!456" }.build())
        nodeRepository.setNodes(listOf(localNode, remoteNode))
        nodeRepository.setMyNodeInfo(myNodeInfo(myNodeNum = 100))
        everySuspend { adminController.getDeviceMetadata(any()) } returns
            AdminResult.Success(DeviceMetadata.Builder().build())
        viewModel = createViewModel(destNum = 456)

        viewModel.setResponseStateLoading(AdminRoute.REBOOT_DFU)
        runCurrent()
        verifySuspend { adminActionsUseCase.rebootToDfu(456) }
        assertTrue(viewModel.radioConfigState.value.responseState is ResponseState.Loading)
        assertFalse(nodeRestartTracker.restartExpected.value)

        advanceTimeBy(31_000)
        runCurrent()

        assertTrue(viewModel.radioConfigState.value.responseState is ResponseState.Success)
    }

    // Catches: a refusal that ends only its own request, leaving a sibling read's deadline to overwrite the specific
    // failure with a timeout.
    @Test
    fun `a refused read stops its sibling read from timing out over the refusal`() = runTest {
        val node = Node(num = 123, user = User.Builder().also { wb -> wb.id = "!123" }.build())
        nodeRepository.setNodes(listOf(node))
        val channelReply = CompletableDeferred<AdminResult<Channel>>()
        everySuspend { adminController.getChannel(any(), any()) } calls { channelReply.await() }
        viewModel = createViewModel()

        viewModel.setResponseStateLoading(ConfigRoute.CHANNELS)
        runCurrent()
        verifySuspend { adminController.getConfig(123, AdminMessage.ConfigType.LORA_CONFIG.value) }

        val failure = UiText.Resource(getStringResFrom(Routing.Error.MAX_RETRANSMIT.value))
        channelReply.complete(AdminResult.Failed(Routing.Error.MAX_RETRANSMIT))
        runCurrent()
        assertEquals(ResponseState.Error(failure), viewModel.radioConfigState.value.responseState)

        advanceTimeBy(31_000)
        runCurrent()

        assertEquals(ResponseState.Error(failure), viewModel.radioConfigState.value.responseState)
    }

    // Catches: a write acknowledged while a read route is open staying tracked forever, so every later save takes
    // incrementCompleted instead of success.
    @Test
    fun `a save acknowledged while a read route is open does not block the next save`() = runTest {
        val localNode = Node(num = 100, user = User.Builder().also { wb -> wb.id = "!100" }.build())
        val remoteNode = Node(num = 456, user = User.Builder().also { wb -> wb.id = "!456" }.build())
        val config =
            Config.Builder()
                .also { wb ->
                    wb.device = Config.DeviceConfig.Builder().also { d -> d.node_info_broadcast_secs = 900 }.build()
                }
                .build()
        val saveReply = CompletableDeferred<AdminResult<Unit>>()
        val readReply = CompletableDeferred<AdminResult<Config>>()
        everySuspend { adminController.setConfig(any(), any()) } calls { saveReply.await() }
        everySuspend { adminController.getConfig(any(), any()) } calls { readReply.await() }
        nodeRepository.setNodes(listOf(localNode, remoteNode))
        nodeRepository.setMyNodeInfo(myNodeInfo(myNodeNum = 100))
        viewModel = createViewModel(destNum = 456)

        viewModel.setConfig(config)
        runCurrent()
        viewModel.loadConfigRoute(ConfigRoute.DEVICE)
        runCurrent()
        saveReply.complete(AdminResult.Success(Unit))
        runCurrent()
        readReply.complete(AdminResult.Success(config))
        runCurrent()

        advanceTimeBy(31_000)
        runCurrent()

        everySuspend { adminController.setConfig(any(), any()) } returns AdminResult.Success(Unit)
        viewModel.setConfig(config)
        runCurrent()

        assertTrue(viewModel.radioConfigState.value.responseState is ResponseState.Success)
    }

    @Test
    fun `routing error clears request timeout without replacing the specific failure`() = runTest {
        val node = Node(num = 123, user = User.Builder().also { wb -> wb.id = "!123" }.build())
        nodeRepository.setNodes(listOf(node))
        val ownerReply = CompletableDeferred<AdminResult<User>>()
        everySuspend { adminController.getOwner(any()) } calls { ownerReply.await() }

        viewModel = createViewModel()
        viewModel.setResponseStateLoading(ConfigRoute.USER)
        runCurrent()
        verifySuspend { adminController.getOwner(123) }

        val failure = UiText.Resource(getStringResFrom(Routing.Error.MAX_RETRANSMIT.value))
        ownerReply.complete(AdminResult.Failed(Routing.Error.MAX_RETRANSMIT))
        runCurrent()

        assertEquals(ResponseState.Error(failure), viewModel.radioConfigState.value.responseState)

        advanceTimeBy(31_000)
        runCurrent()

        assertEquals(ResponseState.Error(failure), viewModel.radioConfigState.value.responseState)
    }

    @Test
    fun lateRemoteReadSatisfiesItsPendingRetry() = runTest {
        val localNode = Node(num = 100, user = User.Builder().also { wb -> wb.id = "!100" }.build())
        val remoteNode = Node(num = 456, user = User.Builder().also { wb -> wb.id = "!456" }.build())
        val readReplies = mutableListOf<CompletableDeferred<AdminResult<Config>>>()
        val config =
            Config.Builder()
                .also { wb ->
                    wb.device = Config.DeviceConfig.Builder().also { wb -> wb.node_info_broadcast_secs = 900 }.build()
                }
                .build()

        everySuspend { adminController.getConfig(any(), any()) } calls
            {
                CompletableDeferred<AdminResult<Config>>().also { reply -> readReplies += reply }.await()
            }
        nodeRepository.setNodes(listOf(localNode, remoteNode))
        nodeRepository.setMyNodeInfo(myNodeInfo(myNodeNum = 100))
        viewModel = createViewModel(destNum = 456)

        viewModel.loadConfigRoute(ConfigRoute.DEVICE)
        runCurrent()
        advanceTimeBy(30_001)
        runCurrent()

        assertEquals(timeoutError, viewModel.radioConfigState.value.responseState)

        advanceTimeBy(55_000)
        viewModel.loadConfigRoute(ConfigRoute.DEVICE)
        runCurrent()
        assertTrue(viewModel.radioConfigState.value.responseState is ResponseState.Loading)
        assertEquals(2, readReplies.size)

        advanceTimeBy(5_000)
        readReplies[0].complete(AdminResult.Success(config))
        runCurrent()

        assertEquals(config.device, viewModel.radioConfigState.value.radioConfig.device)
        assertEquals(ResponseState.Empty, viewModel.radioConfigState.value.responseState)

        advanceTimeBy(31_000)
        runCurrent()
        assertEquals(ResponseState.Empty, viewModel.radioConfigState.value.responseState)
    }

    @Test
    fun `late remote read clears its deadline error without a retry`() = runTest {
        val localNode = Node(num = 100, user = User.Builder().also { wb -> wb.id = "!100" }.build())
        val remoteNode = Node(num = 456, user = User.Builder().also { wb -> wb.id = "!456" }.build())
        val config =
            Config.Builder()
                .also { wb ->
                    wb.device = Config.DeviceConfig.Builder().also { wb -> wb.node_info_broadcast_secs = 900 }.build()
                }
                .build()
        val readReply = CompletableDeferred<AdminResult<Config>>()

        everySuspend { adminController.getConfig(any(), any()) } calls { readReply.await() }
        nodeRepository.setNodes(listOf(localNode, remoteNode))
        nodeRepository.setMyNodeInfo(myNodeInfo(myNodeNum = 100))
        viewModel = createViewModel(destNum = 456)

        viewModel.loadConfigRoute(ConfigRoute.DEVICE)
        runCurrent()
        advanceTimeBy(30_001)
        runCurrent()
        assertEquals(timeoutError, viewModel.radioConfigState.value.responseState)

        readReply.complete(AdminResult.Success(config))
        runCurrent()

        assertEquals(config.device, viewModel.radioConfigState.value.radioConfig.device)
        assertEquals(ResponseState.Empty, viewModel.radioConfigState.value.responseState)
    }

    @Test
    fun `late owner read is retained when the user route reads only the owner`() = runTest {
        val localNode = Node(num = 100, user = User.Builder().also { wb -> wb.id = "!100" }.build())
        val remoteNode =
            Node(
                num = 456,
                user = User.Builder().also { wb -> wb.id = "!456" }.build(),
                metadata = DeviceMetadata.Builder().also { wb -> wb.firmware_version = "2.7.19" }.build(),
            )
        val owner =
            User.Builder()
                .also { wb ->
                    wb.id = "!456"
                    wb.long_name = "Late"
                }
                .build()
        val ownerReply = CompletableDeferred<AdminResult<User>>()

        everySuspend { adminController.getOwner(any()) } calls { ownerReply.await() }
        nodeRepository.setNodes(listOf(localNode, remoteNode))
        nodeRepository.setMyNodeInfo(myNodeInfo(myNodeNum = 100))
        viewModel = createViewModel(destNum = 456)

        viewModel.loadConfigRoute(ConfigRoute.USER)
        runCurrent()
        advanceTimeBy(30_001)
        runCurrent()
        assertEquals(timeoutError, viewModel.radioConfigState.value.responseState)

        // Firmware without the status message module answers one get, so the route is a single-response read
        // whatever ConfigRoute.USER.hasReadFanOut says, and the late owner still lands.
        ownerReply.complete(AdminResult.Success(owner))
        runCurrent()

        assertEquals(owner, viewModel.radioConfigState.value.userConfig)
        assertEquals(ResponseState.Empty, viewModel.radioConfigState.value.responseState)
    }

    @Test
    fun `newer save supersedes a retained read and its retry`() = runTest {
        val localNode = Node(num = 100, user = User.Builder().also { wb -> wb.id = "!100" }.build())
        val remoteNode = Node(num = 456, user = User.Builder().also { wb -> wb.id = "!456" }.build())
        val staleConfig =
            Config.Builder()
                .also { wb ->
                    wb.device = Config.DeviceConfig.Builder().also { wb -> wb.node_info_broadcast_secs = 900 }.build()
                }
                .build()
        val savedConfig =
            Config.Builder()
                .also { wb ->
                    wb.device = Config.DeviceConfig.Builder().also { wb -> wb.node_info_broadcast_secs = 300 }.build()
                }
                .build()
        val readReplies = mutableListOf<CompletableDeferred<AdminResult<Config>>>()
        val saveReply = CompletableDeferred<AdminResult<Unit>>()

        everySuspend { adminController.getConfig(any(), any()) } calls
            {
                CompletableDeferred<AdminResult<Config>>().also { reply -> readReplies += reply }.await()
            }
        everySuspend { adminController.setConfig(any(), any()) } calls { saveReply.await() }
        nodeRepository.setNodes(listOf(localNode, remoteNode))
        nodeRepository.setMyNodeInfo(myNodeInfo(myNodeNum = 100))
        viewModel = createViewModel(destNum = 456)

        viewModel.loadConfigRoute(ConfigRoute.DEVICE)
        runCurrent()
        advanceTimeBy(30_001)
        runCurrent()

        viewModel.loadConfigRoute(ConfigRoute.DEVICE)
        runCurrent()
        viewModel.setConfig(savedConfig)
        runCurrent()
        assertEquals(ResponseState.Loading(), viewModel.radioConfigState.value.responseState)

        readReplies[0].complete(AdminResult.Success(staleConfig))
        runCurrent()
        assertEquals(
            ResponseState.Loading(),
            viewModel.radioConfigState.value.responseState,
            "a superseded read must not clear the active save",
        )
        assertEquals(savedConfig.device, viewModel.radioConfigState.value.radioConfig.device)

        saveReply.complete(AdminResult.Success(Unit))
        runCurrent()
        assertTrue(viewModel.radioConfigState.value.responseState is ResponseState.Success)

        advanceTimeBy(31_000)
        runCurrent()
        assertTrue(viewModel.radioConfigState.value.responseState is ResponseState.Success)
    }

    @Test
    fun `remote write keeps max retransmit terminal`() = runTest {
        val localNode = Node(num = 100, user = User.Builder().also { wb -> wb.id = "!100" }.build())
        val remoteNode = Node(num = 456, user = User.Builder().also { wb -> wb.id = "!456" }.build())
        val maxRetransmit = UiText.Resource(getStringResFrom(Routing.Error.MAX_RETRANSMIT.value))
        val config =
            Config.Builder()
                .also { wb ->
                    wb.device = Config.DeviceConfig.Builder().also { wb -> wb.node_info_broadcast_secs = 900 }.build()
                }
                .build()

        everySuspend { adminController.setConfig(any(), any()) } returns
            AdminResult.Failed(Routing.Error.MAX_RETRANSMIT)
        nodeRepository.setNodes(listOf(localNode, remoteNode))
        nodeRepository.setMyNodeInfo(myNodeInfo(myNodeNum = 100))
        viewModel = createViewModel(destNum = 456)

        viewModel.setConfig(config)
        runCurrent()

        assertEquals(ResponseState.Error(maxRetransmit), viewModel.radioConfigState.value.responseState)

        advanceTimeBy(31_000)
        runCurrent()
        assertEquals(ResponseState.Error(maxRetransmit), viewModel.radioConfigState.value.responseState)
    }

    @Test
    fun nodeUnreachableOutsideARestartReportsNotConnectedAtOnce() = runTest {
        val node = Node(num = 123, user = User.Builder().also { wb -> wb.id = "!123" }.build())
        nodeRepository.setNodes(listOf(node))
        nodeRepository.setMyNodeInfo(myNodeInfo(myNodeNum = 123))
        everySuspend { adminController.getConfig(any(), any()) } returns AdminResult.NodeUnreachable
        viewModel = createViewModel()
        runCurrent()

        viewModel.loadConfigRoute(ConfigRoute.DEVICE)
        runCurrent()

        assertFalse(nodeRestartTracker.restartExpected.value)
        assertEquals(
            ResponseState.Error(UiText.Resource(Res.string.not_connected)),
            viewModel.radioConfigState.value.responseState,
        )
    }

    @Test
    fun aRequestThatThrowsReportsItsFailureAtOnce() = runTest {
        val node = Node(num = 123, user = User.Builder().also { wb -> wb.id = "!123" }.build())
        nodeRepository.setNodes(listOf(node))
        nodeRepository.setMyNodeInfo(myNodeInfo(myNodeNum = 123))
        everySuspend { adminController.getConfig(any(), any()) } throws IllegalStateException("radio gone")
        viewModel = createViewModel()
        runCurrent()

        viewModel.loadConfigRoute(ConfigRoute.DEVICE)
        runCurrent()

        assertEquals(
            ResponseState.Error(UiText.DynamicString("radio gone")),
            viewModel.radioConfigState.value.responseState,
        )
    }

    @Test
    fun nodeUnreachableDuringAnExpectedRestartLeavesTheSavePending() = runTest {
        val node = Node(num = 123, user = User.Builder().also { wb -> wb.id = "!123" }.build())
        nodeRepository.setNodes(listOf(node))
        nodeRepository.setMyNodeInfo(myNodeInfo(myNodeNum = 123))
        everySuspend { adminController.setConfig(any(), any()) } returns AdminResult.NodeUnreachable
        viewModel = createViewModel()
        runCurrent()

        nodeRestartTracker.onConnected()
        viewModel.setConfig(
            Config.Builder()
                .also { wb ->
                    wb.network = Config.NetworkConfig.Builder().also { wb -> wb.wifi_enabled = true }.build()
                }
                .build(),
        )
        runCurrent()

        assertTrue(nodeRestartTracker.restartExpected.value)
        assertTrue(viewModel.radioConfigState.value.responseState is ResponseState.Loading)
    }

    @Test
    fun answerForATimedOutRequestIsDropped() = runTest {
        val node = Node(num = 123, user = User.Builder().also { wb -> wb.id = "!123" }.build())
        nodeRepository.setNodes(listOf(node))
        nodeRepository.setMyNodeInfo(myNodeInfo(myNodeNum = 123))
        val owner = User.Builder().also { wb -> wb.long_name = "Late" }.build()
        val ownerReply = CompletableDeferred<AdminResult<User>>()
        everySuspend { adminController.getOwner(any()) } calls { ownerReply.await() }
        viewModel = createViewModel()
        runCurrent()

        viewModel.loadConfigRoute(ConfigRoute.USER)
        runCurrent()
        advanceTimeBy(30_001)
        runCurrent()
        assertEquals(timeoutError, viewModel.radioConfigState.value.responseState)
        val userConfigBefore = viewModel.radioConfigState.value.userConfig

        // A local read is never retained past its deadline, so its token is retired and the answer has no owner.
        ownerReply.complete(AdminResult.Success(owner))
        runCurrent()

        assertEquals(userConfigBefore, viewModel.radioConfigState.value.userConfig)
        assertEquals(timeoutError, viewModel.radioConfigState.value.responseState)
    }

    @Test
    fun answerForAClearedRequestIsIgnored() = runTest {
        val localNode = Node(num = 100, user = User.Builder().also { wb -> wb.id = "!100" }.build())
        val remoteNode = Node(num = 456, user = User.Builder().also { wb -> wb.id = "!456" }.build())
        nodeRepository.setNodes(listOf(localNode, remoteNode))
        nodeRepository.setMyNodeInfo(myNodeInfo(myNodeNum = 100))
        val owner = User.Builder().also { wb -> wb.long_name = "Late" }.build()
        val ownerReply = CompletableDeferred<AdminResult<User>>()
        everySuspend { adminController.getOwner(any()) } calls { ownerReply.await() }
        viewModel = createViewModel(destNum = 456)
        runCurrent()

        viewModel.loadConfigRoute(ConfigRoute.USER)
        runCurrent()
        val userConfigBefore = viewModel.radioConfigState.value.userConfig
        viewModel.clearPacketResponse()

        ownerReply.complete(AdminResult.Success(owner))
        runCurrent()

        assertEquals(userConfigBefore, viewModel.radioConfigState.value.userConfig)
        assertEquals(ResponseState.Empty, viewModel.radioConfigState.value.responseState)
    }

    @Test
    fun aSaveKeepsRunningAfterItsRequestIsCleared() = runTest {
        val node = Node(num = 123, user = User.Builder().also { wb -> wb.id = "!123" }.build())
        nodeRepository.setNodes(listOf(node))
        nodeRepository.setMyNodeInfo(myNodeInfo(myNodeNum = 123))
        val saveReply = CompletableDeferred<AdminResult<Unit>>()
        var saveFinished = false
        everySuspend { adminController.setOwner(any(), any()) } calls
            {
                saveReply.await().also { saveFinished = true }
            }
        viewModel = createViewModel()
        runCurrent()

        viewModel.setOwner(User.Builder().also { wb -> wb.long_name = "Saved" }.build())
        runCurrent()
        viewModel.clearPacketResponse()
        saveReply.complete(AdminResult.Success(Unit))
        runCurrent()

        // The controller projects a write onto the local cache when it returns, so dismissing must not cancel it.
        assertTrue(saveFinished)
        assertEquals(ResponseState.Empty, viewModel.radioConfigState.value.responseState)
    }

    @Test
    fun `Admin actions call correct useCases`() = runTest {
        val node = Node(num = 123, user = User.Builder().also { wb -> wb.id = "!123" }.build())
        nodeRepository.setNodes(listOf(node))
        // The metadata preflight both gates the action and tells the shutdown branch the node can shut down.
        everySuspend { adminController.getDeviceMetadata(any()) } returns
            AdminResult.Success(DeviceMetadata.Builder().also { wb -> wb.canShutdown = true }.build())

        viewModel = createViewModel()

        // SHUTDOWN
        viewModel.setResponseStateLoading(AdminRoute.SHUTDOWN)
        runCurrent()
        verifySuspend { adminActionsUseCase.shutdown(123) }

        // NODEDB_RESET
        viewModel.setResponseStateLoading(AdminRoute.NODEDB_RESET)
        runCurrent()
        verifySuspend { adminActionsUseCase.nodedbReset(123, any(), any()) }
    }

    @Test
    fun `setResponseStateLoading reads each route from the controller`() = runTest {
        val node = Node(num = 123, user = User.Builder().also { wb -> wb.id = "!123" }.build())
        nodeRepository.setNodes(listOf(node))
        viewModel = createViewModel()

        // USER
        viewModel.setResponseStateLoading(ConfigRoute.USER)
        verifySuspend { adminController.getOwner(123) }
        assertTrue((viewModel.radioConfigState.value.responseState as ResponseState.Loading).showOverlay)

        // CHANNELS
        viewModel.setResponseStateLoading(ConfigRoute.CHANNELS)
        verifySuspend { adminController.getChannel(123, 0) }
        verifySuspend {
            adminController.getConfig(123, org.meshtastic.proto.AdminMessage.ConfigType.LORA_CONFIG.value)
        }

        // LORA
        viewModel.setResponseStateLoading(ConfigRoute.LORA)
        verifySuspend { adminController.getConfig(123, ConfigRoute.LORA.type) }
    }

    @Test
    fun `loadConfigRoute hides progress overlay for local settings refresh`() = runTest {
        val localNode = Node(num = 123, user = User.Builder().also { wb -> wb.id = "!123" }.build())
        nodeRepository.setNodes(listOf(localNode))
        nodeRepository.setMyNodeInfo(myNodeInfo(myNodeNum = 123))
        viewModel = createViewModel(destNum = null)

        viewModel.loadConfigRoute(ConfigRoute.USER)
        runCurrent()

        val loading = viewModel.radioConfigState.value.responseState as ResponseState.Loading
        assertFalse(loading.showOverlay)
        verifySuspend { adminController.getOwner(123) }
    }

    @Test
    fun `loadConfigRoute skips the fetch for local settings while disconnected`() = runTest {
        val localNode = Node(num = 123, user = User.Builder().also { wb -> wb.id = "!123" }.build())
        nodeRepository.setNodes(listOf(localNode))
        nodeRepository.setMyNodeInfo(myNodeInfo(myNodeNum = 123))
        every { serviceRepository.connectionState } returns MutableStateFlow(ConnectionState.Disconnected)
        viewModel = createViewModel(destNum = null)

        viewModel.loadConfigRoute(ConfigRoute.USER)
        runCurrent()

        assertTrue(viewModel.radioConfigState.value.responseState is ResponseState.Empty)
        verifySuspend(exactly(0)) { adminController.getOwner(any()) }
    }

    @Test
    fun `loadConfigRoute shows progress overlay for remote settings refresh`() = runTest {
        val localNode = Node(num = 100, user = User.Builder().also { wb -> wb.id = "!100" }.build())
        val remoteNode = Node(num = 456, user = User.Builder().also { wb -> wb.id = "!456" }.build())
        nodeRepository.setNodes(listOf(localNode, remoteNode))
        nodeRepository.setMyNodeInfo(myNodeInfo(myNodeNum = 100))
        viewModel = createViewModel(destNum = 456)

        viewModel.loadConfigRoute(ConfigRoute.USER)
        runCurrent()

        val loading = viewModel.radioConfigState.value.responseState as ResponseState.Loading
        assertTrue(loading.showOverlay)
        verifySuspend { adminController.getOwner(456) }
    }

    @Test
    fun `registerRequestId timeout clears request and sets error`() = runTest {
        val node = Node(num = 123, user = User.Builder().also { wb -> wb.id = "!123" }.build())
        nodeRepository.setNodes(listOf(node))
        viewModel = createViewModel()

        viewModel.setResponseStateLoading(ConfigRoute.USER)
        runCurrent()
        verifySuspend { adminController.getOwner(123) }

        // state should be loading
        assertTrue(viewModel.radioConfigState.value.responseState is ResponseState.Loading)

        // advance time past 30 seconds
        advanceTimeBy(31_000)
        runCurrent()

        assertTrue(viewModel.radioConfigState.value.responseState is ResponseState.Error)
    }

    @Test
    fun `ensureLoadingForRemote sets loading state for remote nodes`() = runTest {
        val localNode = Node(num = 100, user = User.Builder().also { wb -> wb.id = "!100" }.build())
        val remoteNode = Node(num = 456, user = User.Builder().also { wb -> wb.id = "!456" }.build())
        nodeRepository.setNodes(listOf(localNode, remoteNode))
        nodeRepository.setMyNodeInfo(myNodeInfo(myNodeNum = 100))

        val remoteVm = createViewModel(destNum = 456)

        // Remote VM starts with Empty responseState
        assertEquals(ResponseState.Empty, remoteVm.radioConfigState.value.responseState)
        assertFalse(remoteVm.radioConfigState.value.isLocal)

        // ensureLoadingForRemote should transition to Loading
        remoteVm.ensureLoadingForRemote()
        assertTrue(remoteVm.radioConfigState.value.responseState is ResponseState.Loading)
    }

    @Test
    fun `ensureLoadingForRemote is no-op for local session before node identity resolves`() = runTest {
        val localVm = createViewModel(destNum = null)

        localVm.ensureLoadingForRemote()
        assertEquals(ResponseState.Empty, localVm.radioConfigState.value.responseState)
    }

    @Test
    fun `ensureLoadingForRemote is no-op when already loading`() = runTest {
        val localNode = Node(num = 100, user = User.Builder().also { wb -> wb.id = "!100" }.build())
        val remoteNode = Node(num = 456, user = User.Builder().also { wb -> wb.id = "!456" }.build())
        nodeRepository.setNodes(listOf(localNode, remoteNode))
        nodeRepository.setMyNodeInfo(myNodeInfo(myNodeNum = 100))

        val remoteVm = createViewModel(destNum = 456)

        // Set loading first
        remoteVm.ensureLoadingForRemote()
        assertTrue(remoteVm.radioConfigState.value.responseState is ResponseState.Loading)

        // Calling again should still be Loading (no-op, not a new instance)
        remoteVm.ensureLoadingForRemote()
        assertTrue(remoteVm.radioConfigState.value.responseState is ResponseState.Loading)
    }

    @Test
    fun `local destination exposes its PlatformIO target`() = runTest {
        val localNode = Node(num = 100, user = User.Builder().also { wb -> wb.id = "!100" }.build())
        nodeRepository.setNodes(listOf(localNode))
        nodeRepository.setMyNodeInfo(myNodeInfo(myNodeNum = 100, pioEnv = "tlora-v2-1-1_8"))

        val localVm = createViewModel(destNum = 100)
        runCurrent()

        assertTrue(localVm.radioConfigState.value.isLocal)
        assertEquals("tlora-v2-1-1_8", localVm.radioConfigState.value.pioEnv)
    }

    @Test
    fun `local destination updates its PlatformIO target when identity is unchanged`() = runTest {
        val localNode = Node(num = 100, user = User.Builder().also { wb -> wb.id = "!100" }.build())
        nodeRepository.setNodes(listOf(localNode))
        nodeRepository.setMyNodeInfo(myNodeInfo(myNodeNum = 100, pioEnv = "tlora-t3s3-v1"))
        val localVm = createViewModel(destNum = 100)
        runCurrent()

        val beforeReflash =
            loRaBandwidthSelection(
                storedValue = 800,
                region = Config.LoRaConfig.RegionCode.LORA_24,
                hwModel = null,
                pioEnv = localVm.radioConfigState.value.pioEnv,
            )
        assertFalse(beforeReflash.options.orEmpty().any { it.wireValue == 1600 })

        nodeRepository.setMyNodeInfo(myNodeInfo(myNodeNum = 100, pioEnv = "my-esp32s3-diy-oled"))
        runCurrent()

        val afterReflash =
            loRaBandwidthSelection(
                storedValue = 800,
                region = Config.LoRaConfig.RegionCode.LORA_24,
                hwModel = null,
                pioEnv = localVm.radioConfigState.value.pioEnv,
            )
        assertTrue(localVm.radioConfigState.value.isLocal)
        assertEquals("my-esp32s3-diy-oled", localVm.radioConfigState.value.pioEnv)
        assertTrue(afterReflash.options.orEmpty().any { it.wireValue == 1600 })
    }

    @Test
    fun `remote destination never inherits gateway PlatformIO target`() = runTest {
        val localNode = Node(num = 100, user = User.Builder().also { wb -> wb.id = "!100" }.build())
        val remoteNode = Node(num = 456, user = User.Builder().also { wb -> wb.id = "!456" }.build())
        nodeRepository.setNodes(listOf(localNode, remoteNode))
        nodeRepository.setMyNodeInfo(myNodeInfo(myNodeNum = 100, pioEnv = "tlora-v2-1-1_8"))

        val remoteVm = createViewModel(destNum = 456)
        runCurrent()

        assertFalse(remoteVm.radioConfigState.value.isLocal)
        assertNull(remoteVm.radioConfigState.value.pioEnv)
    }

    @Test
    fun `loraRegionPresetMapFlow populates state`() = runTest {
        val node = Node(num = 123, user = User.Builder().also { wb -> wb.id = "!123" }.build())
        nodeRepository.setNodes(listOf(node))
        val mapFlow = MutableStateFlow<LoRaRegionPresetMap?>(null)
        every { radioConfigRepository.loraRegionPresetMapFlow } returns mapFlow
        viewModel = createViewModel()
        runCurrent()

        assertEquals(null, viewModel.radioConfigState.value.loraRegionPresetMap)

        val map =
            LoRaRegionPresetMap.Builder()
                .also { wb ->
                    wb.groups = listOf(LoRaPresetGroup.Builder().also { wb -> wb.licensed_only = true }.build())
                }
                .build()
        mapFlow.value = map
        runCurrent()

        assertEquals(map, viewModel.radioConfigState.value.loraRegionPresetMap)
    }

    @Test
    fun `localIsLicensed tracks the destination node's is_licensed flag`() = runTest {
        val node =
            Node(
                num = 123,
                user =
                User.Builder()
                    .also { wb ->
                        wb.id = "!123"
                        wb.is_licensed = true
                    }
                    .build(),
            )
        nodeRepository.setNodes(listOf(node))
        viewModel = createViewModel()
        runCurrent()

        assertTrue(viewModel.radioConfigState.value.localIsLicensed)
    }

    @Test
    fun `LoRa setConfig is not applied optimistically and issues no re-read`() = runTest {
        // A firmware region swap (e.g. EU sibling) is applied live; the form must reflect the device's actual
        // value, which is re-read on next LoRa-screen entry — NOT applied optimistically here, and the save ACK
        // must not trigger an in-place re-read (that would suppress the normal save-success UX).
        val node = Node(num = 123, user = User.Builder().also { wb -> wb.id = "!123" }.build())
        nodeRepository.setNodes(listOf(node))
        nodeRepository.setMyNodeInfo(myNodeInfo(myNodeNum = 123))
        val saveReply = CompletableDeferred<AdminResult<Unit>>()
        everySuspend { adminController.setConfig(any(), any()) } calls { saveReply.await() }
        every { radioConfigRepository.localConfigFlow } returns
            MutableStateFlow(
                LocalConfig.Builder()
                    .also { wb ->
                        wb.lora =
                            Config.LoRaConfig.Builder()
                                .also { wb -> wb.region = Config.LoRaConfig.RegionCode.EU_868 }
                                .build()
                    }
                    .build(),
            )
        viewModel = createViewModel(destNum = 123)
        runCurrent()

        viewModel.setConfig(
            Config.Builder()
                .also { wb ->
                    wb.lora =
                        Config.LoRaConfig.Builder().also { wb -> wb.region = Config.LoRaConfig.RegionCode.US }.build()
                }
                .build(),
        )
        runCurrent()

        // The optimistic state still reflects the device's value, not the requested region.
        assertEquals(Config.LoRaConfig.RegionCode.EU_868, viewModel.radioConfigState.value.radioConfig.lora?.region)

        saveReply.complete(AdminResult.Success(Unit))
        runCurrent()

        assertTrue(viewModel.radioConfigState.value.responseState is ResponseState.Success)
        verifySuspend(exactly(0)) { adminController.getConfig(any(), any()) }
    }

    @Test
    fun `backupSecurityKeys refuses to persist empty keys`() = runTest {
        val node = Node(num = 123, user = User.Builder().also { wb -> wb.id = "!123" }.build())
        nodeRepository.setNodes(listOf(node))
        viewModel = createViewModel()

        // Empty SecurityConfig is the fallback before the device's config response arrives — backing it up and later
        // restoring it would wipe the device's real keys with blanks.
        val snackbar = snackbarShown()
        viewModel.backupSecurityKeys(Config.SecurityConfig.Builder().build())
        snackbar.await()

        verify(exactly(0)) { securityKeyBackupStore.save(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `backupSecurityKeys persists real keys and the node's long name`() = runTest {
        val node =
            Node(
                num = 123,
                user =
                User.Builder()
                    .also { wb ->
                        wb.id = "!123"
                        wb.long_name = "Base Station"
                    }
                    .build(),
            )
        nodeRepository.setNodes(listOf(node))
        viewModel = createViewModel()

        val config =
            Config.SecurityConfig.Builder()
                .also { wb ->
                    wb.public_key = "pub".encodeUtf8()
                    wb.private_key = "priv".encodeUtf8()
                }
                .build()
        val snackbar = snackbarShown()
        viewModel.backupSecurityKeys(config)
        snackbar.await()

        // Pin the exact base64 so a public/private swap or encoding change is caught.
        verify { securityKeyBackupStore.save(123, "cHVi", "cHJpdg==", any(), "Base Station") }
    }

    @Test
    fun `restoreSecurityKeys is a no-op when no backup exists`() = runTest {
        val node = Node(num = 123, user = User.Builder().also { wb -> wb.id = "!123" }.build())
        nodeRepository.setNodes(listOf(node))
        viewModel = createViewModel()
        every { securityKeyBackupStore.get(123) } returns null

        val snackbar = snackbarShown()
        viewModel.restoreSecurityKeys()
        snackbar.await()

        verifySuspend(exactly(0)) { adminController.setConfig(any(), any()) }
    }

    @Test
    fun `restoreSecurityKeys pushes decoded config to the device on success`() = runTest {
        val node = Node(num = 123, user = User.Builder().also { wb -> wb.id = "!123" }.build())
        nodeRepository.setNodes(listOf(node))
        viewModel = createViewModel()

        val stored = StoredSecurityKeys(publicKeyBase64 = "cHVi", privateKeyBase64 = "cHJpdg==", timestamp = 1L)
        val decoded =
            Config.SecurityConfig.Builder()
                .also { wb ->
                    wb.public_key = "pub".encodeUtf8()
                    wb.private_key = "priv".encodeUtf8()
                }
                .build()
        every { securityKeyBackupStore.get(123) } returns stored
        every { importSecurityConfigUseCase(stored) } returns Result.success(decoded)

        val snackbar = snackbarShown()
        viewModel.restoreSecurityKeys()
        snackbar.await()

        verifySuspend {
            adminController.setConfig(123, Config.Builder().also { wb -> wb.security = decoded }.build())
        }
    }

    @Test
    fun `restoreSecurityKeys from another node number pushes those keys to this node`() = runTest {
        val node = Node(num = 123, user = User.Builder().also { wb -> wb.id = "!123" }.build())
        nodeRepository.setNodes(listOf(node))
        viewModel = createViewModel()

        val stored = StoredSecurityKeys(publicKeyBase64 = "cHVi", privateKeyBase64 = "cHJpdg==", timestamp = 1L)
        val decoded = Config.SecurityConfig.Builder().also { wb -> wb.public_key = "pub".encodeUtf8() }.build()
        every { securityKeyBackupStore.get(456) } returns stored
        every { importSecurityConfigUseCase(stored) } returns Result.success(decoded)

        val snackbar = snackbarShown()
        viewModel.restoreSecurityKeys(fromNodeNum = 456)
        snackbar.await()

        verifySuspend {
            adminController.setConfig(123, Config.Builder().also { wb -> wb.security = decoded }.build())
        }
    }

    @Test
    fun `securityKeyBackups lists this node first then newest first with known names`() = runTest {
        val node = Node(num = 123, user = User.Builder().also { wb -> wb.id = "!123" }.build())
        val oldSelf =
            Node(
                num = 456,
                user =
                User.Builder()
                    .also { wb ->
                        wb.id = "!456"
                        wb.long_name = "Old Self"
                    }
                    .build(),
            )
        nodeRepository.setNodes(listOf(node, oldSelf))
        viewModel = createViewModel()
        every { securityKeyBackupStore.all() } returns
            mapOf(
                456 to StoredSecurityKeys("a", "b", timestamp = 3L),
                789 to StoredSecurityKeys("c", "d", timestamp = 5L, longName = "Saved Name"),
                321 to StoredSecurityKeys("g", "h", timestamp = 2L),
                123 to StoredSecurityKeys("e", "f", timestamp = 1L),
            )

        val backups = viewModel.securityKeyBackups()

        assertEquals(listOf(123, 789, 456, 321), backups.map { it.nodeNum })
        assertEquals(listOf(true, false, false, false), backups.map { it.isCurrentNode })
        assertEquals("Saved Name", backups[1].longName)
        assertEquals("Old Self", backups[2].longName)
        assertNull(backups[3].longName)
    }

    @Test
    fun `deleteSecurityKeyBackup removes the backup under the picked node number`() = runTest {
        val node = Node(num = 123, user = User.Builder().also { wb -> wb.id = "!123" }.build())
        nodeRepository.setNodes(listOf(node))
        viewModel = createViewModel()

        val snackbar = snackbarShown()
        viewModel.deleteSecurityKeyBackup(backupNodeNum = 456)
        snackbar.await()

        verify { securityKeyBackupStore.delete(456) }
        verify(exactly(0)) { securityKeyBackupStore.delete(123) }
    }

    private fun fourChannelFixture() = listOf(
        ChannelSettings.Builder().also { wb -> wb.name = "A" }.build(),
        ChannelSettings.Builder().also { wb -> wb.name = "B" }.build(),
        ChannelSettings.Builder().also { wb -> wb.name = "C" }.build(),
        ChannelSettings.Builder().also { wb -> wb.name = "D" }.build(),
    )

    private fun myNodeInfo(
        myNodeNum: Int,
        pioEnv: String? = null,
        firmwareVersion: String? = null,
        deviceId: String? = null,
    ) = MyNodeInfo(
        myNodeNum = myNodeNum,
        hasGPS = false,
        model = null,
        firmwareVersion = firmwareVersion,
        couldUpdate = false,
        shouldUpdate = false,
        currentPacketId = 0,
        messageTimeoutMsec = 0,
        minAppVersion = 0,
        maxChannels = 8,
        hasWifi = false,
        channelUtilization = 0f,
        airUtilTx = 0f,
        deviceId = deviceId,
        pioEnv = pioEnv,
    )

    private fun nodeNumbered(num: Int) = Node(num = num, user = User.Builder().also { wb -> wb.id = "!$num" }.build())

    // Regression: a session opened on the connected node by number kept addressing the old number after a renumber.
    @Test
    fun `a session opened on the connected node by number follows it through a renumber`() = runTest {
        nodeRepository.setNodes(listOf(nodeNumbered(100)))
        nodeRepository.setMyNodeInfo(myNodeInfo(myNodeNum = 100, deviceId = "0a1b2c"))
        viewModel = createViewModel(destNum = 100)
        runCurrent()

        nodeRepository.setNodes(listOf(nodeNumbered(200)))
        nodeRepository.setMyNodeInfo(myNodeInfo(myNodeNum = 200, deviceId = "0a1b2c"))
        runCurrent()
        val user = User.Builder().also { wb -> wb.long_name = "Base" }.build()
        viewModel.saveUserConfig(user)
        runCurrent()

        verifySuspend { adminController.setOwner(200, user) }
    }

    // Regression: another radio's node can still be reached as a remote, so its number must not be dropped.
    @Test
    fun `a session on a node number keeps it when a different radio connects`() = runTest {
        nodeRepository.setNodes(listOf(nodeNumbered(100)))
        nodeRepository.setMyNodeInfo(myNodeInfo(myNodeNum = 100, deviceId = "0a1b2c"))
        viewModel = createViewModel(destNum = 100)
        runCurrent()

        // The new radio's node comes first, where a session with no number would find it.
        nodeRepository.setNodes(listOf(nodeNumbered(200), nodeNumbered(100)))
        nodeRepository.setMyNodeInfo(myNodeInfo(myNodeNum = 200, deviceId = "ffee00"))
        runCurrent()
        val user = User.Builder().also { wb -> wb.long_name = "Base" }.build()
        viewModel.saveUserConfig(user)
        runCurrent()

        verifySuspend { adminController.setOwner(100, user) }
    }

    @Test
    fun `local reboot-applying config save opens the restart window`() = runTest {
        val node = Node(num = 123, user = User.Builder().also { wb -> wb.id = "!123" }.build())
        nodeRepository.setNodes(listOf(node))
        nodeRepository.setMyNodeInfo(myNodeInfo(myNodeNum = 123))
        viewModel = createViewModel()
        runCurrent()

        nodeRestartTracker.onConnected()
        viewModel.setConfig(
            Config.Builder()
                .also { wb ->
                    wb.network = Config.NetworkConfig.Builder().also { wb -> wb.wifi_enabled = true }.build()
                }
                .build(),
        )
        runCurrent()

        assertTrue(nodeRestartTracker.restartExpected.value)
    }

    @Test
    fun `reboot-applying save resolves to restarting-success when the node drops`() = runTest {
        // The reboot the save triggers eats the routing ACK; the transport-drop during the restart window is the
        // real confirmation, so the save dialog must show success ("restarting") rather than a 30s timeout error.
        val node = Node(num = 123, user = User.Builder().also { wb -> wb.id = "!123" }.build())
        nodeRepository.setNodes(listOf(node))
        nodeRepository.setMyNodeInfo(myNodeInfo(myNodeNum = 123))
        val connFlow = MutableStateFlow<ConnectionState>(ConnectionState.Connected)
        every { serviceRepository.connectionState } returns connFlow
        viewModel = createViewModel()
        runCurrent()

        nodeRestartTracker.onConnected()
        viewModel.setConfig(
            Config.Builder()
                .also { wb ->
                    wb.network = Config.NetworkConfig.Builder().also { wb -> wb.wifi_enabled = true }.build()
                }
                .build(),
        )
        runCurrent()
        assertTrue(viewModel.radioConfigState.value.responseState is ResponseState.Loading)

        // The node reboots and the transport drops.
        connFlow.value = ConnectionState.Disconnected
        runCurrent()

        assertTrue(viewModel.radioConfigState.value.responseState is ResponseState.Success)
    }

    @Test
    fun `remote config save does not open the restart window`() = runTest {
        val localNode = Node(num = 100, user = User.Builder().also { wb -> wb.id = "!100" }.build())
        val remoteNode = Node(num = 456, user = User.Builder().also { wb -> wb.id = "!456" }.build())
        nodeRepository.setNodes(listOf(localNode, remoteNode))
        nodeRepository.setMyNodeInfo(myNodeInfo(myNodeNum = 100))
        viewModel = createViewModel(destNum = 456)
        runCurrent()

        nodeRestartTracker.onConnected()
        viewModel.setConfig(
            Config.Builder()
                .also { wb ->
                    wb.network = Config.NetworkConfig.Builder().also { wb -> wb.wifi_enabled = true }.build()
                }
                .build(),
        )
        runCurrent()

        assertFalse(nodeRestartTracker.restartExpected.value)
    }

    @Test
    fun `local module save that reboots opens the restart window`() = runTest {
        val node = Node(num = 123, user = User.Builder().also { wb -> wb.id = "!123" }.build())
        nodeRepository.setNodes(listOf(node))
        nodeRepository.setMyNodeInfo(myNodeInfo(myNodeNum = 123))
        viewModel = createViewModel()
        runCurrent()

        nodeRestartTracker.onConnected()
        viewModel.setModuleConfig(
            ModuleConfig.Builder()
                .also { wb -> wb.mqtt = ModuleConfig.MQTTConfig.Builder().also { wb -> wb.enabled = true }.build() }
                .build(),
        )
        runCurrent()

        assertTrue(nodeRestartTracker.restartExpected.value)
    }

    @Test
    fun `local Mesh Beacon save does not open the restart window`() = runTest {
        val node = Node(num = 123, user = User.Builder().also { wb -> wb.id = "!123" }.build())
        nodeRepository.setNodes(listOf(node))
        nodeRepository.setMyNodeInfo(myNodeInfo(myNodeNum = 123))
        viewModel = createViewModel()
        runCurrent()

        nodeRestartTracker.onConnected()
        viewModel.setModuleConfig(
            ModuleConfig.Builder()
                .also { wb ->
                    wb.mesh_beacon = MeshBeaconConfig.Builder().also { wb -> wb.broadcast_message = "hi" }.build()
                }
                .build(),
        )
        runCurrent()

        assertFalse(nodeRestartTracker.restartExpected.value)
    }
}

private val SAVE_SESSION = RadioSessionContext(generation = 7L, address = "tcp:radio")
