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
package org.meshtastic.feature.map.tiles

import kotlinx.serialization.Serializable
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

/**
 * Whether a tile URL template is one we are willing to request.
 *
 * Hand-parsed rather than handed to a URL type, because there is no URL parser in common code and the rules here are
 * narrow: a scheme we accept, a host, no credentials, no fragment, and every placeholder accounted for. The template
 * belongs to the user, so the checks exist to keep a typo from being sent to a server as though it were a real request,
 * not to defend against the person who typed it.
 *
 * A private/link-local host blocklist is intentionally omitted: the user supplies the tile endpoint, requests carry no
 * Meshtastic-held credentials, and client-side tile GETs make that SSRF shape an accepted low-risk case.
 */
fun String.isValidTileUrlTemplate(requireHttps: Boolean): Boolean {
    val resolved = resolvedForValidation() ?: return false
    return resolved.hasAcceptedScheme(requireHttps) && resolved.hasUsableAuthority()
}

/**
 * The template with our placeholders filled in, or null if it still holds one we cannot fill.
 *
 * A leftover `{apiKey}` is the common case: half-finished, and it would be sent to the server with braces intact.
 */
private fun String.resolvedForValidation(): String? {
    val hasPlaceholders =
        contains("{z}", ignoreCase = true) && contains("{x}", ignoreCase = true) && contains("{y}", ignoreCase = true)

    val resolved =
        replace("{s}", "a", ignoreCase = true)
            .replace("{z}", "0", ignoreCase = true)
            .replace("{x}", "0", ignoreCase = true)
            .replace("{y}", "0", ignoreCase = true)

    return resolved.takeIf { hasPlaceholders && '{' !in it && '}' !in it && it.none(Char::isWhitespace) }
}

private fun String.hasAcceptedScheme(requireHttps: Boolean): Boolean {
    val scheme = substringBefore(SCHEME_SEPARATOR, missingDelimiterValue = "").lowercase()
    return if (requireHttps) scheme == "https" else scheme == "http" || scheme == "https"
}

/** A host, no fragment, and no credentials — those would be persisted in the clear and sent with every tile. */
private fun String.hasUsableAuthority(): Boolean {
    val afterScheme = substringAfter(SCHEME_SEPARATOR)
    val authority = afterScheme.substringBefore('/').substringBefore('?')
    return '#' !in afterScheme && '@' !in authority && authority.substringBefore(':').isNotBlank()
}

private const val SCHEME_SEPARATOR = "://"
