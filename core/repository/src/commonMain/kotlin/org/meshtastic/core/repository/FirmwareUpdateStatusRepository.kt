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

data class FirmwareUpdateStatus(val isOtaUpdateActive: Boolean = false, val isAwaitingOtaStatus: Boolean = false)

class FirmwareUpdateStatusRepository {
    private val _status = MutableStateFlow(FirmwareUpdateStatus())
    val status: StateFlow<FirmwareUpdateStatus> = _status.asStateFlow()

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
