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
package org.meshtastic.core.data.repository

import co.touchlab.kermit.Logger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.safeCatching
import org.meshtastic.core.data.datasource.BundledAssetReader
import org.meshtastic.core.data.datasource.decode
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.EventFirmwareEdition
import org.meshtastic.core.model.EventFirmwareResponse
import org.meshtastic.core.repository.EventFirmwareRepository
import kotlin.concurrent.Volatile

/**
 * ponytail: bundled-only — the snapshot is a handful of static records, so it's decoded once into memory with no DB or
 * network refresh (unlike [DeviceLinkRepositoryImpl]). Add a `/resource/eventFirmware` refresh when that API ships.
 */
@Single
class EventFirmwareRepositoryImpl(
    private val assetReader: BundledAssetReader,
    private val json: Json,
    private val dispatchers: CoroutineDispatchers,
) : EventFirmwareRepository {

    private val mutex = Mutex()

    @Volatile private var cache: Map<String, EventFirmwareEdition>? = null

    override suspend fun getEdition(editionName: String): EventFirmwareEdition? = load()[editionName]

    /**
     * Decodes the bundled snapshot once, keyed by edition name. Empty (not crashing) if the asset is absent/malformed.
     */
    private suspend fun load(): Map<String, EventFirmwareEdition> {
        cache?.let {
            return it
        }
        return mutex.withLock {
            cache
                ?: withContext(dispatchers.io) {
                    safeCatching { assetReader.decode<EventFirmwareResponse>(ASSET_NAME, json)?.editions.orEmpty() }
                        .onFailure { e -> Logger.w(e) { "EventFirmwareRepository: failed to read bundled JSON" } }
                        .getOrDefault(emptyList())
                        .associateBy { it.edition }
                }
                    .also { cache = it }
        }
    }

    private companion object {
        private const val ASSET_NAME = "event_firmware.json"
    }
}
