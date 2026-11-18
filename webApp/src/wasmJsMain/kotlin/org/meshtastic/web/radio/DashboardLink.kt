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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import org.meshtastic.core.network.NodeDashboardClient
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The radio address of the node-kmp host that served this page, its phone link on the same origin, or null when the
 * page came from anywhere else. A host serves its dashboard API beside the page, so the page asks it.
 *
 * A link the host printed carries the token in its fragment, `#token=…`: the page signs in with it and drops it from
 * the address. A host that needs the token and has no session for the page sends it to the sign-in form instead. The
 * phone link names this browser with a client id kept in local storage, so the node holds what the mesh sends while the
 * page reloads.
 */
internal suspend fun servingDashboardAddress(dashboard: NodeDashboardClient): String? {
    val origin = window.location.origin
    fragmentToken()?.let { token ->
        dashboard.post(origin, "session", "{\"token\":${JsonPrimitive(token)}}")
        window.history.replaceState(null, "", window.location.pathname + window.location.search)
    }
    val session = dashboard.get(origin, "session")?.let(::parseObject)
    val signedOut = (session?.get("authorized") as? JsonPrimitive)?.booleanOrNull == false
    if (signedOut) window.location.href = SIGN_IN
    if (session == null || signedOut) return null
    val scheme = if (window.location.protocol == "https:") "wss" else "ws"
    return "w$scheme://${window.location.host}$PHONE_PATH?client=${clientId()}"
}

private fun fragmentToken(): String? = window.location.hash
    .removePrefix("#")
    .split('&')
    .firstOrNull { it.startsWith("token=") }
    ?.removePrefix("token=")
    ?.takeIf { it.isNotEmpty() }

// A body that is not a JSON object reads as no host.
@Suppress("SwallowedException")
private fun parseObject(body: String): JsonObject? = try {
    Json.parseToJsonElement(body) as? JsonObject
} catch (e: IllegalArgumentException) {
    null
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

private const val PHONE_PATH = "/api/node/v1/phone"
private const val SIGN_IN = "/signin?next=%2F"
private const val CLIENT_KEY = "meshtastic.phoneLinkClient"
