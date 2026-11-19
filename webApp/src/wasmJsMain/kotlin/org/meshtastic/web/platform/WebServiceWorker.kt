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

import kotlin.js.Promise

/**
 * Shows a notification through the active service worker registration, the only way Android Chrome shows one. False
 * when there is no active worker or the browser refuses it.
 */
internal fun showThroughWorker(
    title: String,
    body: String,
    tag: String?,
    silent: Boolean,
    renotify: Boolean,
    deepLink: String?,
): Promise<JsBoolean> = js(
    """(async () => {
        if (!('serviceWorker' in navigator)) return false;
        const registration = await navigator.serviceWorker.getRegistration();
        if (!registration || !registration.active) return false;
        const options = { body, silent, icon: 'icon-512.png', data: { deepLink } };
        if (tag) {
            options.tag = tag;
            options.renotify = renotify;
        }
        await registration.showNotification(title, options);
        return true;
    })().catch((e) => { console.warn('showNotification failed', e); return false; })""",
)

/** Closes the worker's notifications under [tag], or all of them when it is null. */
internal fun closeWorkerNotifications(tag: String?): Unit = js(
    """{
        if (!('serviceWorker' in navigator)) return;
        navigator.serviceWorker.getRegistration()
            .then((registration) => registration ? registration.getNotifications(tag ? { tag } : {}) : [])
            .then((notifications) => notifications.forEach((n) => n.close()))
            .catch(() => {});
    }""",
)

/**
 * Hands the service worker every same-origin file this page loaded. A first visit fetches the app before the worker
 * controls the page, so without this the next reload offline would find nothing cached.
 */
internal fun cacheLoadedResources(): Unit = js(
    """{
        if (!('serviceWorker' in navigator)) return;
        navigator.serviceWorker.ready.then((registration) => {
            const urls = performance.getEntriesByType('resource')
                .map((entry) => entry.name)
                .filter((name) => name.startsWith(location.origin));
            urls.push(new URL('icon-512.png', location.href).href, new URL('favicon.svg', location.href).href);
            if (registration.active) registration.active.postMessage({ type: 'cache-loaded', urls });
        }).catch(() => {});
    }""",
)
