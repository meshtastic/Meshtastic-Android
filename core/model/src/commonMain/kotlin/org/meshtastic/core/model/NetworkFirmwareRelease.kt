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

/**
 * The API also returns a `pullRequests` array of open firmware PRs. It is deliberately not modelled: nothing in the app
 * offers PR builds, and the list turns over several times a day, so carrying it only churned the bundled asset. The
 * shared [kotlinx.serialization.json.Json] is configured with `ignoreUnknownKeys`, so responses still decode.
 */
@Serializable data class NetworkFirmwareReleases(@SerialName("releases") val releases: Releases = Releases())

/**
 * The nightly-preview pointer published to `firmware-nightly/index.json` on meshtastic.github.io. Unlike the release
 * channels above it is not served by api.meshtastic.org, and only [version] is guaranteed present — [id] and [title]
 * are derived when absent, matching the web flasher's parsing.
 */
@Serializable
data class NetworkFirmwareNightly(
    @SerialName("version") val version: String = "",
    @SerialName("id") val id: String? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("commit") val commit: String? = null,
)

/**
 * Normalizes the nightly pointer into the common release shape, or null when the pointer carries no usable version.
 * Nightly artifacts are served per-file from the fixed `firmware-nightly/` folder, so there is no release zip.
 */
fun NetworkFirmwareNightly.asFirmwareRelease(): NetworkFirmwareRelease? {
    val resolvedId = id?.takeIf { it.isNotBlank() } ?: version.takeIf { it.isNotBlank() }?.let { "v$it" }
    resolvedId ?: return null
    val resolvedVersion = version.ifBlank { resolvedId.removePrefix("v") }
    return NetworkFirmwareRelease(
        id = resolvedId,
        pageUrl = commit?.takeIf { it.isNotBlank() }?.let { "https://github.com/meshtastic/firmware/commit/$it" } ?: "",
        releaseNotes = "",
        title = title?.takeIf { it.isNotBlank() } ?: "Meshtastic Firmware $resolvedVersion Nightly",
        zipUrl = "",
    )
}
