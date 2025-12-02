/*
 * Copyright (c) 2025 Meshtastic LLC
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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonIgnoreUnknownKeys
data class NetworkDeviceHardware(
    @SerialName("activelySupported") val activelySupported: Boolean = false,
    @SerialName("architecture") val architecture: String = "",
    @SerialName("displayName") val displayName: String = "",
    @SerialName("hasInkHud") val hasInkHud: Boolean? = null,
    @SerialName("hasMui") val hasMui: Boolean? = null,
    @SerialName("hwModel") val hwModel: Int = 0,
    @SerialName("hwModelSlug") val hwModelSlug: String = "",
    @SerialName("images") val images: List<String>? = null,
    @SerialName("key") val key: String? = null,
    @SerialName("partitionScheme") val partitionScheme: String? = null,
    @SerialName("platformioTarget") val platformioTarget: String = "",
    @SerialName("requiresDfu") val requiresDfu: Boolean? = null,
    @SerialName("supportLevel") val supportLevel: Int? = null,
    @SerialName("tags") val tags: List<String>? = null,
    @SerialName("variant") val variant: String? = null,
)
