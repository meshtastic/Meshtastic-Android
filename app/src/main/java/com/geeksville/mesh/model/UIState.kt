/*
 * Copyright (c) 2025 Meshtastic LLC
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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.geeksville.mesh.repository.radio.MeshActivity
import com.geeksville.mesh.repository.radio.RadioInterfaceService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import org.meshtastic.core.analytics.platform.PlatformAnalytics
import org.meshtastic.core.data.repository.FirmwareReleaseRepository
import org.meshtastic.core.data.repository.MeshLogRepository
import org.meshtastic.core.data.repository.NodeRepository
import org.meshtastic.core.data.repository.PacketRepository
import org.meshtastic.core.database.entity.MyNodeEntity
import org.meshtastic.core.database.entity.asDeviceVersion
import org.meshtastic.core.datastore.UiPreferencesDataSource
import org.meshtastic.core.model.util.toChannelSet
import org.meshtastic.core.service.IMeshService
import org.meshtastic.core.service.MeshServiceNotifications
import org.meshtastic.core.service.ServiceRepository
import org.meshtastic.core.strings.R
import org.meshtastic.core.ui.component.toSharedContact
import org.meshtastic.core.ui.viewmodel.stateInWhileSubscribed
import org.meshtastic.proto.AdminProtos
import org.meshtastic.proto.AppOnlyProtos
import org.meshtastic.proto.MeshProtos
import timber.log.Timber
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
) : ViewModel() {

    val theme: StateFlow<Int> = uiPreferencesDataSource.theme

    val firmwareEdition = meshLogRepository.getMyNodeInfo().map { nodeInfo -> nodeInfo?.firmwareEdition }

    val clientNotification: StateFlow<MeshProtos.ClientNotification?> = serviceRepository.clientNotification

    fun clearClientNotification(notification: MeshProtos.ClientNotification) {
        serviceRepository.clearClientNotification()
        meshServiceNotifications.clearClientNotification(notification)
    }

    /**
     * Emits events for mesh network send/receive activity. This is a SharedFlow to ensure all events are delivered,
     * even if they are the same.
     */
    val meshActivity: SharedFlow<MeshActivity> =
        radioInterfaceService.meshActivity.shareIn(viewModelScope, SharingStarted.Eagerly, 0)

    data class AlertData(
        val title: String,
        val message: String? = null,
        val html: String? = null,
        val onConfirm: (() -> Unit)? = null,
        val onDismiss: (() -> Unit)? = null,
        val choices: Map<String, () -> Unit> = emptyMap(),
    )

    private val _currentAlert: MutableStateFlow<AlertData?> = MutableStateFlow(null)
    val currentAlert = _currentAlert.asStateFlow()

    fun showAlert(
        title: String,
        message: String? = null,
        html: String? = null,
        onConfirm: (() -> Unit)? = {},
        dismissable: Boolean = true,
        choices: Map<String, () -> Unit> = emptyMap(),
    ) {
        _currentAlert.value =
            AlertData(
                title = title,
                message = message,
                html = html,
                onConfirm = {
                    onConfirm?.invoke()
                    dismissAlert()
                },
                onDismiss = { if (dismissable) dismissAlert() },
                choices = choices,
            )
    }

    private fun dismissAlert() {
        _currentAlert.value = null
    }

    val meshService: IMeshService?
        get() = serviceRepository.meshService

    val unreadMessageCount =
        packetRepository.getUnreadCountTotal().map { it.coerceAtLeast(0) }.stateInWhileSubscribed(initialValue = 0)

    // hardware info about our local device (can be null)
    val myNodeInfo: StateFlow<MyNodeEntity?>
        get() = nodeDB.myNodeInfo

    val snackBarHostState = SnackbarHostState()

    fun showSnackBar(text: Int) = showSnackBar(app.getString(text))

    fun showSnackBar(
        text: String,
        actionLabel: String? = null,
        withDismissAction: Boolean = false,
        duration: SnackbarDuration = if (actionLabel == null) SnackbarDuration.Short else SnackbarDuration.Indefinite,
        onActionPerformed: (() -> Unit) = {},
        onDismissed: (() -> Unit) = {},
    ) = viewModelScope.launch {
        snackBarHostState.showSnackbar(text, actionLabel, withDismissAction, duration).run {
            when (this) {
                SnackbarResult.ActionPerformed -> onActionPerformed()
                SnackbarResult.Dismissed -> onDismissed()
            }
        }
    }

    init {
        serviceRepository.errorMessage
            .filterNotNull()
            .onEach {
                showAlert(
                    title = app.getString(R.string.client_notification),
                    message = it,
                    onConfirm = { serviceRepository.clearErrorMessage() },
                    dismissable = false,
                )
            }
            .launchIn(viewModelScope)

        Timber.d("ViewModel created")
    }

    private val _sharedContactRequested: MutableStateFlow<AdminProtos.SharedContact?> = MutableStateFlow(null)
    val sharedContactRequested: StateFlow<AdminProtos.SharedContact?>
        get() = _sharedContactRequested.asStateFlow()

    fun setSharedContactRequested(url: Uri) {
        runCatching { _sharedContactRequested.value = url.toSharedContact() }
            .onFailure { ex ->
                Timber.e(ex, "Shared contact error")
                showSnackBar(R.string.contact_invalid)
            }
    }

    /** Called immediately after activity observes requestChannelUrl */
    fun clearSharedContactRequested() {
        _sharedContactRequested.value = null
    }

    // Connection state to our radio device
    val connectionState
        get() = serviceRepository.connectionState

    private val _requestChannelSet = MutableStateFlow<AppOnlyProtos.ChannelSet?>(null)
    val requestChannelSet: StateFlow<AppOnlyProtos.ChannelSet?>
        get() = _requestChannelSet

    fun requestChannelUrl(url: Uri) = runCatching { _requestChannelSet.value = url.toChannelSet() }
        .onFailure { ex ->
            Timber.e(ex, "Channel url error")
            showSnackBar(R.string.channel_invalid)
        }

    val latestStableFirmwareRelease = firmwareReleaseRepository.stableRelease.mapNotNull { it?.asDeviceVersion() }

    /** Called immediately after activity observes requestChannelUrl */
    fun clearRequestChannelUrl() {
        _requestChannelSet.value = null
    }

    override fun onCleared() {
        super.onCleared()
        Timber.d("ViewModel cleared")
    }

    val tracerouteResponse: LiveData<String?>
        get() = serviceRepository.tracerouteResponse.asLiveData()

    fun clearTracerouteResponse() {
        serviceRepository.clearTracerouteResponse()
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
