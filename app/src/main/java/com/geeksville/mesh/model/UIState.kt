/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package com.geeksville.mesh.model

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import co.touchlab.kermit.Logger
import com.geeksville.mesh.repository.radio.MeshActivity
import com.geeksville.mesh.repository.radio.RadioInterfaceService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import org.jetbrains.compose.resources.getString
import org.meshtastic.core.analytics.platform.PlatformAnalytics
import org.meshtastic.core.data.repository.FirmwareReleaseRepository
import org.meshtastic.core.data.repository.MeshLogRepository
import org.meshtastic.core.data.repository.NodeRepository
import org.meshtastic.core.data.repository.PacketRepository
import org.meshtastic.core.database.entity.MyNodeEntity
import org.meshtastic.core.database.entity.asDeviceVersion
import org.meshtastic.core.datastore.UiPreferencesDataSource
import org.meshtastic.core.model.TracerouteMapAvailability
import org.meshtastic.core.model.evaluateTracerouteMapAvailability
import org.meshtastic.core.model.util.dispatchMeshtasticUri
import org.meshtastic.core.service.IMeshService
import org.meshtastic.core.service.MeshServiceNotifications
import org.meshtastic.core.service.ServiceRepository
import org.meshtastic.core.service.TracerouteResponse
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.client_notification
import org.meshtastic.core.ui.component.ScrollToTopEvent
import org.meshtastic.core.ui.util.AlertManager
import org.meshtastic.core.ui.viewmodel.stateInWhileSubscribed
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ClientNotification
import org.meshtastic.proto.SharedContact
import javax.inject.Inject

// Given a human name, strip out the first letter of the first three words and return that as the
// initials for
// that user, ignoring emojis. If the original name is only one word, strip vowels from the original
// name and if the result is 3 or more characters, use the first three characters. If not, just take
// the first 3 characters of the original name.
fun getInitials(fullName: String): String {
    val maxInitialLength = 4
    val minWordCountForInitials = 2
    val name = fullName.trim().withoutEmojis()
    val words = name.split(Regex("\\s+")).filter { it.isNotEmpty() }

    val initials =
        when (words.size) {
            in 0 until minWordCountForInitials -> {
                val nameWithoutVowels =
                    if (name.isNotEmpty()) {
                        name.first() + name.drop(1).filterNot { c -> c.lowercase() in "aeiou" }
                    } else {
                        ""
                    }
                if (nameWithoutVowels.length >= maxInitialLength) nameWithoutVowels else name
            }

            else -> words.map { it.first() }.joinToString("")
        }
    return initials.take(maxInitialLength)
}

private fun String.withoutEmojis(): String = filterNot { char -> char.isSurrogate() }

@Suppress("LongParameterList", "LargeClass", "UnusedPrivateProperty")
@HiltViewModel
class UIViewModel
@Inject
constructor(
    private val app: Application,
    private val nodeDB: NodeRepository,
    private val serviceRepository: ServiceRepository,
    radioInterfaceService: RadioInterfaceService,
    meshLogRepository: MeshLogRepository,
    firmwareReleaseRepository: FirmwareReleaseRepository,
    private val uiPreferencesDataSource: UiPreferencesDataSource,
    private val meshServiceNotifications: MeshServiceNotifications,
    private val analytics: PlatformAnalytics,
    packetRepository: PacketRepository,
    private val alertManager: AlertManager,
) : ViewModel() {

    val theme: StateFlow<Int> = uiPreferencesDataSource.theme

    val firmwareEdition = meshLogRepository.getMyNodeInfo().map { nodeInfo -> nodeInfo?.firmware_edition }

    val clientNotification: StateFlow<ClientNotification?> = serviceRepository.clientNotification

    fun clearClientNotification(notification: ClientNotification) {
        serviceRepository.clearClientNotification()
        meshServiceNotifications.clearClientNotification(notification)
    }

    /**
     * Emits events for mesh network send/receive activity. This is a SharedFlow to ensure all events are delivered,
     * even if they are the same.
     */
    val meshActivity: SharedFlow<MeshActivity> =
        radioInterfaceService.meshActivity.shareIn(viewModelScope, SharingStarted.Eagerly, 0)

    private val _scrollToTopEventFlow =
        MutableSharedFlow<ScrollToTopEvent>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val scrollToTopEventFlow: Flow<ScrollToTopEvent> = _scrollToTopEventFlow.asSharedFlow()

    fun emitScrollToTopEvent(event: ScrollToTopEvent) {
        _scrollToTopEventFlow.tryEmit(event)
    }

    val currentAlert = alertManager.currentAlert

    fun tracerouteMapAvailability(forwardRoute: List<Int>, returnRoute: List<Int>): TracerouteMapAvailability =
        evaluateTracerouteMapAvailability(
            forwardRoute = forwardRoute,
            returnRoute = returnRoute,
            positionedNodeNums =
            nodeDB.nodeDBbyNum.value.values.filter { it.validPosition != null }.map { it.num }.toSet(),
        )

    fun showAlert(
        title: String,
        message: String? = null,
        html: String? = null,
        onConfirm: (() -> Unit)? = {},
        dismissable: Boolean = true,
        choices: Map<String, () -> Unit> = emptyMap(),
    ) {
        alertManager.showAlert(title, message, html, onConfirm, dismissable, choices)
    }

    fun dismissAlert() {
        alertManager.dismissAlert()
    }

    val meshService: IMeshService?
        get() = serviceRepository.meshService

    val unreadMessageCount =
        packetRepository.getUnreadCountTotal().map { it.coerceAtLeast(0) }.stateInWhileSubscribed(initialValue = 0)

    // hardware info about our local device (can be null)
    val myNodeInfo: StateFlow<MyNodeEntity?>
        get() = nodeDB.myNodeInfo

    init {
        serviceRepository.errorMessage
            .filterNotNull()
            .onEach {
                showAlert(
                    title = getString(Res.string.client_notification),
                    message = it,
                    onConfirm = { serviceRepository.clearErrorMessage() },
                    dismissable = false,
                )
            }
            .launchIn(viewModelScope)

        Logger.d { "ViewModel created" }
    }

    private val _sharedContactRequested: MutableStateFlow<SharedContact?> = MutableStateFlow(null)
    val sharedContactRequested: StateFlow<SharedContact?>
        get() = _sharedContactRequested.asStateFlow()

    fun setSharedContactRequested(contact: SharedContact?) {
        _sharedContactRequested.value = contact
    }

    /** Called immediately after activity observes requestChannelUrl */
    fun clearSharedContactRequested() {
        _sharedContactRequested.value = null
    }

    // Connection state to our radio device
    val connectionState
        get() = serviceRepository.connectionState

    private val _requestChannelSet = MutableStateFlow<ChannelSet?>(null)
    val requestChannelSet: StateFlow<ChannelSet?>
        get() = _requestChannelSet

    fun setRequestChannelSet(channelSet: ChannelSet?) {
        _requestChannelSet.value = channelSet
    }

    /** Unified handler for scanned Meshtastic URIs (contacts or channels). */
    fun handleScannedUri(uri: Uri, onInvalid: () -> Unit) {
        uri.dispatchMeshtasticUri(
            onContact = { setSharedContactRequested(it) },
            onChannel = { setRequestChannelSet(it) },
            onInvalid = onInvalid,
        )
    }

    val latestStableFirmwareRelease = firmwareReleaseRepository.stableRelease.mapNotNull { it?.asDeviceVersion() }

    /** Called immediately after activity observes requestChannelUrl */
    fun clearRequestChannelUrl() {
        _requestChannelSet.value = null
    }

    override fun onCleared() {
        super.onCleared()
        Logger.d { "ViewModel cleared" }
    }

    val tracerouteResponse: LiveData<TracerouteResponse?>
        get() = serviceRepository.tracerouteResponse.asLiveData()

    fun clearTracerouteResponse() {
        serviceRepository.clearTracerouteResponse()
    }

    val neighborInfoResponse: LiveData<String?>
        get() = serviceRepository.neighborInfoResponse.asLiveData()

    fun clearNeighborInfoResponse() {
        serviceRepository.clearNeighborInfoResponse()
    }

    val appIntroCompleted: StateFlow<Boolean> = uiPreferencesDataSource.appIntroCompleted

    fun onAppIntroCompleted() {
        uiPreferencesDataSource.setAppIntroCompleted(true)
    }

    @Composable
    fun AddNavigationTrackingEffect(navController: NavHostController) {
        analytics.AddNavigationTrackingEffect(navController)
    }
}
