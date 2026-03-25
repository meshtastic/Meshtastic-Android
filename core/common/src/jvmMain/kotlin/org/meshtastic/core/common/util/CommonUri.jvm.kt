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
package org.meshtastic.core.common.util

import java.net.URI

actual class CommonUri(private val uri: URI) {
    private val queryParameters: Map<String, List<String>> by lazy { parseQueryParameters(uri.rawQuery) }

    actual val host: String?
        get() = uri.host

    actual val fragment: String?
        get() = uri.fragment

    actual val pathSegments: List<String>
        get() = uri.path.orEmpty().split('/').filter { it.isNotBlank() }

    actual fun getQueryParameter(key: String): String? = queryParameters[key]?.firstOrNull()

    actual fun getBooleanQueryParameter(key: String, defaultValue: Boolean): Boolean {
        val value = getQueryParameter(key) ?: return defaultValue
        return value != "false" && value != "0"
    }

    actual override fun toString(): String = uri.toString()

    actual companion object {
        actual fun parse(uriString: String): CommonUri = CommonUri(URI(uriString))
    }

    fun toUri(): URI = uri
}

actual fun CommonUri.toPlatformUri(): Any = this.toUri()
