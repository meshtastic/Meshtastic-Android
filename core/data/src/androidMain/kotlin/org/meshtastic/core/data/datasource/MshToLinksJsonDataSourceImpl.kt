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

import android.app.Application
import co.touchlab.kermit.Logger
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.koin.core.annotation.Single
import org.meshtastic.core.model.MshToMarketplace
import org.meshtastic.core.model.MshToRoute
import org.meshtastic.core.model.MshToUrlsFile

@Single
class MshToLinksJsonDataSourceImpl(private val application: Application) : MshToLinksJsonDataSource {

    // Tolerant parser: tolerate extra fields/trailing data so a stale bundled file never crashes the import.
    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        exceptionsWithDebugInfo = false
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun loadRoutes(): List<MshToRoute> =
        runCatching { application.assets.open(URLS_ASSET).use { json.decodeFromStream<MshToUrlsFile>(it).routes } }
            .onFailure { Logger.w(it) { "Unable to load $URLS_ASSET for device links" } }
            .getOrDefault(emptyList())

    @OptIn(ExperimentalSerializationApi::class)
    override fun loadMarketplaces(): Map<String, MshToMarketplace> = runCatching {
        application.assets.open(MARKETPLACES_ASSET).use {
            json.decodeFromStream<Map<String, MshToMarketplace>>(it)
        }
    }
        .onFailure {
            Logger.w(it) { "Unable to load $MARKETPLACES_ASSET; marketplace links will not be region-filtered" }
        }
        .getOrDefault(emptyMap())

    private companion object {
        const val URLS_ASSET = "urls.json"
        const val MARKETPLACES_ASSET = "marketplaces.json"
    }
}
