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
package org.meshtastic.app.map.model

import kotlinx.serialization.Serializable
import java.net.URI
import kotlin.uuid.Uuid

@Serializable
data class CustomTileProviderConfig(
    val id: String = Uuid.random().toString(),
    val name: String,
    val urlTemplate: String,
    val localUri: String? = null,
) {
    val isLocal: Boolean
        get() = localUri != null

    fun normalized(): CustomTileProviderConfig = copy(name = name.trim(), urlTemplate = urlTemplate.trim())
}

internal fun String.isValidTileUrlTemplate(requireHttps: Boolean): Boolean {
    val hasPlaceholders =
        contains("{z}", ignoreCase = true) && contains("{x}", ignoreCase = true) && contains("{y}", ignoreCase = true)
    val normalizedForParsing =
        replace("{s}", "a", ignoreCase = true)
            .replace("{z}", "0", ignoreCase = true)
            .replace("{x}", "0", ignoreCase = true)
            .replace("{y}", "0", ignoreCase = true)
    val parsed = runCatching { URI(normalizedForParsing) }.getOrNull() ?: return false
    val scheme = parsed.scheme?.lowercase()
    val hasValidAuthority = !parsed.host.isNullOrBlank() && parsed.rawUserInfo == null
    val hasValidScheme = if (requireHttps) scheme == "https" else scheme == "http" || scheme == "https"
    // A private/link-local host blocklist is intentionally omitted: the user supplies the tile endpoint, requests carry
    // no Meshtastic/server-held credentials, and client-side tile GETs make that SSRF shape an accepted low-risk case.
    return hasPlaceholders && parsed.rawFragment == null && hasValidAuthority && hasValidScheme
}
