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
package org.meshtastic.core.ui.util

import org.meshtastic.core.common.util.CommonUri

fun parseDeepLinkOrInvalid(
    uriString: String,
    onHandleDeepLink: (CommonUri, onInvalid: () -> Unit) -> Unit,
    onInvalid: () -> Unit,
) = parseDeepLinkOrInvalid(uriString, onHandleDeepLink, onInvalid, CommonUri::parse)

internal fun parseDeepLinkOrInvalid(
    uriString: String,
    onHandleDeepLink: (CommonUri, onInvalid: () -> Unit) -> Unit,
    onInvalid: () -> Unit,
    parseUri: (String) -> CommonUri,
) {
    val uri =
        try {
            parseUri(uriString)
        } catch (_: IllegalArgumentException) {
            null
        }
    if (uri == null) {
        onInvalid()
    } else {
        onHandleDeepLink(uri, onInvalid)
    }
}
