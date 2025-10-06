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
import android.os.RemoteException
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.geeksville.mesh.AdminProtos
import com.geeksville.mesh.AppOnlyProtos
import com.geeksville.mesh.ConfigProtos.Config
import com.geeksville.mesh.LocalOnlyProtos.LocalConfig
import com.geeksville.mesh.LocalOnlyProtos.LocalModuleConfig
import com.geeksville.mesh.MeshProtos
import com.geeksville.mesh.channelSet
import com.geeksville.mesh.config
import com.geeksville.mesh.copy
import com.geeksville.mesh.repository.radio.MeshActivity
import com.geeksville.mesh.repository.radio.RadioInterfaceService
import com.geeksville.mesh.service.MeshServiceNotifications
import com.geeksville.mesh.ui.sharing.toSharedContact
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.meshtastic.core.analytics.platform.PlatformAnalytics
import org.meshtastic.core.data.repository.FirmwareReleaseRepository
import org.meshtastic.core.data.repository.MeshLogRepository
import org.meshtastic.core.data.repository.NodeRepository
import org.meshtastic.core.data.repository.QuickChatActionRepository
import org.meshtastic.core.data.repository.RadioConfigRepository
import org.meshtastic.core.database.entity.MyNodeEntity
import org.meshtastic.core.database.entity.QuickChatAction
import org.meshtastic.core.database.entity.asDeviceVersion
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.datastore.UiPreferencesDataSource
import org.meshtastic.core.model.util.toChannelSet
import org.meshtastic.core.service.IMeshService
import org.meshtastic.core.service.ServiceRepository
import org.meshtastic.core.strings.R
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

data class Contact(
    val contactKey: String,
    val shortName: String,
    val longName: String,
    val lastMessageTime: String?,
    val lastMessageText: String?,
    val unreadCount: Int,
    val messageCount: Int,
    val isMuted: Boolean,
    val isUnmessageable: Boolean,
    val nodeColors: Pair<Int, Int>? = null,
)

@Suppress("LongParameterList", "LargeClass", "UnusedPrivateProperty")
@HiltViewModel
class UIViewModel
@Inject
constructor(
    private val app: Application,
    private val nodeDB: NodeRepository,
    private val radioConfigRepository: RadioConfigRepository,
    private val serviceRepository: ServiceRepository,
    radioInterfaceService: RadioInterfaceService,
    meshLogRepository: MeshLogRepository,
    private val quickChatActionRepository: QuickChatActionRepository,
    firmwareReleaseRepository: FirmwareReleaseRepository,
    private val uiPreferencesDataSource: UiPreferencesDataSource,
    private val meshServiceNotifications: MeshServiceNotifications,
    private val analytics: PlatformAnalytics,
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

    private val localConfig = MutableStateFlow<LocalConfig>(LocalConfig.getDefaultInstance())

    val config
        get() = localConfig.value

    private val _moduleConfig = MutableStateFlow<LocalModuleConfig>(LocalModuleConfig.getDefaultInstance())
    val moduleConfig: StateFlow<LocalModuleConfig> = _moduleConfig
    val module
        get() = _moduleConfig.value

    private val _channels = MutableStateFlow(channelSet {})
    val channels: StateFlow<AppOnlyProtos.ChannelSet>
        get() = _channels

    val quickChatActions
        get() =
            quickChatActionRepository
                .getAllActions()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // hardware info about our local device (can be null)
    val myNodeInfo: StateFlow<MyNodeEntity?>
        get() = nodeDB.myNodeInfo

    val ourNodeInfo: StateFlow<Node?>
        get() = nodeDB.ourNodeInfo

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

        radioConfigRepository.localConfigFlow.onEach { config -> localConfig.value = config }.launchIn(viewModelScope)
        radioConfigRepository.moduleConfigFlow
            .onEach { config -> _moduleConfig.value = config }
            .launchIn(viewModelScope)
        radioConfigRepository.channelSetFlow
            .onEach { channelSet -> _channels.value = channelSet }
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

    var region: Config.LoRaConfig.RegionCode
        get() = config.lora.region
        set(value) {
            updateLoraConfig { it.copy { region = value } }
        }

    override fun onCleared() {
        super.onCleared()
        Timber.d("ViewModel cleared")
    }

    private inline fun updateLoraConfig(crossinline body: (Config.LoRaConfig) -> Config.LoRaConfig) {
        val data = body(config.lora)
        setConfig(config { lora = data })
    }

    // Set the radio config (also updates our saved copy in preferences)
    fun setConfig(config: Config) {
        try {
            meshService?.setConfig(config.toByteArray())
        } catch (ex: RemoteException) {
            Timber.e(ex, "Set config error")
        }
    }

    fun addQuickChatAction(action: QuickChatAction) =
        viewModelScope.launch(Dispatchers.IO) { quickChatActionRepository.upsert(action) }

    fun deleteQuickChatAction(action: QuickChatAction) =
        viewModelScope.launch(Dispatchers.IO) { quickChatActionRepository.delete(action) }

    fun updateActionPositions(actions: List<QuickChatAction>) {
        viewModelScope.launch(Dispatchers.IO) {
            for (position in actions.indices) {
                quickChatActionRepository.setItemPosition(actions[position].uuid, position)
            }
        }
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

    fun addNavigationTrackingEffect(navController: NavHostController) {
        analytics.addNavigationTrackingEffect(navController)
    }
}
