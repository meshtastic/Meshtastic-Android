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
package org.meshtastic.core.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavKey
import co.touchlab.kermit.Logger
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.common.util.CommonUri
import org.meshtastic.core.database.entity.asDeviceVersion
import org.meshtastic.core.model.MeshActivity
import org.meshtastic.core.model.MyNodeInfo
import org.meshtastic.core.model.RadioController
import org.meshtastic.core.model.TracerouteMapAvailability
import org.meshtastic.core.model.evaluateTracerouteMapAvailability
import org.meshtastic.core.model.service.TracerouteResponse
import org.meshtastic.core.model.util.dispatchMeshtasticUri
import org.meshtastic.core.navigation.DeepLinkRouter
import org.meshtastic.core.repository.FirmwareReleaseRepository
import org.meshtastic.core.repository.MeshLogRepository
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.NotificationManager
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.repository.UiPrefs
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.client_notification
import org.meshtastic.core.resources.compromised_keys
import org.meshtastic.core.ui.component.ScrollToTopEvent
import org.meshtastic.core.ui.util.AlertManager
import org.meshtastic.core.ui.util.ComposableContent
import org.meshtastic.core.ui.util.SnackbarManager
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ClientNotification
import org.meshtastic.proto.SharedContact

/**
 * Shared base for the application-level ViewModel.
 *
 * Contains all platform-independent state and actions (themes, alerts, connection state, firmware checks, traceroute,
 * shared contacts, channel sets, unread counts, etc.).
 */
@KoinViewModel
@Suppress("LongParameterList", "TooManyFunctions")
class UIViewModel(
    private val nodeDB: NodeRepository,
    protected val serviceRepository: ServiceRepository,
    private val radioController: RadioController,
    radioInterfaceService: RadioInterfaceService,
    meshLogRepository: MeshLogRepository,
    firmwareReleaseRepository: FirmwareReleaseRepository,
    private val uiPrefs: UiPrefs,
    private val notificationManager: NotificationManager,
    packetRepository: PacketRepository,
    val alertManager: AlertManager,
    val snackbarManager: SnackbarManager,
) : ViewModel() {

    private val _navigationDeepLink = MutableSharedFlow<List<NavKey>>(replay = 1)
    val navigationDeepLink = _navigationDeepLink.asSharedFlow()

    /**
     * Unified handler for all Meshtastic deep links and OS intents.
     *
     * This method orchestrates two distinct types of URI handling:
     * 1. **Navigation:** First attempts to parse the URI into a typed [NavKey] backstack via [DeepLinkRouter]. If
     *    successful, navigates the user to the target screen.
     * 2. **Data Import:** If navigation fails, falls back to legacy contact/channel parsing via
     *    [dispatchMeshtasticUri]. This triggers import dialogs for shared nodes or channel configurations.
     */
    fun handleDeepLink(uri: CommonUri, onInvalid: () -> Unit = {}) {
        // Try navigation routing first
        val navKeys = DeepLinkRouter.route(uri)
        if (navKeys != null) {
            _navigationDeepLink.tryEmit(navKeys)
            return
        }

        // Fallback to channel/contact importing
        uri.dispatchMeshtasticUri(
            onContact = { setSharedContactRequested(it) },
            onChannel = { setRequestChannelSet(it) },
            onInvalid = onInvalid,
        )
    }

    val theme: StateFlow<Int> = uiPrefs.theme
    val contrastLevel: StateFlow<Int> = uiPrefs.contrastLevel

    val firmwareEdition = meshLogRepository.getMyNodeInfo().map { nodeInfo -> nodeInfo?.firmware_edition }

    val clientNotification: StateFlow<ClientNotification?> = serviceRepository.clientNotification

    fun clearClientNotification(notification: ClientNotification) {
        serviceRepository.clearClientNotification()
        notificationManager.cancel(notification.toString().hashCode())
    }

    /** Emits events for mesh network send/receive activity. */
    val meshActivity: Flow<MeshActivity> = radioInterfaceService.meshActivity

    val currentDeviceAddressFlow: StateFlow<String?> = radioInterfaceService.currentDeviceAddressFlow

    private val _scrollToTopEventFlow =
        MutableSharedFlow<ScrollToTopEvent>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val scrollToTopEventFlow: Flow<ScrollToTopEvent> = _scrollToTopEventFlow.asFlow()

    fun emitScrollToTopEvent(event: ScrollToTopEvent) {
        _scrollToTopEventFlow.tryEmit(event)
    }

    fun tracerouteMapAvailability(forwardRoute: List<Int>, returnRoute: List<Int>): TracerouteMapAvailability =
        evaluateTracerouteMapAvailability(
            forwardRoute = forwardRoute,
            returnRoute = returnRoute,
            positionedNodeNums =
            nodeDB.nodeDBbyNum.value.values.filter { it.validPosition != null }.map { it.num }.toSet(),
        )

    fun showAlert(
        title: String? = null,
        titleRes: StringResource? = null,
        message: String? = null,
        messageRes: StringResource? = null,
        composableMessage: ComposableContent? = null,
        html: String? = null,
        onConfirm: (() -> Unit)? = {},
        onDismiss: (() -> Unit)? = null,
        confirmText: String? = null,
        confirmTextRes: StringResource? = null,
        dismissText: String? = null,
        dismissTextRes: StringResource? = null,
        choices: Map<String, () -> Unit> = emptyMap(),
    ) {
        alertManager.showAlert(
            title = title,
            titleRes = titleRes,
            message = message,
            messageRes = messageRes,
            composableMessage = composableMessage,
            html = html,
            onConfirm = onConfirm,
            onDismiss = onDismiss,
            confirmText = confirmText,
            confirmTextRes = confirmTextRes,
            dismissText = dismissText,
            dismissTextRes = dismissTextRes,
            choices = choices,
        )
    }

    fun dismissAlert() {
        alertManager.dismissAlert()
    }

    fun showSnackbar(message: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
        snackbarManager.showSnackbar(message = message, actionLabel = actionLabel, onAction = onAction)
    }

    fun setDeviceAddress(address: String) {
        radioController.setDeviceAddress(address)
    }

    val unreadMessageCount =
        packetRepository.getUnreadCountTotal().map { it.coerceAtLeast(0) }.stateInWhileSubscribed(initialValue = 0)

    // hardware info about our local device (can be null)
    val myNodeInfo: StateFlow<MyNodeInfo?>
        get() = nodeDB.myNodeInfo

    init {
        serviceRepository.errorMessage
            .filterNotNull()
            .onEach {
                showAlert(
                    titleRes = Res.string.client_notification,
                    message = it,
                    onConfirm = { serviceRepository.clearErrorMessage() },
                )
            }
            .launchIn(viewModelScope)

        serviceRepository.clientNotification
            .filterNotNull()
            .onEach { notification ->
                val isCompromised = notification.low_entropy_key != null || notification.duplicated_public_key != null
                showAlert(
                    titleRes = Res.string.client_notification,
                    message = if (isCompromised) getString(Res.string.compromised_keys) else notification.message,
                    onConfirm = {
                        // Action for compromised keys should be handled via a callback or event
                        clearClientNotification(notification)
                    },
                    onDismiss = { clearClientNotification(notification) },
                )
            }
            .launchIn(viewModelScope)

        Logger.d { "UIViewModel created" }
    }

    private val _sharedContactRequested: MutableStateFlow<SharedContact?> = MutableStateFlow(null)
    val sharedContactRequested: StateFlow<SharedContact?>
        get() = _sharedContactRequested.asStateFlow()

    fun setSharedContactRequested(contact: SharedContact?) {
        _sharedContactRequested.value = contact
    }

    /** Clears the pending shared contact request. */
    fun clearSharedContactRequested() {
        _sharedContactRequested.value = null
    }

    /** Canonical app-level connection state, sourced from [ServiceRepository.connectionState]. */
    val connectionState
        get() = serviceRepository.connectionState

    private val _requestChannelSet = MutableStateFlow<ChannelSet?>(null)
    val requestChannelSet: StateFlow<ChannelSet?>
        get() = _requestChannelSet

    fun setRequestChannelSet(channelSet: ChannelSet?) {
        _requestChannelSet.value = channelSet
    }

    val latestStableFirmwareRelease = firmwareReleaseRepository.stableRelease.mapNotNull { it?.asDeviceVersion() }

    /** Clears the pending channel set import request. */
    fun clearRequestChannelUrl() {
        _requestChannelSet.value = null
    }

    override fun onCleared() {
        super.onCleared()
        Logger.d { "UIViewModel cleared" }
    }

    val tracerouteResponse: Flow<TracerouteResponse?>
        get() = serviceRepository.tracerouteResponse

    fun clearTracerouteResponse() {
        serviceRepository.clearTracerouteResponse()
    }

    val neighborInfoResponse: StateFlow<String?> = serviceRepository.neighborInfoResponse

    fun clearNeighborInfoResponse() {
        serviceRepository.clearNeighborInfoResponse()
    }

    val appIntroCompleted: StateFlow<Boolean> = uiPrefs.appIntroCompleted

    fun onAppIntroCompleted() {
        uiPrefs.setAppIntroCompleted(true)
    }
}
