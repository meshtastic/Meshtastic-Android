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

import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okio.Buffer
import okio.BufferedSink
import okio.BufferedSource
import org.meshtastic.core.common.util.CommonUri
import org.meshtastic.core.domain.usecase.settings.AdminActionsUseCase
import org.meshtastic.core.domain.usecase.settings.ExportProfileUseCase
import org.meshtastic.core.domain.usecase.settings.ImportProfileUseCase
import org.meshtastic.core.domain.usecase.settings.ImportSecurityConfigUseCase
import org.meshtastic.core.domain.usecase.settings.InstallProfileUseCase
import org.meshtastic.core.domain.usecase.settings.ProcessRadioResponseUseCase
import org.meshtastic.core.domain.usecase.settings.RadioConfigUseCase
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.repository.AnalyticsPrefs
import org.meshtastic.core.repository.FileService
import org.meshtastic.core.repository.HomoglyphPrefs
import org.meshtastic.core.repository.LocationRepository
import org.meshtastic.core.repository.LocationService
import org.meshtastic.core.repository.MapConsentPrefs
import org.meshtastic.core.repository.MqttManager
import org.meshtastic.core.repository.NodeRestartTracker
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.SecurityKeyBackupStore
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.testing.FakeLockdownCoordinator
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.ui.util.SnackbarManager
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.Config
import org.meshtastic.proto.DeviceProfile
import org.meshtastic.proto.LocalConfig
import org.meshtastic.proto.LocalModuleConfig
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.ModuleConfig
import org.meshtastic.proto.Position
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileRoundTripTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val radioConfigRepository: RadioConfigRepository = mock(MockMode.autofill)
    private val packetRepository: PacketRepository = mock(MockMode.autofill)
    private val serviceRepository: ServiceRepository = mock(MockMode.autofill)
    private val nodeRepository = FakeNodeRepository()
    private val locationRepository: LocationRepository = mock(MockMode.autofill)
    private val mapConsentPrefs: MapConsentPrefs = mock(MockMode.autofill)
    private val analyticsPrefs: AnalyticsPrefs = mock(MockMode.autofill)
    private val homoglyphEncodingPrefs: HomoglyphPrefs = mock(MockMode.autofill)
    private val importSecurityConfigUseCase: ImportSecurityConfigUseCase = mock(MockMode.autofill)
    private val securityKeyBackupStore: SecurityKeyBackupStore = mock(MockMode.autofill)
    private val snackbarManager: SnackbarManager = mock(MockMode.autofill)
    private val nodeRestartTracker = NodeRestartTracker(CoroutineScope(SupervisorJob()))
    private val installProfileUseCase: InstallProfileUseCase = mock(MockMode.autofill)
    private val radioConfigUseCase: RadioConfigUseCase = mock(MockMode.autofill)
    private val adminActionsUseCase: AdminActionsUseCase = mock(MockMode.autofill)
    private val processRadioResponseUseCase: ProcessRadioResponseUseCase = mock(MockMode.autofill)
    private val locationService: LocationService = mock(MockMode.autofill)
    private val mqttManager: MqttManager = mock(MockMode.autofill)
    private lateinit var fileService: InMemoryFileService
    private lateinit var viewModel: RadioConfigViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fileService = InMemoryFileService()

        every { radioConfigRepository.deviceProfileFlow } returns MutableStateFlow(DeviceProfile.Builder().build())
        every { radioConfigRepository.localConfigFlow } returns MutableStateFlow(LocalConfig.Builder().build())
        every { radioConfigRepository.channelSetFlow } returns MutableStateFlow(ChannelSet.Builder().build())
        every { radioConfigRepository.moduleConfigFlow } returns MutableStateFlow(LocalModuleConfig.Builder().build())
        every { radioConfigRepository.deviceUIConfigFlow } returns MutableStateFlow(null)
        every { radioConfigRepository.fileManifestFlow } returns MutableStateFlow(emptyList())
        every { radioConfigRepository.loraRegionPresetMapFlow } returns MutableStateFlow(null)

        every { analyticsPrefs.analyticsAllowed } returns MutableStateFlow(false)
        every { homoglyphEncodingPrefs.homoglyphEncodingEnabled } returns MutableStateFlow(false)

        every { serviceRepository.meshPacketFlow } returns MutableSharedFlow<MeshPacket>()
        every { serviceRepository.connectionState } returns MutableStateFlow(ConnectionState.Connected)
        every { mqttManager.mqttConnectionState } returns
            MutableStateFlow(org.meshtastic.core.model.MqttConnectionState.Inactive)

        viewModel =
            RadioConfigViewModel(
                initialDestNum = null,
                radioConfigRepository = radioConfigRepository,
                packetRepository = packetRepository,
                serviceRepository = serviceRepository,
                nodeRepository = nodeRepository,
                locationRepository = locationRepository,
                mapConsentPrefs = mapConsentPrefs,
                analyticsPrefs = analyticsPrefs,
                homoglyphEncodingPrefs = homoglyphEncodingPrefs,
                importProfileUseCase = ImportProfileUseCase(),
                exportProfileUseCase = ExportProfileUseCase(),
                importSecurityConfigUseCase = importSecurityConfigUseCase,
                installProfileUseCase = installProfileUseCase,
                radioConfigUseCase = radioConfigUseCase,
                adminActionsUseCase = adminActionsUseCase,
                processRadioResponseUseCase = processRadioResponseUseCase,
                locationService = locationService,
                fileService = fileService,
                mqttManager = mqttManager,
                lockdownCoordinator = FakeLockdownCoordinator(),
                analytics = mock(MockMode.autofill),
                securityKeyBackupStore = securityKeyBackupStore,
                snackbarManager = snackbarManager,
                nodeRestartTracker = nodeRestartTracker,
                connectionManager = mock(MockMode.autofill),
            )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `profile export then import round trips representative DeviceProfile`() = runTest {
        assertRoundTrip(
            DeviceProfile.Builder()
                .also { wb ->
                    wb.long_name = "Round Trip Node"
                    wb.short_name = "RTN"
                    wb.channel_url = "https://meshtastic.org/e/#CgMSAQESBggBQANIAQ"
                    wb.config =
                        LocalConfig.Builder()
                            .also { wb ->
                                wb.device =
                                    Config.DeviceConfig.Builder()
                                        .also { wb ->
                                            wb.role = Config.DeviceConfig.Role.ROUTER
                                            wb.button_gpio = 7
                                        }
                                        .build()
                                wb.lora =
                                    Config.LoRaConfig.Builder()
                                        .also { wb ->
                                            wb.hop_limit = 5
                                            wb.use_preset = true
                                        }
                                        .build()
                                wb.power =
                                    Config.PowerConfig.Builder()
                                        .also { wb ->
                                            wb.is_power_saving = true
                                            wb.ls_secs = 300
                                        }
                                        .build()
                                wb.network =
                                    Config.NetworkConfig.Builder()
                                        .also { wb ->
                                            wb.wifi_enabled = true
                                            wb.wifi_ssid = "mesh-ssid"
                                            wb.wifi_psk = "mesh-pass"
                                            wb.ntp_server = "meshtastic.pool.ntp.org"
                                        }
                                        .build()
                            }
                            .build()
                    wb.module_config =
                        LocalModuleConfig.Builder()
                            .also { wb ->
                                wb.mqtt =
                                    ModuleConfig.MQTTConfig.Builder()
                                        .also { wb ->
                                            wb.enabled = true
                                            wb.proxy_to_client_enabled = true
                                            wb.root = "msh/US/test"
                                            wb.json_enabled = true
                                        }
                                        .build()
                                wb.telemetry =
                                    ModuleConfig.TelemetryConfig.Builder()
                                        .also { wb ->
                                            wb.device_update_interval = 300
                                            wb.environment_measurement_enabled = true
                                            wb.power_measurement_enabled = true
                                        }
                                        .build()
                                wb.canned_message =
                                    ModuleConfig.CannedMessageConfig.Builder()
                                        .also { wb ->
                                            wb.rotary1_enabled = true
                                            wb.inputbroker_pin_a = 12
                                            wb.inputbroker_pin_b = 13
                                            wb.send_bell = true
                                        }
                                        .build()
                                wb.statusmessage =
                                    ModuleConfig.StatusMessageConfig.Builder()
                                        .also { wb -> wb.node_status = "Ready to mesh" }
                                        .build()
                            }
                            .build()
                    wb.fixed_position =
                        Position.Builder()
                            .also { wb ->
                                wb.latitude_i = 327766650
                                wb.longitude_i = -967969890
                                wb.altitude = 138
                            }
                            .build()
                    wb.ringtone = "tones/notify.mp3"
                    wb.canned_messages = "Alpha|Bravo|Charlie"
                }
                .build(),
        )
    }

    @Test
    fun `profile export then import round trips empty DeviceProfile`() = runTest {
        assertRoundTrip(DeviceProfile.Builder().build())
    }

    @Test
    fun `profile export then import round trips partially populated DeviceProfile`() = runTest {
        assertRoundTrip(
            DeviceProfile.Builder()
                .also { wb ->
                    wb.long_name = "Partial Node"
                    wb.module_config =
                        LocalModuleConfig.Builder()
                            .also { wb ->
                                wb.statusmessage =
                                    ModuleConfig.StatusMessageConfig.Builder()
                                        .also { wb -> wb.node_status = "Standing by" }
                                        .build()
                            }
                            .build()
                }
                .build(),
        )
    }

    private suspend fun TestScope.assertRoundTrip(profile: DeviceProfile) {
        val exportUri = CommonUri.parse("content://test/profile.bin")
        val reExportUri = CommonUri.parse("content://test/profile-reexport.bin")
        var importedProfile: DeviceProfile? = null

        viewModel.exportProfile(exportUri, profile)
        runCurrent()

        viewModel.importProfile(exportUri) { importedProfile = it }
        runCurrent()

        val actualImportedProfile = assertNotNull(importedProfile)
        assertEquals(profile, actualImportedProfile)
        assertContentEquals(profile.encode(), fileService.readBytes(exportUri))
        assertContentEquals(profile.encode(), actualImportedProfile.encode())

        viewModel.exportProfile(reExportUri, actualImportedProfile)
        runCurrent()

        assertContentEquals(fileService.readBytes(exportUri), fileService.readBytes(reExportUri))
    }

    private class InMemoryFileService : FileService {
        private val files = mutableMapOf<String, ByteArray>()

        override suspend fun write(uri: CommonUri, block: suspend (BufferedSink) -> Unit): Boolean {
            val buffer = Buffer()
            block(buffer)
            files[uri.toString()] = buffer.readByteArray()
            return true
        }

        override suspend fun read(uri: CommonUri, block: suspend (BufferedSource) -> Unit): Boolean {
            val bytes = files[uri.toString()] ?: return false
            block(Buffer().write(bytes))
            return true
        }

        fun readBytes(uri: CommonUri): ByteArray = files[uri.toString()] ?: error("Missing file for $uri")
    }
}
