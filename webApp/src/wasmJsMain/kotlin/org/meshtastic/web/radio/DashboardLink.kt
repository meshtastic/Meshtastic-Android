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
package org.meshtastic.web.radio

import kotlinx.browser.localStorage
import kotlinx.browser.window
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The radio address of the node-kmp dashboard that served this page at `/app/`, its phone link on the same origin, or
 * null when the page came from anywhere else. The link names this browser with a client id kept in local storage, so
 * the node holds what the mesh sends while the page reloads.
 */
internal fun servingDashboardAddress(): String? {
    if (!window.location.pathname.startsWith(APP_PATH)) return null
    val scheme = if (window.location.protocol == "https:") "wss" else "ws"
    return "w$scheme://${window.location.host}$PHONE_PATH?client=${clientId()}"
}

@OptIn(ExperimentalUuidApi::class)
private fun clientId(): String {
    val stored = runCatching { localStorage.getItem(CLIENT_KEY) }.getOrNull()
    if (stored != null) return stored
    val made = Uuid.random().toHexString()
    // Storage can be off, as in a private window: the link then lasts only as long as this page.
    runCatching { localStorage.setItem(CLIENT_KEY, made) }
    return made
}

private const val APP_PATH = "/app/"
private const val PHONE_PATH = "/api/node/v1/phone"
private const val CLIENT_KEY = "meshtastic.phoneLinkClient"
