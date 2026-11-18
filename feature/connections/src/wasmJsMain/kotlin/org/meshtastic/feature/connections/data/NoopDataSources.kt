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
package org.meshtastic.feature.connections.data

import kotlinx.browser.localStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single
import org.meshtastic.core.datastore.model.PendingFirmwareRecovery
import org.meshtastic.core.datastore.model.RecentAddress

/**
 * The recent network radios in the browser's local storage, newest first, as the Preferences-backed source keeps them
 * elsewhere; that one has no wasmJs variant. Storage that is off, as in a private window, keeps them for the page only.
 */
@Single
class WebRecentAddressesSource : RecentAddressesSource {
    private val serializer = ListSerializer(RecentAddress.serializer())
    private val state = MutableStateFlow(load())

    override val recentAddresses: Flow<List<RecentAddress>> = state.asStateFlow()

    override suspend fun add(address: RecentAddress) {
        save(listOf(address) + state.value.filterNot { it.address == address.address })
    }

    override suspend fun remove(address: String) {
        save(state.value.filterNot { it.address == address })
    }

    private fun save(list: List<RecentAddress>) {
        val kept = list.take(MAX_RECENT)
        state.value = kept
        runCatching { localStorage.setItem(KEY, Json.encodeToString(serializer, kept)) }
    }

    private fun load(): List<RecentAddress> = runCatching {
        localStorage.getItem(KEY)?.let { Json.decodeFromString(serializer, it) }
    }
        .getOrNull()
        .orEmpty()

    private companion object {
        const val KEY = "meshtastic.recentAddresses"
        const val MAX_RECENT = 10
    }
}

/** Web has no firmware-recovery banner: its data source is Preferences-backed, which has no wasmJs variant. */
@Single
class NoopPendingFirmwareRecoverySource : PendingFirmwareRecoverySource {
    override val pending: Flow<PendingFirmwareRecovery?> = flowOf(null)

    override val lastBleNode: Flow<PendingFirmwareRecovery?> = flowOf(null)

    override suspend fun promoteLastBleNode(fullAddress: String) = Unit

    override suspend fun dismiss() = Unit

    override suspend fun clear() = Unit
}
