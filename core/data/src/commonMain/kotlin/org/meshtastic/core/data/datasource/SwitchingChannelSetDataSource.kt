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
package org.meshtastic.core.data.datasource

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import org.meshtastic.core.database.DatabaseProvider
import org.meshtastic.core.database.entity.ChannelSetEntity
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config

/**
 * Per-device channel-set storage backed by the current device's Room database.
 *
 * Replaces the former single global `channel_set.pb` DataStore. The channel set now switches with
 * [DatabaseProvider.currentDb], exactly like messages and nodes, so switching between devices can no longer render one
 * device's conversations against another device's channels (#4623).
 *
 * The whole [ChannelSet] proto is stored in one row (see [ChannelSetEntity]); mutations are read-modify-write, so they
 * are serialized through [writeMutex] to preserve the atomicity the old DataStore's `updateData {}` provided (the
 * handshake fires overlapping [updateChannelSettings] calls as it downloads channels one by one).
 */
@Single
class SwitchingChannelSetDataSource(
    private val dbManager: DatabaseProvider,
    private val dispatchers: CoroutineDispatchers,
) {
    private val writeMutex = Mutex()

    val channelSetFlow: Flow<ChannelSet> =
        dbManager.currentDb
            .flatMapLatest { db -> db.channelSetDao().observe() }
            .map { entity -> entity?.channelSet ?: ChannelSet() }
            .distinctUntilChanged()

    suspend fun clearChannelSet() {
        withContext(dispatchers.io) { dbManager.withDb { it.channelSetDao().clear() } }
    }

    /** Replaces all [ChannelSettings] in a single atomic operation. */
    suspend fun replaceAllSettings(settingsList: List<ChannelSettings>) {
        mutate { it.copy(settings = settingsList) }
    }

    /** Places [channel]'s settings at its index, resizing with blank channels to fill any gap (parity with legacy). */
    suspend fun updateChannelSettings(channel: Channel) {
        if (channel.role == Channel.Role.DISABLED) return
        mutate { current ->
            val settings = current.settings.toMutableList()
            while (settings.size <= channel.index) {
                settings.add(ChannelSettings())
            }
            settings[channel.index] = channel.settings ?: ChannelSettings()
            current.copy(settings = settings)
        }
    }

    suspend fun setLoraConfig(config: Config.LoRaConfig) {
        mutate { it.copy(lora_config = config) }
    }

    private suspend fun mutate(transform: (ChannelSet) -> ChannelSet) {
        withContext(dispatchers.io) {
            writeMutex.withLock {
                dbManager.withDb { db ->
                    val dao = db.channelSetDao()
                    val current = dao.get()?.channelSet ?: ChannelSet()
                    dao.upsert(ChannelSetEntity(channelSet = transform(current)))
                }
            }
        }
    }
}
