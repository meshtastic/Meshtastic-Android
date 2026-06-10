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

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

/**
 * Response envelope of `GET /resource/deviceLinks` on the Meshtastic API. The server resolves meshtastic/msh.to's
 * catalog into fully-classified links (type + targets + regions), so the client only stores and filters them — no
 * client-side matching heuristic.
 */
@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonIgnoreUnknownKeys
data class NetworkDeviceLinksResponse(
    val version: Int = 1,
    val generatedAt: String? = null,
    val source: String? = null,
    val links: List<NetworkDeviceLink> = emptyList(),
)

/**
 * A single resolved device link from the Meshtastic API.
 *
 * @param shortCode msh.to short code, e.g. `rokland-t-deck-plus`.
 * @param url the user-facing `https://msh.to/<shortCode>` link (the retailer destination is intentionally not exposed).
 * @param description human-readable label.
 * @param type authoritative classification: [TYPE_INTERNAL], [TYPE_VENDOR], or [TYPE_MARKETPLACE].
 * @param targets device `platformioTarget`s this link is attached to; `null` = untriaged, empty = device-agnostic.
 * @param hwModels `hwModel` ints derived from [targets] server-side (parallel list).
 * @param marketplace retailer key (e.g. `rokland`) for marketplace links, else `null`.
 * @param regions ISO 3166-1 alpha-2 shipping regions; `null` = worldwide (no region filter).
 */
@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonIgnoreUnknownKeys
data class NetworkDeviceLink(
    val shortCode: String = "",
    val url: String = "",
    val description: String? = null,
    val type: String = TYPE_INTERNAL,
    val targets: List<String>? = null,
    val hwModels: List<Int>? = null,
    val marketplace: String? = null,
    val regions: List<String>? = null,
) {
    companion object {
        const val TYPE_INTERNAL = "internal"
        const val TYPE_VENDOR = "vendor"
        const val TYPE_MARKETPLACE = "marketplace"
    }
}

/**
 * Pure mapping to the cached domain model. Callers are expected to drop [TYPE_INTERNAL] links (GitHub, YouTube, …),
 * which never belong to a device's purchase section.
 */
fun NetworkDeviceLink.toDeviceLink(): DeviceLink = DeviceLink(
    shortCode = shortCode,
    description = description,
    isVendor = type == NetworkDeviceLink.TYPE_VENDOR,
    regions = regions,
    targets = targets,
)
