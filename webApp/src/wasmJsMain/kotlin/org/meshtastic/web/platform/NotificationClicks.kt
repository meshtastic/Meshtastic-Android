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

package org.meshtastic.web.platform

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Destinations the user opened from a notification, as `NotificationDeepLinks` paths: from a click `sw.js` forwarded to
 * this tab, from the `?open=` address it opened a new window at, or from a page-level notification's own click.
 */
internal object NotificationClicks {
    private val pending = Channel<String>(Channel.BUFFERED)

    val deepLinks: Flow<String> = pending.receiveAsFlow()

    /** Takes the `?open=` destination this page was opened at and starts forwarding clicks. Call once, at startup. */
    fun start() {
        takeOpenParameter()?.let(::open)
        listenForWorkerClicks(::open)
    }

    fun open(deepLink: String) {
        pending.trySend(deepLink)
    }
}

/** The `open` query parameter `sw.js` opens a window with, removed from the address once read. */
private fun takeOpenParameter(): String? = js(
    """(() => {
        const url = new URL(location.href);
        const deepLink = url.searchParams.get('open');
        if (deepLink === null) return null;
        url.searchParams.delete('open');
        history.replaceState(history.state, '', url.pathname + url.search + url.hash);
        return deepLink;
    })()""",
)

private fun listenForWorkerClicks(onOpen: (String) -> Unit): Unit = js(
    """{
        if (!('serviceWorker' in navigator)) return;
        navigator.serviceWorker.addEventListener('message', (event) => {
            const data = event.data || {};
            if (data.type === 'meshtastic-open' && typeof data.deepLink === 'string') onOpen(data.deepLink);
        });
    }""",
)
