/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package org.meshtastic.core.model.util

import android.net.Uri

/**
 * Dispatches an incoming Meshtastic URI to the appropriate handler.
 *
 * @param uri The URI to handle.
 * @param onChannel Callback if the URI is a Channel Set.
 * @param onContact Callback if the URI is a Shared Contact.
 * @return True if the URI was handled (matched a supported path), false otherwise.
 */
fun handleMeshtasticUri(uri: Uri, onChannel: (Uri) -> Unit = {}, onContact: (Uri) -> Unit = {}): Boolean {
    val h = uri.host ?: ""
    val isCorrectHost =
        h.equals(MESHTASTIC_HOST, ignoreCase = true) || h.equals("www.$MESHTASTIC_HOST", ignoreCase = true)
    if (!isCorrectHost) return false

    val segments = uri.pathSegments
    return when {
        segments.any { it.equals("e", ignoreCase = true) } -> {
            onChannel(uri)
            true
        }
        segments.any { it.equals("v", ignoreCase = true) } -> {
            onContact(uri)
            true
        }
        else -> false
    }
}
