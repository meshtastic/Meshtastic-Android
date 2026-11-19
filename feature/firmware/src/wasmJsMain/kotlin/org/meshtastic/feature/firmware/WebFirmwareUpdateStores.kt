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
package org.meshtastic.feature.firmware

import kotlinx.browser.localStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single
import org.meshtastic.core.datastore.model.PendingFirmwareRecovery

/**
 * The dismissed bootloader warnings in the browser's local storage. Storage that is off, as in a private window, keeps
 * them for the page only.
 */
@Single
class WebBootloaderWarningSource : BootloaderWarningSource {
    private val serializer = ListSerializer(String.serializer())
    private var dismissed: Set<String> = load()

    override suspend fun isDismissed(address: String): Boolean = address in dismissed

    override suspend fun dismiss(address: String) {
        if (address in dismissed) return
        dismissed = dismissed + address
        runCatching { localStorage.setItem(KEY, Json.encodeToString(serializer, dismissed.toList())) }
    }

    private fun load(): Set<String> = runCatching {
        localStorage.getItem(KEY)?.let { Json.decodeFromString(serializer, it) }
    }
        .getOrNull()
        .orEmpty()
        .toSet()

    private companion object {
        const val KEY = "meshtastic.dismissedBootloaderWarnings"
    }
}

/** Recovery re-flashes a stranded radio over BLE DFU, which the browser cannot run, so there is never a record. */
@Single
class NoopFirmwareRecoverySource : FirmwareRecoverySource {
    override val pending: Flow<PendingFirmwareRecovery?> = flowOf(null)

    override suspend fun set(recovery: PendingFirmwareRecovery) = Unit

    override suspend fun clear() = Unit
}
