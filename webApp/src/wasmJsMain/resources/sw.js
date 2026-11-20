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

// Meshtastic Web's service worker: shows notifications and routes their clicks into the app, and keeps the app shell
// cached so a reload works offline. Served from the distribution root, so its scope is the directory the app is in.

const CACHE = "meshtastic-shell-v1";
const SCOPE = new URL(self.registration.scope);
const INDEX = new URL("./", SCOPE).href;
const OPEN_PARAM = "open";

// webpack names the Kotlin/Wasm modules by content hash, so a cached copy is valid forever.
const HASHED = /\/[0-9a-f]{20}\.(wasm|js|mjs)$/;
// Scripts webpack does not hash keep their names across deploys, so they come from the network first, as the page
// does: a cached webApp.js would load the previous build's modules once more after every deploy.
const SCRIPT = /\.(js|mjs)$/;
const SHELL = /\.(wasm|js|mjs|css|png|svg|ico|webmanifest|json|cvr|xml|ttf|otf|woff2?)$/;

self.addEventListener("install", (event) => {
    event.waitUntil(
        caches.open(CACHE).then((cache) => cache.addAll([INDEX, "webApp.js", "manifest.webmanifest"]))
            .catch((e) => console.warn("Meshtastic SW: precache failed", e))
            .then(() => self.skipWaiting()),
    );
});

self.addEventListener("activate", (event) => {
    event.waitUntil(
        caches.keys()
            .then((keys) => Promise.all(keys.filter((k) => k.startsWith("meshtastic-shell-") && k !== CACHE)
                .map((k) => caches.delete(k))))
            .then(() => self.clients.claim()),
    );
});

/** Same-origin static files under the scope; the radio's HTTP API, WebSockets and anything else pass straight by. */
function cacheable(url) {
    if (url.origin !== SCOPE.origin || !url.href.startsWith(SCOPE.href)) return false;
    const path = url.pathname.slice(SCOPE.pathname.length);
    if (path.startsWith("api/") || path.startsWith("json/") || path === "sw.js") return false;
    return SHELL.test(url.pathname);
}

/** The app's own page: the scope itself or its index.html, whatever the query. */
function isIndex(url) {
    return url.origin === SCOPE.origin &&
        (url.pathname === SCOPE.pathname || url.pathname === SCOPE.pathname + "index.html");
}

function store(cache, request, response) {
    if (response.ok && response.type === "basic") return cache.put(request, response.clone()).then(() => response);
    return response;
}

self.addEventListener("fetch", (event) => {
    const request = event.request;
    if (request.method !== "GET" || request.headers.has("range")) return;
    const url = new URL(request.url);

    // Any other page on the origin, such as a host's sign-in form, passes by and is never cached.
    if (request.mode === "navigate") {
        if (isIndex(url)) {
            event.respondWith(networkFirstIndex(request));
            event.waitUntil(pruneHashed());
        }
        return;
    }
    if (!cacheable(url)) return;
    if (HASHED.test(url.pathname)) event.respondWith(cacheFirst(request));
    else if (SCRIPT.test(url.pathname)) event.respondWith(networkFirst(request, request));
    else event.respondWith(staleWhileRevalidate(event, request));
});

/** The page itself: the network's copy when there is one, so a deploy shows on the next load. */
function networkFirstIndex(request) {
    return networkFirst(request, INDEX);
}

/** The network's copy, kept under [key]; the cached one only when the network fails, as offline. */
async function networkFirst(request, key) {
    const cache = await caches.open(CACHE);
    try {
        const response = await fetch(request);
        if (response.ok) await cache.put(key, response.clone());
        return response;
    } catch (e) {
        return (await cache.match(key, { ignoreSearch: true })) || Response.error();
    }
}

async function cacheFirst(request) {
    const cache = await caches.open(CACHE);
    return (await cache.match(request)) || store(cache, request, await fetch(request));
}

async function staleWhileRevalidate(event, request) {
    const cache = await caches.open(CACHE);
    const cached = await cache.match(request, { ignoreSearch: true });
    const network = fetch(request).then((response) => store(cache, request, response));
    if (cached) {
        event.waitUntil(network.catch(() => undefined));
        return cached;
    }
    return network;
}

/**
 * Drops content-hashed modules no cached script names any more, so each deploy does not leave its old wasm behind.
 * Runs as a page loads: the scripts it is about to run are the cached ones, which already name the hashes they need.
 */
async function pruneHashed() {
    const cache = await caches.open(CACHE);
    const requests = await cache.keys();
    const referenced = new Set();
    for (const request of requests) {
        const path = new URL(request.url).pathname;
        if (!/\.(js|mjs)$/.test(path) || HASHED.test(path)) continue;
        const text = await (await cache.match(request)).text();
        for (const name of text.match(/[0-9a-f]{20}\.(wasm|js|mjs)/g) || []) referenced.add(name);
    }
    if (referenced.size === 0) return;
    await Promise.all(requests.filter((request) => {
        const path = new URL(request.url).pathname;
        return HASHED.test(path) && !referenced.has(path.slice(path.lastIndexOf("/") + 1));
    }).map((request) => cache.delete(request)));
}

self.addEventListener("message", (event) => {
    const data = event.data || {};
    // The page lists what it loaded before this worker controlled it, which a first visit fetched past the cache.
    if (data.type === "cache-loaded" && Array.isArray(data.urls)) {
        event.waitUntil(caches.open(CACHE).then((cache) => Promise.all(data.urls
            .map((href) => new URL(href))
            .filter(cacheable)
            .map((url) => cache.match(url.href).then((hit) => hit || cache.add(url.href).catch(() => undefined))))));
    }
});

self.addEventListener("notificationclick", (event) => {
    event.notification.close();
    const deepLink = (event.notification.data && event.notification.data.deepLink) || null;
    event.waitUntil(openApp(deepLink));
});

/** Focuses a window of the app and hands it [deepLink], or opens one that reads it from its address. */
async function openApp(deepLink) {
    const windows = await self.clients.matchAll({ type: "window", includeUncontrolled: true });
    const client = windows.find((c) => c.url.startsWith(SCOPE.href));
    if (client) {
        await client.focus().catch(() => undefined);
        if (deepLink) client.postMessage({ type: "meshtastic-open", deepLink });
        return;
    }
    const url = new URL(INDEX);
    if (deepLink) url.searchParams.set(OPEN_PARAM, deepLink);
    await self.clients.openWindow(url.href);
}
