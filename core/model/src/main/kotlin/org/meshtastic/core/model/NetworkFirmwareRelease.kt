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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NetworkFirmwareRelease(
    @SerialName("id") val id: String = "",
    @SerialName("page_url") val pageUrl: String = "",
    @SerialName("release_notes") val releaseNotes: String = "",
    @SerialName("title") val title: String = "",
    @SerialName("zip_url") val zipUrl: String = "",
)

@Serializable
data class Releases(
    @SerialName("alpha") val alpha: List<NetworkFirmwareRelease> = listOf(),
    @SerialName("stable") val stable: List<NetworkFirmwareRelease> = listOf(),
)

@Serializable
data class NetworkFirmwareReleases(
    @SerialName("pullRequests") val pullRequests: List<NetworkFirmwareRelease> = listOf(),
    @SerialName("releases") val releases: Releases = Releases(),
)
