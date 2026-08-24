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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource
import org.koin.core.annotation.InjectedParam
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.common.util.CommonUri
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.common.util.safeCatching
import org.meshtastic.core.domain.usecase.settings.AdminActionsUseCase
import org.meshtastic.core.domain.usecase.settings.ExportProfileUseCase
import org.meshtastic.core.domain.usecase.settings.ImportProfileUseCase
import org.meshtastic.core.domain.usecase.settings.ImportSecurityConfigUseCase
import org.meshtastic.core.domain.usecase.settings.InstallProfileUseCase
import org.meshtastic.core.domain.usecase.settings.ProcessRadioResponseUseCase
import org.meshtastic.core.domain.usecase.settings.RadioConfigUseCase
import org.meshtastic.core.domain.usecase.settings.RadioResponseResult
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.MqttConnectionState
import org.meshtastic.core.model.MqttProbeStatus
import org.meshtastic.core.model.MyNodeInfo
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.Position
import org.meshtastic.core.model.util.MalformedMeshtasticUrlException
import org.meshtastic.core.repository.AnalyticsPrefs
import org.meshtastic.core.repository.FileService
import org.meshtastic.core.repository.HomoglyphPrefs
import org.meshtastic.core.repository.LocationRepository
import org.meshtastic.core.repository.LocationService
import org.meshtastic.core.repository.LockdownCoordinator
import org.meshtastic.core.repository.LockdownPassphraseStore
import org.meshtastic.core.repository.MapConsentPrefs
import org.meshtastic.core.repository.MqttManager
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.NodeRestartTracker
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.PlatformAnalytics
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.SecurityKeyBackupStore
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.UiText
import org.meshtastic.core.resources.cant_shutdown
import org.meshtastic.core.resources.channel_invalid
import org.meshtastic.core.resources.key_backup_deleted
import org.meshtastic.core.resources.key_backup_not_found
import org.meshtastic.core.resources.key_backup_restore_failed
import org.meshtastic.core.resources.key_backup_restored
import org.meshtastic.core.resources.key_backup_saved
import org.meshtastic.core.resources.timeout
import org.meshtastic.core.resources.unknown_error
import org.meshtastic.core.ui.util.SnackbarManager
import org.meshtastic.core.ui.util.getChannelList
import org.meshtastic.core.ui.viewmodel.safeLaunch
import org.meshtastic.feature.settings.navigation.ConfigRoute
import org.meshtastic.feature.settings.navigation.ModuleRoute
import org.meshtastic.proto.AdminMessage
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config
import org.meshtastic.proto.DeviceConnectionStatus
import org.meshtastic.proto.DeviceMetadata
import org.meshtastic.proto.DeviceProfile
import org.meshtastic.proto.DeviceUIConfig
import org.meshtastic.proto.FileInfo
import org.meshtastic.proto.HamParameters
import org.meshtastic.proto.HardwareModel
import org.meshtastic.proto.LoRaRegionPresetMap
import org.meshtastic.proto.LocalConfig
import org.meshtastic.proto.LocalModuleConfig
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.ModuleConfig
import org.meshtastic.proto.Routing
import org.meshtastic.proto.User
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

internal val MANUAL_CHANNEL_WRITE_DELAY: Duration = 1.seconds
private val REMOTE_READ_LATE_RESPONSE_GRACE: Duration = 2.minutes

/** Data class that represents the current RadioConfig state. */
data class RadioConfigState(
    val isLocal: Boolean = false,
    /** PlatformIO target for the configured destination; available only when that destination is directly connected. */
    val pioEnv: String? = null,
    val connected: Boolean = false,
    val route: String = "",
    val metadata: DeviceMetadata? = null,
    val userConfig: User = User(),
    val channelList: List<ChannelSettings> = emptyList(),
    val radioConfig: LocalConfig = LocalConfig(),
    val moduleConfig: LocalModuleConfig = LocalModuleConfig(),
    val ringtone: String = "",
    val cannedMessageMessages: String = "",
    val deviceConnectionStatus: DeviceConnectionStatus? = null,
    val deviceUIConfig: DeviceUIConfig? = null,
    val fileManifest: List<FileInfo> = emptyList(),
    /** Firmware's region→preset legality map (local handshake only); null when unavailable (firmware < 2.8). */
    val loraRegionPresetMap: LoRaRegionPresetMap? = null,
    /** Whether the device being configured is flagged as a licensed (amateur) operator; gates licensed-only presets. */
    val localIsLicensed: Boolean = false,
    val responseState: ResponseState<Boolean> = ResponseState.Empty,
    val analyticsAvailable: Boolean = true,
    val analyticsEnabled: Boolean = true,
    val nodeDbResetPreserveFavorites: Boolean = false,
)

@KoinViewModel
@Suppress("LongParameterList", "LargeClass")
open class RadioConfigViewModel(
    @InjectedParam private val destNum: Int?,
    private val radioConfigRepository: RadioConfigRepository,
    private val packetRepository: PacketRepository,
    private val serviceRepository: ServiceRepository,
    private val nodeRepository: NodeRepository,
    private val locationRepository: LocationRepository,
    private val mapConsentPrefs: MapConsentPrefs,
    private val analyticsPrefs: AnalyticsPrefs,
    private val homoglyphEncodingPrefs: HomoglyphPrefs,
    protected val importProfileUseCase: ImportProfileUseCase,
    protected val exportProfileUseCase: ExportProfileUseCase,
    protected val importSecurityConfigUseCase: ImportSecurityConfigUseCase,
    private val installProfileUseCase: InstallProfileUseCase,
    private val radioConfigUseCase: RadioConfigUseCase,
    private val adminActionsUseCase: AdminActionsUseCase,
    private val processRadioResponseUseCase: ProcessRadioResponseUseCase,
    private val locationService: LocationService,
    private val fileService: FileService,
    private val mqttManager: MqttManager,
    private val lockdownCoordinator: LockdownCoordinator,
    private val securityKeyBackupStore: SecurityKeyBackupStore,
    private val snackbarManager: SnackbarManager,
    private val nodeRestartTracker: NodeRestartTracker,
    private val analytics: PlatformAnalytics,
) : ViewModel() {

    val lockdownTokenInfo = serviceRepository.lockdownTokenInfo
    val sessionAuthorized = serviceRepository.sessionAuthorized
    val lockdownState = serviceRepository.lockdownState

    fun sendLockNow() {
        safeLaunch(tag = "sendLockNow") { lockdownCoordinator.lockNow() }
    }

    /**
     * Submits a lockdown passphrase: enables lockdown (from DISABLED), authenticates ([disable]=false from LOCKED), or
     * turns lockdown off ([disable]=true from UNLOCKED).
     */
    fun submitLockdownPassphrase(
        passphrase: String,
        boots: Int = LockdownPassphraseStore.DEFAULT_BOOTS,
        hours: Int = 0,
        maxSessionSeconds: Int = 0,
        disable: Boolean = false,
    ) {
        safeLaunch(tag = "submitLockdownPassphrase") {
            lockdownCoordinator.submitPassphrase(passphrase, boots, hours, maxSessionSeconds, disable)
        }
    }

    val analyticsAllowedFlow = analyticsPrefs.analyticsAllowed

    fun toggleAnalyticsAllowed() {
        analyticsPrefs.setAnalyticsAllowed(!analyticsPrefs.analyticsAllowed.value)
    }

    val homoglyphEncodingEnabledFlow = homoglyphEncodingPrefs.homoglyphEncodingEnabled

    fun toggleHomoglyphCharactersEncodingEnabled() {
        homoglyphEncodingPrefs.setHomoglyphEncodingEnabled(!homoglyphEncodingPrefs.homoglyphEncodingEnabled.value)
    }

    /** MQTT proxy connection state for the settings UI. */
    val mqttConnectionState: StateFlow<MqttConnectionState> = mqttManager.mqttConnectionState

    /** Whether this phone is currently running the MQTT proxy (relaying broker traffic to the connected device). */
    val mqttProxyActive: StateFlow<Boolean> = mqttManager.proxyActive

    /**
     * Phone-local control over the MQTT proxy that does **not** touch the device's persisted MQTT config. Turning it
     * off calls [MqttManager.stop] immediately, cutting the proxy firehose that can saturate the BLE link and MCU on
     * nRF devices — no slow device read-modify-write-readback round-trip. Turning it back on resumes proxying using the
     * device's current MQTT config (a no-op unless the device has MQTT and proxy-to-client enabled).
     */
    fun setMqttProxyActive(active: Boolean) {
        if (active) {
            val mqtt = radioConfigState.value.moduleConfig.mqtt
            mqttManager.startProxy(
                enabled = mqtt?.enabled == true,
                proxyToClientEnabled = mqtt?.proxy_to_client_enabled == true,
            )
        } else {
            mqttManager.stop()
        }
    }

    private val _mqttProbeStatus = MutableStateFlow<MqttProbeStatus?>(null)

    /** Latest result from a [probeMqttConnection] call, or `null` if no probe has been run. */
    val mqttProbeStatus: StateFlow<MqttProbeStatus?> = _mqttProbeStatus.asStateFlow()

    private var probeJob: Job? = null
    private val channelUpdateMutex = Mutex()
    private val manualChannelBatchJobsLock = SynchronizedObject()
    private val manualChannelBatchJobs = mutableSetOf<Job>()
    private var manualChannelBatchEnqueueing = false
    private val manualChannelBatchRequestIds = mutableSetOf<Int>()

    /**
     * Run a one-shot reachability/credentials probe against an MQTT broker. Cancels any in-flight probe before starting
     * a new one. Result is exposed via [mqttProbeStatus].
     */
    fun probeMqttConnection(address: String, tlsEnabled: Boolean, username: String?, password: String?) {
        probeJob?.cancel()
        _mqttProbeStatus.value = MqttProbeStatus.Probing
        probeJob =
            viewModelScope.launch {
                val result =
                    safeCatching { mqttManager.probe(address, tlsEnabled, username, password) }
                        .getOrElse { e ->
                            Logger.w(e) { "MQTT probe threw" }
                            MqttProbeStatus.Other(message = e.message)
                        }
                _mqttProbeStatus.value = result
            }
    }

    /** Clear the latest probe result (e.g. when the user edits the address). */
    fun clearMqttProbeStatus() {
        probeJob?.cancel()
        _mqttProbeStatus.value = null
    }

    private val _destNode = MutableStateFlow<Node?>(null)
    val destNode: StateFlow<Node?>
        get() = _destNode

    private val requestIds = MutableStateFlow(hashSetOf<Int>())

    // Main-dispatcher confined with the other ViewModel request state below. Keep every access on viewModelScope unless
    // these collections are moved behind explicit synchronization.
    private val requestTimeoutJobs = mutableMapOf<Int, Job>()

    // Only getter registrations enter this map; writes and destructive actions therefore cannot inherit late-read
    // recovery merely because the user saved from a screen whose route name is still selected.
    private val readRequestRoutes = mutableMapOf<Int, String>()
    private val deferredRemoteReadErrors = mutableMapOf<Int, UiText>()
    private val lateRemoteReads = mutableMapOf<Int, LateRemoteRead>()
    private val _radioConfigState = MutableStateFlow(RadioConfigState())
    val radioConfigState: StateFlow<RadioConfigState> = _radioConfigState

    fun setPreserveFavorites(preserveFavorites: Boolean) {
        _radioConfigState.update { it.copy(nodeDbResetPreserveFavorites = preserveFavorites) }
    }

    private val _currentDeviceProfile = MutableStateFlow(DeviceProfile())
    val currentDeviceProfile
        get() = _currentDeviceProfile.value

    open suspend fun getCurrentLocation(): org.meshtastic.core.repository.Location? =
        locationService.getCurrentLocation()

    init {
        nodeRepository.nodeDBbyNum
            .map { nodes -> if (destNum != null) nodes[destNum] else nodes.values.firstOrNull() }
            .distinctUntilChanged()
            .onEach {
                _destNode.value = it
                _radioConfigState.update { state ->
                    state.copy(metadata = it?.metadata, localIsLicensed = it?.user?.is_licensed == true)
                }
            }
            .launchIn(viewModelScope)

        radioConfigRepository.deviceProfileFlow.onEach { _currentDeviceProfile.value = it }.launchIn(viewModelScope)

        // Derive isLocal from the immutable destNum and the (possibly changing) myNodeInfo.
        // flatMapLatest cancels the previous inner flow on every change, so there is
        // no window where stale local config can leak through.
        nodeRepository.myNodeInfo
            .map { ni ->
                val isLocal = (destNum == null) || (destNum == ni?.myNodeNum)
                isLocal to if (isLocal) ni?.pioEnv else null
            }
            .distinctUntilChanged()
            .flatMapLatest { (isLocal, pioEnv) ->
                if (isLocal) {
                    combine(
                        radioConfigRepository.channelSetFlow,
                        radioConfigRepository.localConfigFlow,
                        radioConfigRepository.moduleConfigFlow,
                    ) { cs, lc, mc ->
                        _radioConfigState.update {
                            it.copy(
                                isLocal = true,
                                pioEnv = pioEnv,
                                channelList = cs.settings,
                                radioConfig = lc,
                                moduleConfig = mc,
                            )
                        }
                    }
                } else {
                    // Remote admin: clear local config once, then stay idle.
                    // Remote responses arrive via processPacketResponse.
                    flowOf(Unit).onEach {
                        _radioConfigState.update {
                            it.copy(
                                isLocal = false,
                                pioEnv = null,
                                channelList = emptyList(),
                                radioConfig = LocalConfig(),
                                moduleConfig = LocalModuleConfig(),
                            )
                        }
                    }
                }
            }
            .launchIn(viewModelScope)

        radioConfigRepository.deviceUIConfigFlow
            .onEach { uiConfig -> _radioConfigState.update { it.copy(deviceUIConfig = uiConfig) } }
            .launchIn(viewModelScope)

        radioConfigRepository.fileManifestFlow
            .onEach { manifest -> _radioConfigState.update { it.copy(fileManifest = manifest) } }
            .launchIn(viewModelScope)

        radioConfigRepository.loraRegionPresetMapFlow
            .onEach { map -> _radioConfigState.update { it.copy(loraRegionPresetMap = map) } }
            .launchIn(viewModelScope)

        serviceRepository.meshPacketFlow.onEach(::processPacketResponse).launchIn(viewModelScope)

        combine(serviceRepository.connectionState, radioConfigState) { connState, _ ->
            _radioConfigState.update { it.copy(connected = connState == ConnectionState.Connected) }
        }
            .launchIn(viewModelScope)

        // A reboot-applying save can't survive the reboot it triggers: the node drops the transport before the
        // routing ACK arrives, so the save dialog would otherwise sit at 0% until the 30s request timeout and then
        // surface a spurious "Timeout" error. The disconnect during an expected-restart window IS the confirmation
        // the save was persisted (firmware only reboots after saveChanges writes to disk), so resolve the pending
        // save to the restarting-success the moment the transport drops.
        serviceRepository.connectionState
            .map { it == ConnectionState.Connected }
            .distinctUntilChanged()
            .onEach { connected -> if (!connected) completeRestartingSaveIfPending() }
            .launchIn(viewModelScope)

        Logger.d { "RadioConfigViewModel created" }
    }

    private val myNodeInfo: StateFlow<MyNodeInfo?>
        get() = nodeRepository.myNodeInfo

    val myNodeNum
        get() = myNodeInfo.value?.myNodeNum

    /**
     * Opens the expected-restart window when a save/action is about to make the LOCALLY CONNECTED node reboot (firmware
     * applies most config sections with a reboot a few seconds after the ack). A remote node's reboot doesn't drop our
     * transport, so remote destinations never open the window. Called at send time because module saves disable
     * Bluetooth on the device immediately — before the save is even acked.
     */
    private fun expectRestartIfLocal(behavior: RebootBehavior) {
        if (behavior == RebootBehavior.NEVER) return
        val dest = destNum ?: destNode.value?.num ?: return
        if (dest == myNodeNum) nodeRestartTracker.expectRestart()
    }

    val maxChannels
        get() = myNodeInfo.value?.maxChannels ?: 8

    val hasPaFan: Boolean
        get() =
            destNode.value?.user?.hw_model in
                setOf(
                    null,
                    HardwareModel.UNSET,
                    HardwareModel.BETAFPV_2400_TX,
                    HardwareModel.RADIOMASTER_900_BANDIT_NANO,
                    HardwareModel.RADIOMASTER_900_BANDIT,
                )

    override fun onCleared() {
        super.onCleared()
        Logger.d { "RadioConfigViewModel cleared" }
    }

    /**
     * Routes the User config save: ham onboarding (`set_ham_mode`) when the licensed toggle transitions OFF→ON on the
     * locally connected node, [setOwner] otherwise. Routing on the transition — not the toggle state — keeps subsequent
     * saves of an already-licensed node on the `set_owner` path, so edits to other owner fields still reach the device
     * and the node doesn't reboot on every save (firmware reboots on `set_ham_mode`). The local-node guard is the
     * backstop for the UI gate — `set_ham_mode` must never be sent to a remote node.
     */
    fun saveUserConfig(user: User) {
        val destNum = destNum ?: destNode.value?.num ?: return
        val enablingHam = user.is_licensed && !radioConfigState.value.userConfig.is_licensed
        if (enablingHam && destNum == myNodeNum) setHamMode(destNum, user) else setOwner(user)
    }

    private fun setHamMode(destNum: Int, user: User) {
        safeLaunch(tag = "setHamMode") {
            _radioConfigState.update { it.copy(userConfig = user) }
            // The form's long-name field carries the callsign while licensed (iOS parity).
            // When meshtastic/protobufs#941 ships, add long_name here.
            expectRestartIfLocal(RebootBehavior.ALWAYS)
            radioConfigUseCase.setHamMode(
                destNum,
                HamParameters(call_sign = user.long_name, short_name = user.short_name),
                onRequestId = ::registerWriteRequestId,
            )
        }
    }

    /**
     * Sends a plain `set_owner` with [user]. Prefer [saveUserConfig] for User config screen saves — it routes ham
     * onboarding to `set_ham_mode` when the licensed toggle is first enabled; calling this directly bypasses that.
     */
    fun setOwner(user: User) {
        val destNum = destNum ?: destNode.value?.num ?: return
        safeLaunch(tag = "setOwner") {
            _radioConfigState.update { it.copy(userConfig = user) }
            radioConfigUseCase.setOwner(destNum, user, onRequestId = ::registerWriteRequestId)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    fun updateChannels(new: List<ChannelSettings>, old: List<ChannelSettings>) {
        val destNum = destNum ?: destNode.value?.num ?: return

        safeLaunch(tag = "setRemoteChannels") {
            val batchJob = checkNotNull(currentCoroutineContext()[Job])
            synchronized(manualChannelBatchJobsLock) { manualChannelBatchJobs += batchJob }
            try {
                // Manual channel saves are an ordered batch: only update canonical local state after every write
                // request is enqueued. Serialize batches so two ordered write plans cannot interleave on the radio
                // link, and diff each queued save against the canonical list at the moment it starts.
                channelUpdateMutex.withLock {
                    val current = radioConfigState.value.channelList.ifEmpty { old }
                    val updatePlan = getManualChannelUpdatePlan(new, current)
                    if (updatePlan.isEmpty()) return@withLock
                    if (!beginManualChannelBatch(updatePlan.size)) return@withLock
                    val batchRequestIds = mutableSetOf<Int>()

                    try {
                        applyManualChannelUpdatePlan(
                            updatePlan = updatePlan,
                            currentSettings = current,
                            finalSettings = new,
                            writeChannel = { channel, onRequestId ->
                                currentCoroutineContext().ensureActive()
                                radioConfigUseCase.setRemoteChannel(destNum, channel, onRequestId)
                            },
                            registerRequestId = { packetId ->
                                batchRequestIds.add(packetId)
                                registerManualChannelBatchRequestId(packetId)
                            },
                            onInterrupted = { result ->
                                reconcileInterruptedManualChannelUpdate(
                                    destNum = destNum,
                                    oldSettings = current,
                                    appliedSettings = result.appliedSettings,
                                )
                            },
                        )
                        currentCoroutineContext().ensureActive()
                        commitManualChannelSettings(destNum = destNum, oldSettings = current, newSettings = new)
                        finishManualChannelBatch()
                    } catch (e: CancellationException) {
                        abortManualChannelBatch(batchRequestIds)
                        throw e
                    } catch (e: Throwable) {
                        abortManualChannelBatch(batchRequestIds)
                        if (e !is Exception) throw e
                        Logger.w(e) { "Manual channel update failed after enqueue" }
                        e.message?.let(::sendError) ?: sendError(Res.string.unknown_error)
                    }
                }
            } finally {
                synchronized(manualChannelBatchJobsLock) { manualChannelBatchJobs -= batchJob }
            }
        }
    }

    private fun getManualChannelUpdatePlan(new: List<ChannelSettings>, old: List<ChannelSettings>): List<Channel> =
        getChannelList(new, old).sortedBy { it.index }

    private suspend fun commitManualChannelSettings(
        destNum: Int,
        oldSettings: List<ChannelSettings>,
        newSettings: List<ChannelSettings>,
    ) {
        withContext(NonCancellable) {
            if (destNum == myNodeNum) {
                packetRepository.migrateChannelsByPSK(oldSettings, newSettings)
                radioConfigRepository.replaceAllSettings(newSettings)
            }
            _radioConfigState.update { it.copy(channelList = newSettings) }
        }
    }

    private suspend fun reconcileInterruptedManualChannelUpdate(
        destNum: Int,
        oldSettings: List<ChannelSettings>,
        appliedSettings: List<ChannelSettings>,
    ) {
        withContext(NonCancellable) {
            Logger.w { "Reconciling interrupted manual channel update appliedSettings=${appliedSettings.size}" }
            if (destNum == myNodeNum) {
                packetRepository.migrateChannelsByPSK(oldSettings, appliedSettings)
                radioConfigRepository.replaceAllSettings(appliedSettings)
            }
            _radioConfigState.update { it.copy(channelList = appliedSettings) }
        }
    }

    fun setConfig(config: Config) {
        val destNum = destNum ?: destNode.value?.num ?: return
        safeLaunch(tag = "setConfig") {
            _radioConfigState.update { state ->
                state.copy(
                    radioConfig =
                    state.radioConfig.copy(
                        device = config.device ?: state.radioConfig.device,
                        position = config.position ?: state.radioConfig.position,
                        power = config.power ?: state.radioConfig.power,
                        network = config.network ?: state.radioConfig.network,
                        display = config.display ?: state.radioConfig.display,
                        // LoRa is intentionally NOT applied optimistically: the firmware can clamp or region-swap
                        // (e.g. EU sibling) a LoRa write and applies it live, so the form must reflect the device's
                        // actual value. It is re-read from the device when the LoRa screen is next opened.
                        bluetooth = config.bluetooth ?: state.radioConfig.bluetooth,
                        security = config.security ?: state.radioConfig.security,
                    ),
                )
            }
            expectRestartIfLocal(config.saveRebootBehavior())
            radioConfigUseCase.setConfig(destNum, config, onRequestId = ::registerWriteRequestId)
        }
    }

    @Suppress("CyclomaticComplexMethod")
    fun setModuleConfig(config: ModuleConfig) {
        val destNum = destNum ?: destNode.value?.num ?: return
        safeLaunch(tag = "setModuleConfig") {
            _radioConfigState.update { state ->
                state.copy(
                    moduleConfig =
                    state.moduleConfig.copy(
                        mqtt = config.mqtt ?: state.moduleConfig.mqtt,
                        serial = config.serial ?: state.moduleConfig.serial,
                        external_notification =
                        config.external_notification ?: state.moduleConfig.external_notification,
                        store_forward = config.store_forward ?: state.moduleConfig.store_forward,
                        range_test = config.range_test ?: state.moduleConfig.range_test,
                        telemetry = config.telemetry ?: state.moduleConfig.telemetry,
                        canned_message = config.canned_message ?: state.moduleConfig.canned_message,
                        audio = config.audio ?: state.moduleConfig.audio,
                        remote_hardware = config.remote_hardware ?: state.moduleConfig.remote_hardware,
                        neighbor_info = config.neighbor_info ?: state.moduleConfig.neighbor_info,
                        ambient_lighting = config.ambient_lighting ?: state.moduleConfig.ambient_lighting,
                        detection_sensor = config.detection_sensor ?: state.moduleConfig.detection_sensor,
                        paxcounter = config.paxcounter ?: state.moduleConfig.paxcounter,
                        statusmessage = config.statusmessage ?: state.moduleConfig.statusmessage,
                        tak = config.tak ?: state.moduleConfig.tak,
                    ),
                )
            }
            expectRestartIfLocal(config.saveRebootBehavior())
            radioConfigUseCase.setModuleConfig(destNum, config, onRequestId = ::registerWriteRequestId)
        }
    }

    fun setRingtone(ringtone: String) {
        val destNum = destNum ?: destNode.value?.num ?: return
        retireReadRequestsForRoute(radioConfigState.value.route)
        _radioConfigState.update { it.copy(ringtone = ringtone) }
        safeLaunch(tag = "setRingtone") { radioConfigUseCase.setRingtone(destNum, ringtone) }
    }

    fun setCannedMessages(messages: String) {
        val destNum = destNum ?: destNode.value?.num ?: return
        retireReadRequestsForRoute(radioConfigState.value.route)
        _radioConfigState.update { it.copy(cannedMessageMessages = messages) }
        safeLaunch(tag = "setCannedMessages") { radioConfigUseCase.setCannedMessages(destNum, messages) }
    }

    private fun sendAdminRequest(destNum: Int) {
        val route = radioConfigState.value.route
        val isLocal = radioConfigState.value.isLocal
        _radioConfigState.update { it.copy(route = "") } // setter (response is PortNum.ROUTING_APP)

        val trackAdminAction = {
            analytics.trackAction("admin_action", mapOf("route" to route.lowercase(), "is_remote" to !isLocal))
        }

        val preserveFavorites = radioConfigState.value.nodeDbResetPreserveFavorites

        when (route) {
            AdminRoute.SET_TIME.name ->
                safeLaunch(tag = "setTime") {
                    adminActionsUseCase.setTime(destNum, onRequestId = ::registerRequestId)
                    trackAdminAction()
                }

            AdminRoute.REBOOT.name ->
                safeLaunch(tag = "reboot") {
                    expectRestartIfLocal(RebootBehavior.ALWAYS)
                    adminActionsUseCase.reboot(destNum, onRequestId = ::registerRequestId)
                    trackAdminAction()
                }

            AdminRoute.SHUTDOWN.name ->
                with(radioConfigState.value) {
                    if (metadata?.canShutdown != true) {
                        sendError(Res.string.cant_shutdown)
                    } else {
                        safeLaunch(tag = "shutdown") {
                            adminActionsUseCase.shutdown(destNum, onRequestId = ::registerRequestId)
                            trackAdminAction()
                        }
                    }
                }

            AdminRoute.FACTORY_RESET.name ->
                safeLaunch(tag = "factoryReset") {
                    val isLocal = (destNum == myNodeNum)
                    if (isLocal) nodeRestartTracker.expectRestart()
                    adminActionsUseCase.factoryReset(destNum, isLocal, onRequestId = ::registerRequestId)
                    trackAdminAction()
                }

            AdminRoute.NODEDB_RESET.name ->
                safeLaunch(tag = "nodedbReset") {
                    val isLocal = (destNum == myNodeNum)
                    // Firmware reboots after a nodedb reset, so a local reset drops the transport just like a
                    // factory reset — mark it expected so the UI shows "restarting" instead of a surprise disconnect.
                    if (isLocal) nodeRestartTracker.expectRestart()
                    adminActionsUseCase.nodedbReset(destNum, preserveFavorites, isLocal, ::registerRequestId)
                    trackAdminAction()
                }
        }
    }

    fun setFixedPosition(position: Position) {
        val destNum = destNum ?: destNode.value?.num ?: return
        safeLaunch(tag = "setFixedPosition") { radioConfigUseCase.setFixedPosition(destNum, position) }
    }

    fun removeFixedPosition() {
        val destNum = destNum ?: destNode.value?.num ?: return
        safeLaunch(tag = "removeFixedPosition") { radioConfigUseCase.removeFixedPosition(destNum) }
    }

    fun importProfile(uri: CommonUri, onResult: (DeviceProfile) -> Unit) {
        safeLaunch(tag = "importProfile") {
            var profile: DeviceProfile? = null
            fileService.read(uri) { source ->
                importProfileUseCase(source).onSuccess { profile = it }.onFailure { throw it }
            }
            profile?.let { onResult(it) }
        }
    }

    fun exportProfile(uri: CommonUri, profile: DeviceProfile) {
        safeLaunch(tag = "exportProfile") {
            fileService.write(uri) { sink ->
                exportProfileUseCase(sink, profile).onSuccess { /* Success */ }.onFailure { throw it }
            }
        }
    }

    /** Whether an encrypted key backup exists for the node currently being configured. */
    fun securityKeyBackupExists(): Boolean {
        val nodeNum = destNum ?: destNode.value?.num ?: return false
        return securityKeyBackupStore.get(nodeNum) != null
    }

    /** Saves the node's current public/private keys to OS-backed encrypted storage, keyed by node number. */
    fun backupSecurityKeys(securityConfig: Config.SecurityConfig, onComplete: () -> Unit = {}) {
        val nodeNum = destNum ?: destNode.value?.num ?: return
        safeLaunch(tag = "backupSecurityKeys") {
            // Guard against the empty SecurityConfig() fallback: backing up blanks and later restoring them would
            // overwrite the device's real keys.
            if (securityConfig.public_key.size == 0 || securityConfig.private_key.size == 0) {
                snackbarManager.showSnackbar(message = UiText.Resource(Res.string.key_backup_restore_failed).resolve())
                return@safeLaunch
            }
            securityKeyBackupStore.save(
                nodeNum = nodeNum,
                publicKeyBase64 = securityConfig.public_key.base64(),
                privateKeyBase64 = securityConfig.private_key.base64(),
                timestamp = nowMillis,
            )
            snackbarManager.showSnackbar(message = UiText.Resource(Res.string.key_backup_saved).resolve())
            onComplete()
        }
    }

    /** Restores the previously backed-up keys for this node and pushes them to the device via admin config. */
    fun restoreSecurityKeys() {
        val nodeNum = destNum ?: destNode.value?.num ?: return
        safeLaunch(tag = "restoreSecurityKeys") {
            val stored = securityKeyBackupStore.get(nodeNum)
            if (stored == null) {
                snackbarManager.showSnackbar(message = UiText.Resource(Res.string.key_backup_not_found).resolve())
                return@safeLaunch
            }
            importSecurityConfigUseCase(stored)
                .onSuccess {
                    setConfig(Config(security = it))
                    snackbarManager.showSnackbar(message = UiText.Resource(Res.string.key_backup_restored).resolve())
                }
                .onFailure {
                    snackbarManager.showSnackbar(
                        message = UiText.Resource(Res.string.key_backup_restore_failed).resolve(),
                    )
                }
        }
    }

    /** Deletes the encrypted key backup for this node, if any. */
    fun deleteSecurityKeyBackup(onComplete: () -> Unit = {}) {
        val nodeNum = destNum ?: destNode.value?.num ?: return
        safeLaunch(tag = "deleteSecurityKeyBackup") {
            securityKeyBackupStore.delete(nodeNum)
            snackbarManager.showSnackbar(message = UiText.Resource(Res.string.key_backup_deleted).resolve())
            onComplete()
        }
    }

    fun installProfile(protobuf: DeviceProfile) {
        val destNum = destNum ?: destNode.value?.num ?: return
        val state = radioConfigState.value
        val isLocal = this.destNum == null || destNum == myNodeNum
        safeLaunch(tag = "installProfile") {
            try {
                installProfileUseCase(
                    destNum = destNum,
                    profile = protobuf,
                    currentUser = destNode.value?.user,
                    currentLoraConfig = state.radioConfig.lora,
                    isLocal = isLocal,
                )
            } catch (_: MalformedMeshtasticUrlException) {
                Logger.w { "[installProfile] Rejected invalid profile channel URL" }
                snackbarManager.showSnackbar(message = UiText.Resource(Res.string.channel_invalid).resolve())
            }
        }
    }

    fun clearPacketResponse() {
        clearRequestIds()
        _radioConfigState.update { it.copy(responseState = ResponseState.Empty) }
    }

    /**
     * Sets the initial loading state for remote config sub-screens. Must be called before the first
     * `collectAsStateWithLifecycle` read so the LoadingOverlay is visible from the very first composition frame.
     */
    fun ensureLoadingForRemote() {
        val state = _radioConfigState.value
        if (destNum != null && state.responseState is ResponseState.Empty) {
            _radioConfigState.update { it.copy(responseState = ResponseState.Loading()) }
        }
    }

    /** Refreshes a config route while keeping the connect-time local snapshot visible. */
    fun loadConfigRoute(route: Enum<*>) {
        setResponseStateLoading(route = route, showOverlay = destNum != null)
    }

    fun setResponseStateLoading(route: Enum<*>) {
        setResponseStateLoading(route = route, showOverlay = true)
    }

    private fun setResponseStateLoading(route: Enum<*>, showOverlay: Boolean) {
        val destNum = destNum ?: destNode.value?.num ?: return

        // A module without a per-module get (no ModuleConfigType, e.g. MeshBeacon) reads from the connect-time config
        // sync — just select the route and render, skipping the loading/request round-trip that would never complete.
        if (route is ModuleRoute && !route.refreshable) {
            _radioConfigState.update { it.copy(route = route.name, responseState = ResponseState.Empty) }
            return
        }

        _radioConfigState.update {
            it.copy(route = route.name, responseState = ResponseState.Loading(showOverlay = showOverlay))
        }

        when (route) {
            ConfigRoute.USER ->
                safeLaunch(tag = "getOwner") {
                    radioConfigUseCase.getOwner(destNum, onRequestId = ::registerReadRequestId)
                }

            ConfigRoute.CHANNELS -> {
                safeLaunch(tag = "getChannel0") {
                    radioConfigUseCase.getChannel(destNum, 0, onRequestId = ::registerReadRequestId)
                }
                safeLaunch(tag = "getLoraConfig") {
                    radioConfigUseCase.getConfig(
                        destNum,
                        AdminMessage.ConfigType.LORA_CONFIG.value,
                        onRequestId = ::registerReadRequestId,
                    )
                }
                // channel editor is synchronous, so we don't use requestIds as total
                setResponseStateTotal(maxChannels + 1)
            }

            is AdminRoute -> {
                safeLaunch(tag = "getSessionKeyConfig") {
                    radioConfigUseCase.getConfig(
                        destNum,
                        AdminMessage.ConfigType.SESSIONKEY_CONFIG.value,
                        onRequestId = ::registerReadRequestId,
                    )
                }
                setResponseStateTotal(2)
            }

            is ConfigRoute -> loadConfigRoute(destNum, route)

            is ModuleRoute -> loadModuleRoute(destNum, route)
        }
    }

    private fun loadConfigRoute(destNum: Int, route: ConfigRoute) {
        if (route == ConfigRoute.LORA) {
            safeLaunch(tag = "getChannel0ForLora") {
                radioConfigUseCase.getChannel(destNum, 0, onRequestId = ::registerReadRequestId)
            }
        }
        if (route == ConfigRoute.NETWORK) {
            safeLaunch(tag = "getConnectionStatus") {
                radioConfigUseCase.getDeviceConnectionStatus(destNum, onRequestId = ::registerReadRequestId)
            }
        }
        safeLaunch(tag = "getConfig") {
            radioConfigUseCase.getConfig(destNum, route.type, onRequestId = ::registerReadRequestId)
        }
    }

    private fun loadModuleRoute(destNum: Int, route: ModuleRoute) {
        if (route == ModuleRoute.CANNED_MESSAGE) {
            safeLaunch(tag = "getCannedMessages") {
                radioConfigUseCase.getCannedMessages(destNum, onRequestId = ::registerReadRequestId)
            }
        }
        if (route == ModuleRoute.EXT_NOTIFICATION) {
            safeLaunch(tag = "getRingtone") {
                radioConfigUseCase.getRingtone(destNum, onRequestId = ::registerReadRequestId)
            }
        }
        safeLaunch(tag = "getModuleConfig") {
            radioConfigUseCase.getModuleConfig(destNum, route.type, onRequestId = ::registerReadRequestId)
        }
    }

    fun shouldReportLocation(nodeNum: Int?) = mapConsentPrefs.shouldReportLocation(nodeNum)

    fun setShouldReportLocation(nodeNum: Int?, shouldReportLocation: Boolean) {
        mapConsentPrefs.setShouldReportLocation(nodeNum, shouldReportLocation)
    }

    private fun setResponseStateTotal(total: Int) {
        _radioConfigState.update { state ->
            if (state.responseState is ResponseState.Loading) {
                state.copy(responseState = state.responseState.copy(total = total))
            } else {
                state // Return the unchanged state for other response states
            }
        }
    }

    private fun beginManualChannelBatch(total: Int): Boolean {
        if (hasUnrelatedPendingRequest() || hasPendingRequestRegistration()) {
            Logger.w { "Manual channel update skipped while another radio request is pending" }
            return false
        }
        manualChannelBatchEnqueueing = true
        _radioConfigState.update { state ->
            state.copy(route = "", responseState = ResponseState.Loading(total = total))
        }
        return true
    }

    private fun finishManualChannelBatch() {
        manualChannelBatchEnqueueing = false
        if (requestIds.value.isEmpty()) {
            setResponseStateSuccess()
        }
    }

    private fun abortManualChannelBatch(batchRequestIds: Set<Int>) {
        manualChannelBatchEnqueueing = false
        removeRequestIds(batchRequestIds)
    }

    private fun invalidateManualChannelBatch() {
        manualChannelBatchEnqueueing = false
        val jobs = synchronized(manualChannelBatchJobsLock) { manualChannelBatchJobs.toList() }
        jobs.forEach { it.cancel() }
    }

    /**
     * True while a manual channel batch is in flight — from enqueue through the ack-wait that outlives
     * [finishManualChannelBatch]. [manualChannelBatchEnqueueing] alone only covers the enqueue phase, so pending batch
     * request ids are the authoritative signal that a batch (not a reboot-applying save) owns the current Loading
     * state.
     */
    private fun manualChannelBatchInFlight(): Boolean =
        manualChannelBatchEnqueueing || manualChannelBatchRequestIds.isNotEmpty()

    private fun completeSetRequestOrProgressBatch() {
        if (manualChannelBatchEnqueueing) {
            incrementCompleted()
        } else {
            setResponseStateSuccess()
        }
    }

    /**
     * Resolves a pending SAVE response to success ("node is restarting") when a restart is expected. Called on the
     * transport-drop edge (the reboot) and as the request-timeout backstop. Scoped to saves (route is empty; gets keep
     * their route set) and gated on [NodeRestartTracker.restartExpected] so a genuine unexpected disconnect still
     * surfaces normally. Clears request ids first so the pending 30s timeout can't later flip success back to error.
     */
    private fun completeRestartingSaveIfPending() {
        if (!nodeRestartTracker.restartExpected.value) return
        // A manual channel batch shares the save shape (empty route + Loading) but never reboots the node; a stale
        // restart window must not flip an in-flight batch to success. The batch stays in flight through its ack-wait,
        // past finishManualChannelBatch, so key off its pending request ids — not just the enqueue flag.
        if (manualChannelBatchInFlight()) return
        val state = radioConfigState.value
        if (state.responseState is ResponseState.Loading && state.route.isEmpty()) {
            clearRequestIds()
            setResponseStateSuccess()
        }
    }

    protected fun setResponseStateSuccess() {
        _radioConfigState.update { state ->
            if (state.responseState is ResponseState.Loading) {
                state.copy(responseState = ResponseState.Success(true))
            } else {
                state // Return the unchanged state for other response states
            }
        }
    }

    protected fun sendError(error: String) = setResponseStateError(UiText.DynamicString(error))

    protected fun sendError(id: StringResource) = setResponseStateError(UiText.Resource(id))

    protected fun sendError(error: UiText) = setResponseStateError(error)

    private fun setResponseStateError(error: UiText) {
        _radioConfigState.update { it.copy(responseState = ResponseState.Error(error)) }
    }

    private fun incrementCompleted() {
        _radioConfigState.update { state ->
            if (state.responseState is ResponseState.Loading) {
                val increment = state.responseState.completed + 1
                state.copy(responseState = state.responseState.copy(completed = increment))
            } else {
                state // Return the unchanged state for other response states
            }
        }
    }

    private fun registerRequestId(packetId: Int) {
        requestTimeoutJobs.remove(packetId)?.cancel()
        removeLateRemoteRead(packetId)
        readRequestRoutes.remove(packetId)
        deferredRemoteReadErrors.remove(packetId)
        requestIds.update { it.withPacketId(packetId) }
        _radioConfigState.update { state ->
            if (state.responseState is ResponseState.Loading) {
                val total = maxOf(requestIds.value.size, state.responseState.total)
                state.copy(responseState = state.responseState.copy(total = total))
            } else {
                state.copy(
                    route = "", // setter (response is PortNum.ROUTING_APP)
                    responseState = ResponseState.Loading(),
                )
            }
        }

        val requestTimeout = 30.seconds
        requestTimeoutJobs[packetId] =
            safeLaunch(tag = "requestTimeout") {
                delay(requestTimeout)
                if (requestIds.value.contains(packetId)) {
                    // Capture batch membership before removeRequestId drops the last id and empties the batch set.
                    val timedOutBatchRequest = packetId in manualChannelBatchRequestIds
                    val requestRoute = readRequestRoutes[packetId].orEmpty()
                    val deferredRemoteReadError = deferredRemoteReadErrors[packetId]
                    removeRequestId(packetId)
                    if (isSingleResponseRemoteReadRoute(requestRoute)) {
                        retainLateRemoteRead(packetId, requestRoute)
                    }
                    if (!hasPendingRequestsForRoute(requestRoute) && radioConfigState.value.route == requestRoute) {
                        // A save that reboots the node races the reboot against its ACK; a timeout here during an
                        // expected restart means the reboot won — treat it as the restarting-success, not an error.
                        // A manual channel batch never reboots, so exclude it even inside a stale restart window.
                        if (
                            nodeRestartTracker.restartExpected.value &&
                            !timedOutBatchRequest &&
                            !manualChannelBatchInFlight() &&
                            radioConfigState.value.route.isEmpty()
                        ) {
                            setResponseStateSuccess()
                        } else {
                            deferredRemoteReadError?.let(::sendError) ?: sendError(Res.string.timeout)
                        }
                    }
                }
            }
    }

    private fun registerReadRequestId(packetId: Int) {
        val route = radioConfigState.value.route
        registerRequestId(packetId)
        readRequestRoutes[packetId] = route
    }

    private fun registerWriteRequestId(packetId: Int) {
        val writtenRoute = radioConfigState.value.route
        retireReadRequestsForRoute(writtenRoute)
        registerRequestId(packetId)
        // A write owns the visible request flow even if a timed-out read retry is still tracked in the background.
        _radioConfigState.update { it.copy(route = "", responseState = ResponseState.Loading()) }
    }

    private fun registerManualChannelBatchRequestId(packetId: Int) {
        manualChannelBatchRequestIds.add(packetId)
        registerRequestId(packetId)
    }

    private fun hasUnrelatedPendingRequest(): Boolean = requestIds.value.any { it !in manualChannelBatchRequestIds }

    private fun hasPendingRequestsForRoute(route: String): Boolean = if (route.isEmpty()) {
        requestIds.value.any { it !in readRequestRoutes }
    } else {
        readRequestRoutes.any { (packetId, requestRoute) -> packetId in requestIds.value && requestRoute == route }
    }

    private fun hasPendingRequestRegistration(): Boolean = requestIds.value.isEmpty() &&
        manualChannelBatchRequestIds.isEmpty() &&
        radioConfigState.value.responseState is ResponseState.Loading

    private fun clearRequestIds() {
        requestTimeoutJobs.values.forEach { it.cancel() }
        requestTimeoutJobs.clear()
        readRequestRoutes.clear()
        deferredRemoteReadErrors.clear()
        lateRemoteReads.values.forEach { it.expiryJob.cancel() }
        lateRemoteReads.clear()
        manualChannelBatchRequestIds.clear()
        requestIds.value = hashSetOf()
    }

    private fun removeRequestId(packetId: Int) {
        requestTimeoutJobs.remove(packetId)?.cancel()
        readRequestRoutes.remove(packetId)
        deferredRemoteReadErrors.remove(packetId)
        manualChannelBatchRequestIds.remove(packetId)
        requestIds.update { it.withoutPacketId(packetId) }
    }

    private fun removeRequestIds(packetIds: Set<Int>) {
        packetIds.forEach { requestTimeoutJobs.remove(it)?.cancel() }
        packetIds.forEach {
            readRequestRoutes.remove(it)
            deferredRemoteReadErrors.remove(it)
            removeLateRemoteRead(it)
        }
        manualChannelBatchRequestIds.removeAll(packetIds)
        requestIds.update { ids -> ids.withoutPacketIds(packetIds) }
    }

    private fun retireReadRequestsForRoute(route: String) {
        if (route.isEmpty()) return
        val redundantReadIds = readRequestRoutes.filterValues { it == route }.keys.toSet()
        val retainedReadIds = lateRemoteReads.filterValues { it.route == route }.keys.toList()
        removeRequestIds(redundantReadIds)
        retainedReadIds.forEach(::removeLateRemoteRead)
        if (requestIds.value.isEmpty()) {
            _radioConfigState.update { state ->
                val resolvedLateRead =
                    state.responseState is ResponseState.Loading || state.responseState is ResponseState.Error
                if (state.route == route && resolvedLateRead) {
                    state.copy(responseState = ResponseState.Empty)
                } else {
                    state
                }
            }
        }
    }

    private fun processPacketResponse(packet: MeshPacket) {
        val destNum = destNum ?: destNode.value?.num ?: return
        val requestId = packet.decoded?.request_id
        val lateRemoteReadRoute = requestId?.let { lateRemoteReads[it]?.route }
        val pendingRequestIds = requestIds.value + lateRemoteReads.keys
        val result = processRadioResponseUseCase(packet, destNum, pendingRequestIds) ?: return
        val route = radioConfigState.value.route
        val isLateRemoteRead = requestId != null && lateRemoteReadRoute != null

        if (isLateRemoteRead) {
            when (result) {
                is RadioResponseResult.Error -> {
                    if (result.routingError != Routing.Error.MAX_RETRANSMIT) {
                        removeLateRemoteRead(checkNotNull(requestId))
                    }
                    return
                }

                // A routing ACK confirms delivery but does not contain the requested settings. Keep the retained read
                // isolated from any newer save and continue waiting for its ADMIN_APP response.
                is RadioResponseResult.Success -> return

                else -> Unit
            }
        }

        when (result) {
            is RadioResponseResult.Error -> {
                if (
                    requestId != null &&
                    result.routingError == Routing.Error.MAX_RETRANSMIT &&
                    isRemoteReadRoute(readRequestRoutes[requestId].orEmpty())
                ) {
                    // A remote reply can arrive after the connected radio exhausts reliable-send ACK tracking. Keep
                    // this read alive until its existing UX deadline; a matching ADMIN_APP response can still satisfy
                    // it, while the deferred routing error remains the most specific failure if no response arrives.
                    deferredRemoteReadErrors[requestId] = result.message
                    return
                }
                val responseReadRoute = requestId?.let(readRequestRoutes::get)
                if (responseReadRoute != null && responseReadRoute != route) {
                    removeRequestId(checkNotNull(requestId))
                    return
                }
                // A routing/admin error is terminal for the current request flow. Drop every outstanding request ID
                // and cancel its timeout so a late timeout cannot overwrite the specific failure or block the next
                // retry.
                invalidateManualChannelBatch()
                clearRequestIds()
                sendError(result.message)
                // Abort the AdminRoute flow — do not fire the destructive action
                // (reboot/shutdown/factory_reset) if the metadata preflight failed.
                return
            }

            is RadioResponseResult.Success -> {
                if (requestId != null && !isLateRemoteRead && route.isEmpty() && requestId !in readRequestRoutes) {
                    removeRequestId(requestId)
                    if (!hasPendingRequestsForRoute(route)) {
                        completeSetRequestOrProgressBatch()
                    } else {
                        incrementCompleted()
                    }
                }
            }

            is RadioResponseResult.Metadata -> {
                _radioConfigState.update { it.copy(metadata = result.metadata) }
                if (!isLateRemoteRead) incrementCompleted()
            }

            is RadioResponseResult.ChannelResponse -> {
                val response = result.channel
                // Stop once we get to the first disabled entry
                if (response.role != Channel.Role.DISABLED) {
                    _radioConfigState.update { state ->
                        state.copy(
                            channelList =
                            state.channelList.toMutableList().apply {
                                val index = response.index
                                val settings = response.settings ?: ChannelSettings()
                                // Make sure list is large enough
                                while (size <= index) add(ChannelSettings())
                                set(index, settings)
                            },
                        )
                    }
                    if (!isLateRemoteRead) incrementCompleted()
                    val index = response.index
                    if (!isLateRemoteRead && index + 1 < maxChannels && route == ConfigRoute.CHANNELS.name) {
                        // Not done yet, request next channel
                        safeLaunch(tag = "getNextChannel") {
                            radioConfigUseCase.getChannel(destNum, index + 1, onRequestId = ::registerReadRequestId)
                        }
                    }
                } else if (!isLateRemoteRead) {
                    // Received last channel, update total and start channel editor
                    setResponseStateTotal(response.index + 1)
                }
            }

            is RadioResponseResult.Owner -> {
                _radioConfigState.update { it.copy(userConfig = result.user) }
                if (!isLateRemoteRead) incrementCompleted()
            }

            is RadioResponseResult.ConfigResponse -> {
                val response = result.config
                _radioConfigState.update { state ->
                    state.copy(
                        radioConfig =
                        state.radioConfig.copy(
                            device = response.device ?: state.radioConfig.device,
                            position = response.position ?: state.radioConfig.position,
                            power = response.power ?: state.radioConfig.power,
                            network = response.network ?: state.radioConfig.network,
                            display = response.display ?: state.radioConfig.display,
                            lora = response.lora ?: state.radioConfig.lora,
                            bluetooth = response.bluetooth ?: state.radioConfig.bluetooth,
                            security = response.security ?: state.radioConfig.security,
                        ),
                    )
                }
                if (!isLateRemoteRead) incrementCompleted()
            }

            is RadioResponseResult.ModuleConfigResponse -> {
                val response = result.config
                _radioConfigState.update { state ->
                    state.copy(
                        moduleConfig =
                        state.moduleConfig.copy(
                            mqtt = response.mqtt ?: state.moduleConfig.mqtt,
                            serial = response.serial ?: state.moduleConfig.serial,
                            external_notification =
                            response.external_notification ?: state.moduleConfig.external_notification,
                            store_forward = response.store_forward ?: state.moduleConfig.store_forward,
                            range_test = response.range_test ?: state.moduleConfig.range_test,
                            telemetry = response.telemetry ?: state.moduleConfig.telemetry,
                            canned_message = response.canned_message ?: state.moduleConfig.canned_message,
                            audio = response.audio ?: state.moduleConfig.audio,
                            remote_hardware = response.remote_hardware ?: state.moduleConfig.remote_hardware,
                            neighbor_info = response.neighbor_info ?: state.moduleConfig.neighbor_info,
                            ambient_lighting = response.ambient_lighting ?: state.moduleConfig.ambient_lighting,
                            detection_sensor = response.detection_sensor ?: state.moduleConfig.detection_sensor,
                            paxcounter = response.paxcounter ?: state.moduleConfig.paxcounter,
                            statusmessage = response.statusmessage ?: state.moduleConfig.statusmessage,
                            tak = response.tak ?: state.moduleConfig.tak,
                        ),
                    )
                }
                if (!isLateRemoteRead) incrementCompleted()
            }

            is RadioResponseResult.CannedMessages -> {
                _radioConfigState.update { it.copy(cannedMessageMessages = result.messages) }
                if (!isLateRemoteRead) incrementCompleted()
            }

            is RadioResponseResult.Ringtone -> {
                _radioConfigState.update { it.copy(ringtone = result.ringtone) }
                if (!isLateRemoteRead) incrementCompleted()
            }

            is RadioResponseResult.ConnectionStatus -> {
                _radioConfigState.update { it.copy(deviceConnectionStatus = result.status) }
                if (!isLateRemoteRead) incrementCompleted()
            }
        }

        if (isLateRemoteRead) {
            removeLateRemoteRead(checkNotNull(requestId))
            // A late response satisfies its single-response route, including any retry of that route. Retire only
            // redundant reads with the same route; a concurrently registered save owns no read route and is retained.
            retireReadRequestsForRoute(checkNotNull(lateRemoteReadRoute))
            return
        }

        // Routing ACKs (Success) share the same request_id as the upcoming ADMIN_APP response.
        // Removing the id here would cause the actual admin response to be silently dropped,
        // because processRadioResponseUseCase checks `request_id in requestIds`.
        // The Success branch already handles its own id removal when route is empty (set flow).
        if (result is RadioResponseResult.Success) return

        if (AdminRoute.entries.any { it.name == route }) {
            sendAdminRequest(destNum)
        }

        if (requestId == null) return
        // Defer the removal so a chain continuation launched above (e.g. the next getChannel of a
        // sequential channel fetch) registers its request id first — launches run FIFO on the main
        // dispatcher, and registration is the continuation's first act before its send. Removing inline
        // would observe a momentarily-empty request set in the gap between chained requests and tear the
        // whole flow down (clearPacketResponse), stranding the rest of the chain.
        safeLaunch(tag = "completePacketResponse") {
            removeRequestId(requestId)

            if (requestIds.value.isEmpty()) {
                if (route.isNotEmpty() && !AdminRoute.entries.any { it.name == route }) {
                    clearPacketResponse()
                } else if (route.isEmpty()) {
                    completeSetRequestOrProgressBatch()
                }
            }
        }
    }

    private fun isRemoteReadRoute(route: String): Boolean = destNum != null &&
        destNum != myNodeNum &&
        (ConfigRoute.entries.any { it.name == route } || ModuleRoute.entries.any { it.name == route })

    private fun isSingleResponseRemoteReadRoute(route: String): Boolean {
        if (!isRemoteReadRoute(route)) return false
        val hasReadFanOut =
            ConfigRoute.entries.firstOrNull { it.name == route }?.hasReadFanOut
                ?: ModuleRoute.entries.firstOrNull { it.name == route }?.hasReadFanOut
        // Unknown routes are never retained.
        return hasReadFanOut == false
    }

    private fun retainLateRemoteRead(packetId: Int, route: String) {
        removeLateRemoteRead(packetId)
        val expiryJob =
            safeLaunch(tag = "expireLateRemoteRead") {
                delay(REMOTE_READ_LATE_RESPONSE_GRACE)
                lateRemoteReads.remove(packetId)
            }
        lateRemoteReads[packetId] = LateRemoteRead(route, expiryJob)
    }

    private fun removeLateRemoteRead(packetId: Int) {
        lateRemoteReads.remove(packetId)?.expiryJob?.cancel()
    }
}

private data class LateRemoteRead(val route: String, val expiryJob: Job)

internal data class ManualChannelUpdateResult(val packetIds: List<Int>, val finalSettings: List<ChannelSettings>)

internal data class InterruptedManualChannelUpdate(
    val appliedSettings: List<ChannelSettings>,
    val appliedWriteCount: Int,
)

internal suspend fun applyManualChannelUpdatePlan(
    updatePlan: List<Channel>,
    currentSettings: List<ChannelSettings>,
    finalSettings: List<ChannelSettings>,
    writeChannel: suspend (Channel, onRequestId: (Int) -> Unit) -> Int,
    registerRequestId: (Int) -> Unit,
    onInterrupted: suspend (InterruptedManualChannelUpdate) -> Unit = {},
    writeDelay: Duration = MANUAL_CHANNEL_WRITE_DELAY,
    delayFn: suspend (Duration) -> Unit = { delay(it) },
): ManualChannelUpdateResult {
    val packetIds = mutableListOf<Int>()
    val appliedSettings = currentSettings.toMutableList()
    var appliedWriteCount = 0
    var updateComplete = false
    try {
        for ((index, channel) in updatePlan.withIndex()) {
            // Register before the write is issued: the local loopback response/ACK can arrive
            // before the suspending send returns, so post-hoc registration would drop it.
            writeChannel(channel) { packetId ->
                packetIds.add(packetId)
                registerRequestId(packetId)
            }
            appliedSettings.applyManualChannelWrite(channel)
            appliedWriteCount++
            if (index < updatePlan.lastIndex) {
                delayFn(writeDelay)
            }
        }
        updateComplete = true
    } finally {
        if (!updateComplete && appliedWriteCount > 0) {
            onInterrupted(
                InterruptedManualChannelUpdate(
                    appliedSettings = appliedSettings.toList(),
                    appliedWriteCount = appliedWriteCount,
                ),
            )
        }
    }
    return ManualChannelUpdateResult(packetIds = packetIds, finalSettings = finalSettings)
}

private fun MutableList<ChannelSettings>.applyManualChannelWrite(channel: Channel) {
    while (size <= channel.index) {
        add(ChannelSettings())
    }
    this[channel.index] =
        if (channel.role == Channel.Role.DISABLED) {
            ChannelSettings()
        } else {
            channel.settings ?: ChannelSettings()
        }
}

private fun HashSet<Int>.withPacketId(packetId: Int): HashSet<Int> = HashSet(this).apply { add(packetId) }

private fun HashSet<Int>.withoutPacketId(packetId: Int): HashSet<Int> = HashSet(this).apply { remove(packetId) }

private fun HashSet<Int>.withoutPacketIds(packetIds: Set<Int>): HashSet<Int> =
    HashSet(this).apply { removeAll(packetIds) }

/**
 * Coarse mirror of firmware `AdminModule::handleSetConfig`'s reboot decision for the section being saved. Sections with
 * field-level carve-outs map to [RebootBehavior.MAY_RESTART]; see [RebootBehavior] for why this stays coarse.
 */
internal fun Config.saveRebootBehavior(): RebootBehavior = when {
    position != null || network != null || bluetooth != null || security != null -> RebootBehavior.ALWAYS
    else -> RebootBehavior.MAY_RESTART
}

/** Firmware `AdminModule::handleSetModuleConfig` reboots for every module section except status message. */
internal fun ModuleConfig.saveRebootBehavior(): RebootBehavior =
    if (statusmessage != null) RebootBehavior.NEVER else RebootBehavior.ALWAYS
