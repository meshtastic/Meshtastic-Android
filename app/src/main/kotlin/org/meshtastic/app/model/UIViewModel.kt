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
package org.meshtastic.app.model

import android.net.Uri
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.data.repository.FirmwareReleaseRepository
import org.meshtastic.core.datastore.UiPreferencesDataSource
import org.meshtastic.core.model.RadioController
import org.meshtastic.core.model.util.dispatchMeshtasticUri
import org.meshtastic.core.repository.MeshLogRepository
import org.meshtastic.core.repository.MeshServiceNotifications
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.service.AndroidServiceRepository
import org.meshtastic.core.service.IMeshService
import org.meshtastic.core.ui.util.AlertManager
import org.meshtastic.core.ui.viewmodel.BaseUIViewModel

/**
 * Android-specific thin adapter over [BaseUIViewModel].
 *
 * Adds deep-link / URI handling (requires [android.net.Uri]) and direct [IMeshService] access that cannot live in
 * `commonMain`.
 */
@KoinViewModel
@Suppress("LongParameterList", "TooManyFunctions")
class UIViewModel(
    nodeDB: NodeRepository,
    private val androidServiceRepository: AndroidServiceRepository,
    radioController: RadioController,
    radioInterfaceService: RadioInterfaceService,
    meshLogRepository: MeshLogRepository,
    firmwareReleaseRepository: FirmwareReleaseRepository,
    uiPreferencesDataSource: UiPreferencesDataSource,
    meshServiceNotifications: MeshServiceNotifications,
    packetRepository: PacketRepository,
    alertManager: AlertManager,
) : BaseUIViewModel(
    nodeDB = nodeDB,
    serviceRepository = androidServiceRepository,
    radioController = radioController,
    radioInterfaceService = radioInterfaceService,
    meshLogRepository = meshLogRepository,
    firmwareReleaseRepository = firmwareReleaseRepository,
    uiPreferencesDataSource = uiPreferencesDataSource,
    meshServiceNotifications = meshServiceNotifications,
    packetRepository = packetRepository,
    alertManager = alertManager,
) {

    val meshService: IMeshService?
        get() = androidServiceRepository.meshService

    private val _navigationDeepLink = MutableSharedFlow<Uri>(replay = 1)
    val navigationDeepLink = _navigationDeepLink.asSharedFlow()

    fun handleNavigationDeepLink(uri: Uri) {
        _navigationDeepLink.tryEmit(uri)
    }

    /** Unified handler for scanned Meshtastic URIs (contacts or channels). */
    fun handleScannedUri(uri: Uri, onInvalid: () -> Unit) {
        uri.dispatchMeshtasticUri(
            onContact = { setSharedContactRequested(it) },
            onChannel = { setRequestChannelSet(it) },
            onInvalid = onInvalid,
        )
    }
}
