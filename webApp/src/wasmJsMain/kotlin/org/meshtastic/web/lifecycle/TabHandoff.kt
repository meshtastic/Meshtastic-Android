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
@file:Suppress(
    // Every js("...") snippet references its Kotlin parameters inside the JS source, which detekt cannot see.
    "UnusedParameter",
)

package org.meshtastic.web.lifecycle

/**
 * Moves the database between tabs of this origin. The web database opens in one tab at a time, and a tab waiting for it
 * can only ask: [requestRelease] broadcasts the ask, and a tab holding the database that [honorReleaseRequests] hears
 * it, remembers for its next load that it gave the database away ([movedAway]), and reloads. Its old worker goes with
 * the old page, which frees the database for the tab that asked, first in line for it. A frozen or discarded holder
 * never answers, which the asking tab can only find out by waiting.
 */
internal object TabHandoff {
    private const val CHANNEL = "meshtastic-tab-handoff"
    private const val RELEASE = "release"
    private const val MOVED_KEY = "meshtastic-moved-to-another-tab"

    /**
     * True when this page load follows giving the database to another tab. Read once per load, so a manual reload
     * starts normally; the page must not touch the database while this is true or it would queue for it again.
     */
    val movedAway: Boolean = takeSessionFlag(MOVED_KEY)

    fun requestRelease() {
        postMessage(CHANNEL, RELEASE)
    }

    /**
     * Hands the database over when another tab asks for it; call [stop] on the returned handle when no longer holding.
     */
    fun honorReleaseRequests(): Handle {
        val channel =
            listen(CHANNEL, RELEASE) {
                setSessionFlag(MOVED_KEY)
                reloadPage()
            }
        return Handle { closeChannel(channel) }
    }

    fun interface Handle {
        fun stop()
    }
}

private fun postMessage(name: String, message: String) {
    js("{ const c = new BroadcastChannel(name); c.postMessage(message); c.close(); }")
}

private fun listen(name: String, message: String, onMessage: () -> Unit): JsAny = js(
    """{
    const c = new BroadcastChannel(name);
    c.onmessage = (e) => { if (e.data === message) onMessage(); };
    return c;
}""",
)

private fun closeChannel(channel: JsAny) {
    js("channel.close()")
}

private fun takeSessionFlag(key: String): Boolean = js(
    """{
    try {
        const set = sessionStorage.getItem(key) !== null;
        sessionStorage.removeItem(key);
        return set;
    } catch (e) {
        return false;
    }
}""",
)

private fun setSessionFlag(key: String) {
    js("{ try { sessionStorage.setItem(key, '1'); } catch (e) {} }")
}

private fun reloadPage() {
    js("window.location.reload()")
}
