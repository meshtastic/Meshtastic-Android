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
import org.meshtastic.core.model.Capabilities
import org.meshtastic.core.model.HamName
import org.meshtastic.core.model.MqttConnectionState
import org.meshtastic.core.model.MqttProbeStatus
import org.meshtastic.core.model.MyNodeInfo
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.Position
import org.meshtastic.core.model.excludes
import org.meshtastic.core.model.getStringResFrom
import org.meshtastic.core.model.util.MalformedMeshtasticUrlException
import org.meshtastic.core.repository.AdminController
import org.meshtastic.core.repository.AnalyticsPrefs
import org.meshtastic.core.repository.DeviceHardwareRepository
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
import org.meshtastic.core.repository.PlatformAnalytics
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.RadioSessionContext
import org.meshtastic.core.repository.SecurityKeyBackupStore
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.repository.routingErrorOrNull
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.UiText
import org.meshtastic.core.resources.cant_shutdown
import org.meshtastic.core.resources.channel_invalid
import org.meshtastic.core.resources.key_backup_deleted
import org.meshtastic.core.resources.key_backup_not_found
import org.meshtastic.core.resources.key_backup_restore_failed
import org.meshtastic.core.resources.key_backup_restored
import org.meshtastic.core.resources.key_backup_saved
import org.meshtastic.core.resources.not_connected
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
import org.meshtastic.proto.ExcludedModules
import org.meshtastic.proto.FileInfo
import org.meshtastic.proto.HamParameters
import org.meshtastic.proto.HardwareModel
import org.meshtastic.proto.LoRaRegionPresetMap
import org.meshtastic.proto.LocalConfig
import org.meshtastic.proto.LocalModuleConfig
import org.meshtastic.proto.ModuleConfig
import org.meshtastic.proto.User
import org.meshtastic.proto.metadata
import org.meshtastic.sdk.AdminResult
import org.meshtastic.sdk.ConnectionState
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

internal val MANUAL_CHANNEL_WRITE_DELAY: Duration = 1.seconds
private val REMOTE_READ_LATE_RESPONSE_GRACE: Duration = 2.minutes

/** One stored key backup offered for restore; [longName] is null when the node DB no longer knows [nodeNum]. */
data class SecurityKeyBackupOption(
    val nodeNum: Int,
    val longName: String?,
    val timestamp: Long,
    val isCurrentNode: Boolean,
)

/** Data class that represents the current RadioConfig state. */
data class RadioConfigState(
    val isLocal: Boolean = false,
    /** PlatformIO target for the configured destination; available only when that destination is directly connected. */
    val pioEnv: String? = null,
    val connected: Boolean = false,
    val route: String = "",
    val metadata: DeviceMetadata? = null,
    val userConfig: User = User.Builder().build(),
    val channelList: List<ChannelSettings> = emptyList(),
    val radioConfig: LocalConfig = LocalConfig.Builder().build(),
    val moduleConfig: LocalModuleConfig = LocalModuleConfig.Builder().build(),
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
    val canRebootToDfu: Boolean = false,
    /** Whether the board's `HardwareModel` says the maker wired its GPS. */
    val isOemFixedGps: Boolean = false,
)

@KoinViewModel
@Suppress("LongParameterList", "LargeClass")
open class RadioConfigViewModel(
    @InjectedParam initialDestNum: Int?,
    private val radioConfigRepository: RadioConfigRepository,
    private val serviceRepository: ServiceRepository,
    private val nodeRepository: NodeRepository,
    private val deviceHardwareRepository: DeviceHardwareRepository,
    private val locationRepository: LocationRepository,
    private val mapConsentPrefs: MapConsentPrefs,
    private val analyticsPrefs: AnalyticsPrefs,
    private val homoglyphEncodingPrefs: HomoglyphPrefs,
    protected val importProfileUseCase: ImportProfileUseCase,
    protected val exportProfileUseCase: ExportProfileUseCase,
    protected val importSecurityConfigUseCase: ImportSecurityConfigUseCase,
    private val installProfileUseCase: InstallProfileUseCase,
    private val adminController: AdminController,
    private val adminActionsUseCase: AdminActionsUseCase,
    private val locationService: LocationService,
    private val fileService: FileService,
    private val mqttManager: MqttManager,
    private val lockdownCoordinator: LockdownCoordinator,
    private val securityKeyBackupStore: SecurityKeyBackupStore,
    private val snackbarManager: SnackbarManager,
    private val nodeRestartTracker: NodeRestartTracker,
    private val analytics: PlatformAnalytics,
) : ViewModel() {

    /**
     * The node this session addresses. Starts as the injected destination (null = the connected node, whatever its
     * number). A session opened on the connected node *by number* — Node detail → Administration — drops to null when
     * that number moves under it (firmware 2.8 renumbers on the first region set), so later writes follow the connected
     * node through [destNode] instead of a number the radio no longer answers to.
     */
    private val activeDestNum = MutableStateFlow(initialDestNum)
    private val destNum: Int?
        get() = activeDestNum.value

    val lockdownTokenInfo = serviceRepository.lockdownTokenInfo
    val sessionAuthorized = serviceRepository.sessionAuthorized
    val lockdownState = serviceRepository.lockdownState

    fun sendLockNow(): Boolean = lockdownCoordinator.lockNow()

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
    ): Boolean = lockdownCoordinator.submitPassphrase(passphrase, boots, hours, maxSessionSeconds, disable)

    val analyticsAllowedFlow = analyticsPrefs.analyticsAllowed

    fun toggleAnalyticsAllowed() {
        analyticsPrefs.toggleAnalyticsAllowed()
    }

    val homoglyphEncodingEnabledFlow = homoglyphEncodingPrefs.homoglyphEncodingEnabled

    fun toggleHomoglyphCharactersEncodingEnabled() {
        homoglyphEncodingPrefs.toggleHomoglyphEncodingEnabled()
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
    private val dfuRequestIds = mutableSetOf<Int>()

    /**
     * Run a one-shot reachability/credentials probe against an MQTT broker. Cancels any in-flight probe before starting
     * a new one. Result is exposed via [mqttProbeStatus].
     */
    fun probeMqttConnection(address: String, tlsEnabled: Boolean, username: String?, password: String?) {
        probeJob?.cancel()
        _mqttProbeStatus.value = MqttProbeStatus.Probing
        probeJob = viewModelScope.launch {
            val result = safeCatching {
                mqttManager.probe(address, tlsEnabled, username, password)
            }
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

    // Request tokens: VM-local keys for one radio request each, standing in for the packet IDs this bookkeeping keyed
    // on before the SDK owned correlation. Main-dispatcher confined like the maps below.
    private val requestIds = MutableStateFlow(hashSetOf<Int>())
    private var lastRequestToken = 0
    private val requestJobs = mutableMapOf<Int, Job>()

    // USER reads one config on firmware without the status message module and two with it, so whether a
    // load fanned out is a property of that load, not of the route. Null falls back to the route's flag.
    private var loadFanOut: Pair<String, Boolean>? = null

    // Main-dispatcher confined with the other ViewModel request state below. Keep every access on viewModelScope unless
    // these collections are moved behind explicit synchronization.
    private val requestTimeoutJobs = mutableMapOf<Int, Job>()

    // Only getter registrations enter this map; writes and destructive actions therefore cannot inherit late-read
    // recovery merely because the user saved from a screen whose route name is still selected.
    private val readRequestRoutes = mutableMapOf<Int, String>()
    private val lateRemoteReads = mutableMapOf<Int, LateRemoteRead>()
    private val _radioConfigState = MutableStateFlow(RadioConfigState())
    val radioConfigState: StateFlow<RadioConfigState> = _radioConfigState

    fun setPreserveFavorites(preserveFavorites: Boolean) {
        _radioConfigState.update { it.copy(nodeDbResetPreserveFavorites = preserveFavorites) }
    }

    private val _currentDeviceProfile = MutableStateFlow(DeviceProfile.Builder().build())
    val currentDeviceProfile
        get() = _currentDeviceProfile.value

    open suspend fun getCurrentLocation(): org.meshtastic.core.repository.Location? =
        locationService.getCurrentLocation()

    init {
        combine(nodeRepository.nodeDBbyNum, activeDestNum) { nodes, dest ->
            if (dest != null) nodes[dest] else nodes.values.firstOrNull()
        }
            .distinctUntilChanged()
            .onEach {
                _destNode.value = it
                _radioConfigState.update { state ->
                    state.copy(
                        metadata = it?.metadata,
                        localIsLicensed = it?.user?.is_licensed == true,
                        isOemFixedGps = it?.user?.hw_model?.metadata?.oem_fixed_gps == true,
                    )
                }
            }
            .launchIn(viewModelScope)

        _destNode
            .map { it?.user?.hw_model?.value }
            .distinctUntilChanged()
            .flatMapLatest { hwModel ->
                if (hwModel == null) {
                    flowOf(null)
                } else {
                    deviceHardwareRepository.observeDeviceHardware(hwModel)
                }
            }
            .onEach { hardware ->
                _radioConfigState.update {
                    it.copy(canRebootToDfu = hardware?.isNrf52Arc == true)
                }
            }
            .launchIn(viewModelScope)

        radioConfigRepository.deviceProfileFlow.onEach { _currentDeviceProfile.value = it }.launchIn(viewModelScope)

        // A session opened on the connected node by number follows it through a renumber: the same device under a
        // new number. Another radio's number is left alone, since that node may still be reachable as a remote.
        var connectedNode: MyNodeInfo? = null
        nodeRepository.myNodeInfo
            .onEach { info ->
                val previous = connectedNode
                if (info != null) connectedNode = info
                val renumbered = previous != null && info != null && previous.isRenumberedTo(info)
                if (renumbered && activeDestNum.value == previous.myNodeNum) activeDestNum.value = null
            }
            .launchIn(viewModelScope)

        // Derive isLocal from the immutable destNum and the (possibly changing) myNodeInfo.
        // flatMapLatest cancels the previous inner flow on every change, so there is
        // no window where stale local config can leak through.
        combine(nodeRepository.myNodeInfo, activeDestNum) { ni, dest ->
            val isLocal = (dest == null) || (dest == ni?.myNodeNum)
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
                    // Remote responses arrive through each read's apply function.
                    flowOf(Unit).onEach {
                        _radioConfigState.update {
                            it.copy(
                                isLocal = false,
                                pioEnv = null,
                                channelList = emptyList(),
                                radioConfig = LocalConfig.Builder().build(),
                                moduleConfig = LocalModuleConfig.Builder().build(),
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

    /** Channel slots on the radio: firmware's `ChannelFile.channels` holds 8. */
    val maxChannels = 8

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
            // While licensed the form's long name is the composed `CALLSIGN//Long name`; firmware rebuilds that same
            // composition from the two HamParameters fields, so send the halves rather than the whole.
            val (callSign, longName) = HamName.split(user.long_name)
            expectRestartIfLocal(RebootBehavior.ALWAYS)
            val hamParameters =
                HamParameters.Builder()
                    .also { wb ->
                        wb.call_sign = callSign
                        wb.short_name = user.short_name
                        wb.long_name = longName
                    }
                    .build()
            launchWrite("setHamMode") { adminController.setHamMode(destNum, hamParameters) }
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
            launchWrite("setOwner") { adminController.setOwner(destNum, user) }
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
                                launchRequest(
                                    "setRemoteChannel",
                                    onRequestId,
                                    { adminController.setRemoteChannel(destNum, channel) },
                                ) { token, _ ->
                                    completeWrite(token)
                                }
                            },
                            registerRequestId = { packetId ->
                                batchRequestIds.add(packetId)
                                registerManualChannelBatchRequestId(packetId)
                            },
                            onInterrupted = { result ->
                                reconcileInterruptedManualChannelUpdate(
                                    destNum = destNum,
                                    appliedSettings = result.appliedSettings,
                                )
                            },
                        )
                        currentCoroutineContext().ensureActive()
                        commitManualChannelSettings(destNum = destNum, newSettings = new)
                        finishManualChannelBatch()
                    } catch (e: CancellationException) {
                        abortManualChannelBatch(batchRequestIds)
                        throw e
                    } catch (e: Exception) {
                        abortManualChannelBatch(batchRequestIds)
                        Logger.w(e) { "Manual channel update failed after enqueue" }
                        e.message?.let(::sendError) ?: sendError(Res.string.unknown_error)
                    } catch (e: Throwable) {
                        abortManualChannelBatch(batchRequestIds)
                        throw e
                    }
                }
            } finally {
                synchronized(manualChannelBatchJobsLock) { manualChannelBatchJobs -= batchJob }
            }
        }
    }

    private fun getManualChannelUpdatePlan(new: List<ChannelSettings>, old: List<ChannelSettings>): List<Channel> =
        getChannelList(new, old).sortedBy { it.index }

    private suspend fun commitManualChannelSettings(destNum: Int, newSettings: List<ChannelSettings>) {
        withContext(NonCancellable) {
            // replaceAllSettings re-keys the conversations itself; remote-admin of another node's channels must not
            // touch this device's conversations, which is what the destNum gate protects.
            if (destNum == myNodeNum) {
                radioConfigRepository.replaceAllSettings(newSettings)
            }
            _radioConfigState.update { it.copy(channelList = newSettings) }
        }
    }

    private suspend fun reconcileInterruptedManualChannelUpdate(destNum: Int, appliedSettings: List<ChannelSettings>) {
        withContext(NonCancellable) {
            Logger.w { "Reconciling interrupted manual channel update appliedSettings=${appliedSettings.size}" }
            if (destNum == myNodeNum) {
                radioConfigRepository.replaceAllSettings(appliedSettings)
            }
            _radioConfigState.update { it.copy(channelList = appliedSettings) }
        }
    }

    fun setConfig(config: Config) {
        val destNum = destNum ?: destNode.value?.num ?: return
        launchConfigSave(config) {
            launchWrite("setConfig") { adminController.setConfig(destNum, config) }
        }
    }

    /**
     * Saves [config] to the connected node on [session] only, as [setConfig] does. [onWritten] runs once the node has
     * answered the write, before that answer settles the save, and not when [session] refused it: a follow-up there
     * cannot overtake the write or reach another session, and it owns the save's result.
     */
    fun setLocalConfig(config: Config, session: RadioSessionContext, onWritten: suspend () -> Unit) {
        launchConfigSave(config) {
            launchWrite("setConfig") {
                adminController.setLocalConfigOn(session, config).also { result ->
                    if (result != AdminResult.NodeUnreachable) onWritten()
                }
            }
        }
    }

    private fun launchConfigSave(config: Config, send: suspend () -> Unit) {
        safeLaunch(tag = "setConfig") {
            _radioConfigState.update { state ->
                state.copy(
                    radioConfig =
                    state.radioConfig
                        .newBuilder()
                        .also { wb ->
                            wb.device = config.device ?: state.radioConfig.device
                            wb.position = config.position ?: state.radioConfig.position
                            wb.power = config.power ?: state.radioConfig.power
                            wb.network = config.network ?: state.radioConfig.network
                            wb.display = config.display ?: state.radioConfig.display
                            // LoRa is intentionally NOT applied optimistically: the firmware can clamp or
                            // region-swap
                            // (e.g. EU sibling) a LoRa write and applies it live, so the form must reflect the
                            // device's
                            // actual value. It is re-read from the device when the LoRa screen is next opened.
                            wb.bluetooth = config.bluetooth ?: state.radioConfig.bluetooth
                            wb.security = config.security ?: state.radioConfig.security
                        }
                        .build(),
                )
            }
            expectRestartIfLocal(config.saveRebootBehavior())
            send()
        }
    }

    fun setModuleConfig(config: ModuleConfig) {
        val destNum = destNum ?: destNode.value?.num ?: return
        safeLaunch(tag = "setModuleConfig") {
            _radioConfigState.update { state -> state.copy(moduleConfig = state.moduleConfig.mergedWith(config)) }
            expectRestartIfLocal(config.saveRebootBehavior())
            launchWrite("setModuleConfig") { adminController.setModuleConfig(destNum, config) }
        }
    }

    fun setRingtone(ringtone: String) {
        val destNum = destNum ?: destNode.value?.num ?: return
        retireReadRequestsForRoute(radioConfigState.value.route)
        _radioConfigState.update { it.copy(ringtone = ringtone) }
        safeLaunch(tag = "setRingtone") { adminController.setRingtone(destNum, ringtone) }
    }

    fun playRingtone() {
        val destNum = destNum ?: destNode.value?.num ?: return
        safeLaunch(tag = "playRingtone") { adminController.playRingtone(destNum) }
    }

    fun setCannedMessages(messages: String) {
        val destNum = destNum ?: destNode.value?.num ?: return
        retireReadRequestsForRoute(radioConfigState.value.route)
        _radioConfigState.update { it.copy(cannedMessageMessages = messages) }
        safeLaunch(tag = "setCannedMessages") { adminController.setCannedMessages(destNum, messages) }
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
                    launchAction("setTime") { adminActionsUseCase.setTime(destNum) }
                    trackAdminAction()
                }

            AdminRoute.REBOOT.name ->
                safeLaunch(tag = "reboot") {
                    expectRestartIfLocal(RebootBehavior.ALWAYS)
                    launchAction("reboot") { adminActionsUseCase.reboot(destNum) }
                    trackAdminAction()
                }

            AdminRoute.REBOOT_DFU.name ->
                safeLaunch(tag = "rebootToDfu") {
                    if (isLocal) nodeRestartTracker.expectRestart()
                    launchRequest(
                        "rebootToDfu",
                        register = { token ->
                            registerRequestId(token)
                            dfuRequestIds.add(token)
                        },
                        call = { adminActionsUseCase.rebootToDfu(destNum) },
                    ) { token, _ ->
                        completeWrite(token)
                    }
                    trackAdminAction()
                }

            AdminRoute.SHUTDOWN.name ->
                with(radioConfigState.value) {
                    if (metadata?.canShutdown != true) {
                        sendError(Res.string.cant_shutdown)
                    } else {
                        safeLaunch(tag = "shutdown") {
                            launchAction("shutdown") { adminActionsUseCase.shutdown(destNum) }
                            trackAdminAction()
                        }
                    }
                }

            AdminRoute.FACTORY_RESET.name ->
                safeLaunch(tag = "factoryReset") {
                    val isLocal = (destNum == myNodeNum)
                    if (isLocal) nodeRestartTracker.expectRestart()
                    launchAction("factoryReset") { adminActionsUseCase.factoryReset(destNum, isLocal) }
                    trackAdminAction()
                }

            AdminRoute.NODEDB_RESET.name ->
                safeLaunch(tag = "nodedbReset") {
                    val isLocal = (destNum == myNodeNum)
                    // Firmware reboots after a nodedb reset, so a local reset drops the transport just like a
                    // factory reset — mark it expected so the UI shows "restarting" instead of a surprise disconnect.
                    if (isLocal) nodeRestartTracker.expectRestart()
                    launchAction("nodedbReset") { adminActionsUseCase.nodedbReset(destNum, preserveFavorites, isLocal) }
                    trackAdminAction()
                }
        }
    }

    fun setFixedPosition(position: Position) {
        val destNum = destNum ?: destNode.value?.num ?: return
        safeLaunch(tag = "setFixedPosition") { adminController.setFixedPosition(destNum, position) }
    }

    fun removeFixedPosition() {
        val destNum = destNum ?: destNode.value?.num ?: return
        safeLaunch(tag = "removeFixedPosition") { adminController.setFixedPosition(destNum, Position(0.0, 0.0, 0)) }
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
                longName = nodeRepository.nodeDBbyNum.value[nodeNum]?.user?.long_name?.takeIf { it.isNotBlank() },
            )
            snackbarManager.showSnackbar(message = UiText.Resource(Res.string.key_backup_saved).resolve())
            onComplete()
        }
    }

    /** Every stored key backup, this node's own first, then newest first. */
    fun securityKeyBackups(): List<SecurityKeyBackupOption> {
        val nodeNum = destNum ?: destNode.value?.num
        val nodes = nodeRepository.nodeDBbyNum.value
        return securityKeyBackupStore
            .all()
            .map { (num, keys) ->
                SecurityKeyBackupOption(
                    nodeNum = num,
                    longName = keys.longName ?: nodes[num]?.user?.long_name?.takeIf { it.isNotBlank() },
                    timestamp = keys.timestamp,
                    isCurrentNode = num == nodeNum,
                )
            }
            .sortedWith(
                compareByDescending<SecurityKeyBackupOption> { it.isCurrentNode }.thenByDescending { it.timestamp },
            )
    }

    /**
     * Restores the keys backed up under [fromNodeNum] (default: this node) and pushes them to the device via admin
     * config.
     */
    fun restoreSecurityKeys(fromNodeNum: Int? = null) {
        val nodeNum = destNum ?: destNode.value?.num ?: return
        safeLaunch(tag = "restoreSecurityKeys") {
            val stored = securityKeyBackupStore.get(fromNodeNum ?: nodeNum)
            if (stored == null) {
                snackbarManager.showSnackbar(message = UiText.Resource(Res.string.key_backup_not_found).resolve())
                return@safeLaunch
            }
            importSecurityConfigUseCase(stored)
                .onSuccess {
                    setConfig(Config.Builder().also { wb -> wb.security = it }.build())
                    snackbarManager.showSnackbar(message = UiText.Resource(Res.string.key_backup_restored).resolve())
                }
                .onFailure {
                    snackbarManager.showSnackbar(
                        message = UiText.Resource(Res.string.key_backup_restore_failed).resolve(),
                    )
                }
        }
    }

    /** Deletes the encrypted key backup stored under [backupNodeNum] (default: this node), if any. */
    fun deleteSecurityKeyBackup(backupNodeNum: Int? = null, onComplete: () -> Unit = {}) {
        val nodeNum = backupNodeNum ?: destNum ?: destNode.value?.num ?: return
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
     * Makes [check] the result of the save in flight in place of the write's acknowledgement: loading until it returns,
     * then success when it returns null, or the error it returns.
     */
    suspend fun settleSave(check: suspend () -> StringResource?) {
        clearRequestIds()
        _radioConfigState.update { it.copy(route = "", responseState = ResponseState.Loading()) }
        val error = check()
        if (error == null) {
            setResponseStateSuccess()
        } else {
            _radioConfigState.update { state ->
                if (state.responseState is ResponseState.Loading) {
                    state.copy(responseState = ResponseState.Error(UiText.Resource(error)))
                } else {
                    state
                }
            }
        }
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
        if (destNum == null && serviceRepository.connectionState.value != ConnectionState.Connected) return
        setResponseStateLoading(route = route, showOverlay = destNum != null)
    }

    fun setResponseStateLoading(route: Enum<*>) {
        setResponseStateLoading(route = route, showOverlay = true)
    }

    private fun setResponseStateLoading(route: Enum<*>, showOverlay: Boolean) {
        val destNum = destNum ?: destNode.value?.num ?: return

        _radioConfigState.update {
            it.copy(route = route.name, responseState = ResponseState.Loading(showOverlay = showOverlay))
        }
        loadFanOut = null

        when (route) {
            ConfigRoute.USER -> {
                launchRead("getOwner", { adminController.getOwner(destNum) }, ::applyOwner)
                // The status message is edited on the user screen, so it is read with the owner. Gated like the
                // editor: firmware without the module never answers the get, leaving the overlay waiting.
                val metadata = radioConfigState.value.metadata
                val readsStatusMessage =
                    Capabilities(metadata?.firmware_version).supportsStatusMessage &&
                        !metadata.excludes(ExcludedModules.STATUSMESSAGE_CONFIG)
                loadFanOut = ConfigRoute.USER.name to readsStatusMessage
                if (readsStatusMessage) {
                    launchRead(
                        "getStatusMessageConfig",
                        {
                            adminController.getModuleConfig(
                                destNum,
                                AdminMessage.ModuleConfigType.STATUSMESSAGE_CONFIG.value,
                            )
                        },
                        ::applyModuleConfig,
                    )
                }
            }

            ConfigRoute.CHANNELS -> {
                launchRead(
                    "getChannel0",
                    { adminController.getChannel(destNum, 0) },
                    ::applyChannel,
                )
                launchRead(
                    "getLoraConfig",
                    { adminController.getConfig(destNum, AdminMessage.ConfigType.LORA_CONFIG.value) },
                    ::applyConfig,
                )
                // channel editor is synchronous, so we don't use requestIds as total
                setResponseStateTotal(maxChannels + 1)
            }

            is AdminRoute -> {
                launchRead(
                    "getDeviceMetadata",
                    { adminController.getDeviceMetadata(destNum) },
                    ::applyMetadata,
                )
                setResponseStateTotal(2)
            }

            is ConfigRoute -> loadConfigRoute(destNum, route)

            is ModuleRoute -> loadModuleRoute(destNum, route)
        }
    }

    private fun loadConfigRoute(destNum: Int, route: ConfigRoute) {
        if (route == ConfigRoute.LORA) {
            launchRead(
                "getChannel0ForLora",
                { adminController.getChannel(destNum, 0) },
                ::applyChannel,
            )
        }
        if (route == ConfigRoute.NETWORK) {
            launchRead(
                "getConnectionStatus",
                { adminController.getDeviceConnectionStatus(destNum) },
                ::applyConnectionStatus,
            )
        }
        launchRead(
            "getConfig",
            { adminController.getConfig(destNum, route.type) },
            ::applyConfig,
        )
    }

    private fun loadModuleRoute(destNum: Int, route: ModuleRoute) {
        if (route == ModuleRoute.CANNED_MESSAGE) {
            launchRead(
                "getCannedMessages",
                { adminController.getCannedMessages(destNum) },
                ::applyCannedMessages,
            )
        }
        if (route == ModuleRoute.EXT_NOTIFICATION) {
            launchRead("getRingtone", { adminController.getRingtone(destNum) }, ::applyRingtone)
        }
        if (route == ModuleRoute.MESH_BEACON) {
            // The beacon editor gates on the radio's LoRa region and offers its channels, neither of which a remote
            // session has until read. Firmware without the module never answers the module get, so skip it there.
            launchRead(
                "getChannel0ForMeshBeacon",
                { adminController.getChannel(destNum, 0) },
                ::applyChannel,
            )
            launchRead(
                "getLoraConfigForMeshBeacon",
                { adminController.getConfig(destNum, AdminMessage.ConfigType.LORA_CONFIG.value) },
                ::applyConfig,
            )
            if (!Capabilities(radioConfigState.value.metadata?.firmware_version).supportsMeshBeacon) return
        }
        launchRead(
            "getModuleConfig",
            { adminController.getModuleConfig(destNum, route.type) },
            ::applyModuleConfig,
        )
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
                    val timedOutDfuRequest = packetId in dfuRequestIds
                    val requestRoute = readRequestRoutes[packetId].orEmpty()
                    removeRequestId(packetId)
                    if (isSingleResponseRemoteReadRoute(requestRoute)) {
                        retainLateRemoteRead(packetId, requestRoute)
                    }
                    if (!hasPendingRequestsForRoute(requestRoute) && radioConfigState.value.route == requestRoute) {
                        // A save that reboots the node races the reboot against its ACK; a timeout here during an
                        // expected restart means the reboot won — treat it as the restarting-success, not an error.
                        // A manual channel batch never reboots, so exclude it even inside a stale restart window.
                        // nRF52 firmware jumps to its bootloader without acking, so a DFU request only fails loudly.
                        val restartWon =
                            nodeRestartTracker.restartExpected.value &&
                                !timedOutBatchRequest &&
                                !manualChannelBatchInFlight()
                        if ((restartWon || timedOutDfuRequest) && radioConfigState.value.route.isEmpty()) {
                            setResponseStateSuccess()
                        } else {
                            sendError(Res.string.timeout)
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

    /**
     * Registers a request token through [register], runs [call] and hands a value to [onAnswer]. A refusal, a lost link
     * outside an expected restart and a thrown failure end the flow at once; a timeout is left to the request's own
     * deadline.
     */
    private fun <T> launchRequest(
        tag: String,
        register: (Int) -> Unit,
        call: suspend () -> AdminResult<T>,
        cancelWhenRetired: Boolean = false,
        onAnswer: suspend (token: Int, value: T) -> Unit,
    ): Int {
        val token = ++lastRequestToken
        register(token)
        val job =
            safeLaunch(tag = tag) {
                val outcome = safeCatching { call() }
                requestJobs.remove(token)
                outcome.fold(
                    onSuccess = { answer ->
                        when {
                            answer is AdminResult.Success -> onAnswer(token, answer.value)

                            answer == AdminResult.NodeUnreachable && !nodeRestartTracker.restartExpected.value ->
                                failRequest(token, UiText.Resource(Res.string.not_connected))

                            else -> answer.refusalText()?.let { failRequest(token, it) }
                        }
                    },
                    onFailure = { failure ->
                        Logger.w(failure) { "Radio request $tag failed" }
                        val message = failure.message?.let(UiText::DynamicString)
                        failRequest(token, message ?: UiText.Resource(Res.string.unknown_error))
                    },
                )
            }
        // A write runs to its end even after its token is retired, so its local projection lands; only reads stop.
        if (cancelWhenRetired && job.isActive) requestJobs[token] = job
        return token
    }

    /**
     * The text for a refusal the radio reported; null for a timeout or a lost link, which the request's own deadline
     * settles.
     */
    private fun AdminResult<*>.refusalText(): UiText? =
        routingErrorOrNull()?.let { UiText.Resource(getStringResFrom(it)) }

    private fun answerDestNum(): Int? = destNum ?: destNode.value?.num

    private fun isOutstanding(token: Int): Boolean = token in requestIds.value || token in lateRemoteReads

    private fun <T> launchRead(tag: String, call: suspend () -> AdminResult<T>, apply: (T, late: Boolean) -> Unit) {
        launchRequest(tag, ::registerReadRequestId, call, cancelWhenRetired = true) { token, value ->
            completeRead(token, value, apply)
        }
    }

    private fun launchWrite(tag: String, call: suspend () -> AdminResult<Unit>) {
        launchRequest(tag, ::registerWriteRequestId, call) { token, _ -> completeWrite(token) }
    }

    private fun launchAction(tag: String, call: suspend () -> AdminResult<Unit>) {
        launchRequest(tag, ::registerRequestId, call) { token, _ -> completeWrite(token) }
    }

    private fun cancelRequestJobs(tokens: Collection<Int>) {
        tokens.forEach { requestJobs.remove(it)?.cancel() }
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
        cancelRequestJobs(requestJobs.keys.toList())
        requestTimeoutJobs.values.forEach { it.cancel() }
        requestTimeoutJobs.clear()
        readRequestRoutes.clear()
        lateRemoteReads.values.forEach { it.expiryJob.cancel() }
        lateRemoteReads.clear()
        manualChannelBatchRequestIds.clear()
        requestIds.value = hashSetOf()
    }

    private fun removeRequestId(packetId: Int) {
        requestTimeoutJobs.remove(packetId)?.cancel()
        readRequestRoutes.remove(packetId)
        manualChannelBatchRequestIds.remove(packetId)
        dfuRequestIds.remove(packetId)
        requestIds.update { it.withoutPacketId(packetId) }
    }

    private fun removeRequestIds(packetIds: Set<Int>) {
        cancelRequestJobs(packetIds)
        packetIds.forEach { requestTimeoutJobs.remove(it)?.cancel() }
        packetIds.forEach {
            readRequestRoutes.remove(it)
            removeLateRemoteRead(it)
        }
        manualChannelBatchRequestIds.removeAll(packetIds)
        dfuRequestIds.removeAll(packetIds)
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

    /** A refusal, a lost link outside a restart, or a thrown failure: terminal for the visible flow. */
    private fun failRequest(token: Int, message: UiText) {
        if (answerDestNum() == null || !isOutstanding(token)) return
        val readRoute = readRequestRoutes[token]
        when {
            // The SDK call behind a late read has finished, so a failure ends it.
            token in lateRemoteReads -> removeLateRemoteRead(token)

            readRoute != null && readRoute != radioConfigState.value.route -> removeRequestId(token)

            else -> {
                // Drop every outstanding request and its timeout so a late timeout cannot overwrite the specific
                // failure or block the next retry.
                invalidateManualChannelBatch()
                clearRequestIds()
                sendError(message)
            }
        }
    }

    /** A write, action or batch write was acknowledged. */
    private fun completeWrite(token: Int) {
        answerDestNum() ?: return
        if (token !in requestIds.value) return
        if (radioConfigState.value.route.isEmpty()) {
            removeRequestId(token)
            if (!hasPendingRequestsForRoute("")) completeSetRequestOrProgressBatch() else incrementCompleted()
        }
    }

    /** A read answered: [apply] stores the value, then the flow advances or finishes. */
    private fun <T> completeRead(token: Int, value: T, apply: (T, late: Boolean) -> Unit) {
        val destNum = answerDestNum()
        if (destNum == null || !isOutstanding(token)) return
        val lateRoute = lateRemoteReads[token]?.route
        val route = radioConfigState.value.route
        apply(value, lateRoute != null)
        if (lateRoute != null) {
            removeLateRemoteRead(token)
            // A late answer satisfies its single-response route, including any retry of that route.
            retireReadRequestsForRoute(lateRoute)
        } else {
            if (AdminRoute.entries.any { it.name == route }) sendAdminRequest(destNum)
            // Defer the removal so a chain continuation launched by [apply] registers first; launches run FIFO on
            // the main dispatcher.
            safeLaunch(tag = "completePacketResponse") {
                removeRequestId(token)
                if (requestIds.value.isEmpty()) {
                    if (route.isNotEmpty() && !AdminRoute.entries.any { it.name == route }) {
                        clearPacketResponse()
                    } else if (route.isEmpty()) {
                        completeSetRequestOrProgressBatch()
                    }
                }
            }
        }
    }

    private fun applyMetadata(metadata: DeviceMetadata, late: Boolean) {
        _radioConfigState.update { it.copy(metadata = metadata) }
        if (!late) incrementCompleted()
    }

    private fun applyOwner(user: User, late: Boolean) {
        _radioConfigState.update { it.copy(userConfig = user) }
        if (!late) incrementCompleted()
    }

    private fun applyCannedMessages(messages: String, late: Boolean) {
        _radioConfigState.update { it.copy(cannedMessageMessages = messages) }
        if (!late) incrementCompleted()
    }

    private fun applyRingtone(ringtone: String, late: Boolean) {
        _radioConfigState.update { it.copy(ringtone = ringtone) }
        if (!late) incrementCompleted()
    }

    private fun applyConnectionStatus(status: DeviceConnectionStatus, late: Boolean) {
        _radioConfigState.update { it.copy(deviceConnectionStatus = status) }
        if (!late) incrementCompleted()
    }

    private fun applyModuleConfig(config: ModuleConfig, late: Boolean) {
        _radioConfigState.update { state -> state.copy(moduleConfig = state.moduleConfig.mergedWith(config)) }
        if (!late) incrementCompleted()
    }

    private fun applyConfig(response: Config, late: Boolean) {
        _radioConfigState.update { state ->
            state.copy(
                radioConfig =
                state.radioConfig
                    .newBuilder()
                    .also { wb ->
                        wb.device = response.device ?: state.radioConfig.device
                        wb.position = response.position ?: state.radioConfig.position
                        wb.power = response.power ?: state.radioConfig.power
                        wb.network = response.network ?: state.radioConfig.network
                        wb.display = response.display ?: state.radioConfig.display
                        wb.lora = response.lora ?: state.radioConfig.lora
                        wb.bluetooth = response.bluetooth ?: state.radioConfig.bluetooth
                        wb.security = response.security ?: state.radioConfig.security
                    }
                    .build(),
            )
        }
        if (!late) incrementCompleted()
    }

    private fun applyChannel(response: Channel, late: Boolean) {
        val route = radioConfigState.value.route
        // Stop once we get to the first disabled entry
        if (response.role != Channel.Role.DISABLED) {
            _radioConfigState.update { state ->
                state.copy(
                    channelList =
                    state.channelList.toMutableList().apply {
                        val index = response.index
                        val settings = response.settings ?: ChannelSettings.Builder().build()
                        // Make sure list is large enough
                        while (size <= index) add(ChannelSettings.Builder().build())
                        set(index, settings)
                    },
                )
            }
            if (!late) incrementCompleted()
            val index = response.index
            val destNum = answerDestNum() ?: return
            if (!late && index + 1 < maxChannels && route == ConfigRoute.CHANNELS.name) {
                // Not done yet, request next channel
                launchRead("getNextChannel", { adminController.getChannel(destNum, index + 1) }, ::applyChannel)
            }
        } else if (!late) {
            // Received last channel, update total and start channel editor
            setResponseStateTotal(response.index + 1)
        }
    }

    private fun isRemoteReadRoute(route: String): Boolean = destNum != null &&
        destNum != myNodeNum &&
        (ConfigRoute.entries.any { it.name == route } || ModuleRoute.entries.any { it.name == route })

    private fun isSingleResponseRemoteReadRoute(route: String): Boolean {
        if (!isRemoteReadRoute(route)) return false
        val hasReadFanOut =
            loadFanOut?.takeIf { it.first == route }?.second
                ?: ConfigRoute.entries.firstOrNull { it.name == route }?.hasReadFanOut
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

/** The same device under a new node number, as firmware 2.8 leaves it once it mints its key. */
private fun MyNodeInfo.isRenumberedTo(other: MyNodeInfo): Boolean =
    myNodeNum != other.myNodeNum && deviceId != null && deviceId == other.deviceId

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
        add(ChannelSettings.Builder().build())
    }
    this[channel.index] =
        if (channel.role == Channel.Role.DISABLED) {
            ChannelSettings.Builder().build()
        } else {
            channel.settings ?: ChannelSettings.Builder().build()
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

/**
 * Firmware `AdminModule::handleSetModuleConfig` reboots for every module section except status message and Mesh Beacon.
 */
internal fun ModuleConfig.saveRebootBehavior(): RebootBehavior =
    if (statusmessage != null || mesh_beacon != null) RebootBehavior.NEVER else RebootBehavior.ALWAYS
