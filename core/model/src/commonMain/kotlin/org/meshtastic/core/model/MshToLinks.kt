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
package org.meshtastic.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Root of the bundled `urls.json` file (imported as-is from the meshtastic/msh.to repo). */
@Serializable data class MshToUrlsFile(@SerialName("Routes") val routes: List<MshToRoute> = emptyList())

/** A single short-code route in `urls.json`. */
@Serializable
data class MshToRoute(
    @SerialName("ShortCode") val shortCode: String,
    @SerialName("OriginalUrl") val originalUrl: String,
    @SerialName("Description") val description: String? = null,
)

/**
 * Marketplace metadata from the app-maintained `marketplaces.json`. Keyed by marketplace identifier (e.g. `rokland`,
 * `aliexpress`).
 *
 * @param regions ISO 3166-1 alpha-2 shipping regions; empty = worldwide.
 * @param match how the marketplace identifier appears in a short code: `"prefix"` (e.g. `rokland-heltec-v3`) or
 *   `"suffix"` (e.g. `heltec-v3_aliexpress`).
 */
@Serializable data class MshToMarketplace(val regions: List<String> = emptyList(), val match: String)
