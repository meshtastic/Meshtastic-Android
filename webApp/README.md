# `:webApp` - Meshtastic Web

The Compose Multiplatform browser host, built with Kotlin/Wasm (`wasmJs`). It runs the same `core:*` modules and feature screens as the Android and desktop apps: connections, messaging, nodes, settings, onboarding, Help & Documentation, Local Mesh Discovery, mPWRD-OS Wi-Fi provisioning over Web Bluetooth, and firmware updates for nRF52 and RP2040 radios over USB (the `.uf2` is saved with the browser's save picker). Firmware over Bluetooth and ESP32 updates are not offered; ESP32 radios update through flasher.meshtastic.org. There is no map: maplibre-compose publishes no wasmJs variant.

## The web build is opt-in

The wasmJs target and this module exist only when the build is run with `-Pmeshtastic.web=true`. A web build must also turn Isolated Projects off, because KGP's wasm npm resolver configures the root project from each wasmJs project; the build stops with a message saying so if you forget. Every other build keeps Isolated Projects on and has no wasm tasks.

```shell
WEB="-Pmeshtastic.web=true -Dorg.gradle.isolated-projects=false"

# Dev server with live reload, on http://localhost:8080
./gradlew :webApp:wasmJsBrowserDevelopmentRun $WEB

# Static development build in webApp/build/dist/wasmJs/developmentExecutable/
./gradlew :webApp:wasmJsBrowserDevelopmentExecutableDistribution $WEB

# Static production build in webApp/build/dist/wasmJs/productionExecutable/
./gradlew :webApp:wasmJsBrowserDistribution $WEB

# The wasmJs unit tests, in headless Chrome
./gradlew wasmJsBrowserTest $WEB
```

The production build runs Binaryen's `wasm-opt`, which takes 40 minutes or more. To check that the production executable links, which is where Kotlin/Wasm IR failures show up, compile it without optimizing: `./gradlew :webApp:compileProductionExecutableKotlinWasmJs $WEB`.

`WebBuildConfig.IS_DEBUG` is false unless you pass `-Pweb.release=false`; the development and production executables link the same compilation.

## Hosting

The distribution directory is a static site: serve it from any web server.

- Serve `.wasm` files as `application/wasm`. Browsers refuse to stream-compile a module sent with any other type.
- Serve it from a secure context, https or `localhost`. The database lives in the Origin Private File System, which browsers offer only to secure contexts; Web Bluetooth and Web Serial need one too.
- One tab at a time holds the database. A second tab waits, and offers to take the database over.
- Some private windows offer no usable OPFS, and the database cannot open there.
- Serve `sw.js` from the same directory as `index.html`; its scope is that directory, so the app works at a site root, under a path, or from node-headless's client directory. It caches the app shell for offline reloads (network first for the page, stale-while-revalidate for the scripts, cache first for the content-hashed `.wasm`) and leaves `api/`, `json/` and every other request alone. After a rebuild, the first reload can still run the cached scripts; reload again, or tick "Update on reload" in DevTools while developing.

## Installed app

The page is an installable PWA: `manifest.webmanifest` takes its icons from the Android launcher art (`copyPwaIcons`), and `sw.js` shows the notifications, which Android Chrome only shows from a service worker. A notification click focuses the app, or opens it at `?open=<path>`, and goes to the screen Android's notification opens (`NotificationDeepLinks`); the map has no web build, so a waypoint notification opens the app where it was. The installed icon carries the unread message count through the Badging API. The page asks for persistent storage once the database opens, and again on the next tap if the browser says no, so the OPFS database is not evicted. Emoji draw with Noto Color Emoji's COLRv1 build (`NotoColorEmoji.ttf`, SIL Open Font License in `NotoColorEmoji-OFL.txt`), preloaded as a fallback after the first frame, since Compose on web never sees the browser's fonts. The compass reads `deviceorientationabsolute` on Chrome and `webkitCompassHeading` on Safari, which asks for motion access on the next tap; headings are magnetic, since a browser has no declination model.

## Browser support

Radios connect over Web Bluetooth and Web Serial, which only Chromium-based browsers implement: Chrome, Edge and Opera. Bluetooth works there on Windows, macOS, ChromeOS and Android, and on Linux with Chrome's experimental web platform features flag on; Web Serial works on desktop. Firefox and Safari implement neither, so they can open the app but not connect to a radio. Discovery goes through the browser's device picker rather than a background scan, because Chrome ships the Web Bluetooth scanning API only behind a flag. TCP radios are not offered: a browser cannot open raw sockets.

## Tests

`meshtastic.kmp.web` runs each web module's wasmJs tests in headless Chrome through Karma, until Kotlin 2.5's Playwright test DSL (KT-87082). Node.js cannot run them: nearly every module links Compose through `core:model`, and skiko's wasm loader works only in a browser. Karma finds Chrome on the `PATH` or through `CHROME_BIN`. A module whose tests cannot pass there sets `meshtasticWeb { browserTests = false }`, and its wasmJs tests only compile. Tests that need a type with no browser equivalent live in `nonWebTest`. This module, and the `wasmJsMain` interop with Web Bluetooth, Web Serial, OPFS, the Permissions API, notifications, the service worker and orientation events, have no automated tests.
