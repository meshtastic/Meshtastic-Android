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
package org.meshtastic.core.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.meshtastic.core.resources.UiText

data class FirmwareUpdateStatus(val isOtaUpdateActive: Boolean = false, val isAwaitingOtaStatus: Boolean = false)

/**
 * What a running firmware update is doing, for surfaces outside the firmware screen. [percent] is null when unknown.
 */
data class FirmwareUpdateProgress(val message: UiText, val percent: Int?)

class FirmwareUpdateStatusRepository {
    private val _status = MutableStateFlow(FirmwareUpdateStatus())
    val status: StateFlow<FirmwareUpdateStatus> = _status.asStateFlow()

    private val _progress = MutableStateFlow<FirmwareUpdateProgress?>(null)

    /** Null whenever no update is transferring, including while the flow waits on the user. */
    val progress: StateFlow<FirmwareUpdateProgress?> = _progress.asStateFlow()

    fun publishProgress(progress: FirmwareUpdateProgress?) {
        _progress.value = progress
    }

    fun beginOtaUpdate() {
        _status.value = FirmwareUpdateStatus(isOtaUpdateActive = true)
    }

    fun beginOtaPreflight() {
        _status.value = FirmwareUpdateStatus(isOtaUpdateActive = true, isAwaitingOtaStatus = true)
    }

    fun finishOtaPreflight() {
        _status.update { it.copy(isAwaitingOtaStatus = false) }
    }

    fun endOtaUpdate() {
        _status.value = FirmwareUpdateStatus()
    }
}
