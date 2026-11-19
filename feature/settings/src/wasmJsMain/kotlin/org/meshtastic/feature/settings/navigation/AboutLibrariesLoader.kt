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
package org.meshtastic.feature.settings.navigation

import kotlinx.coroutines.await
import kotlin.js.Promise

// webApp's build exports its wasmJs dependency graph into the distribution root, beside index.html.
actual suspend fun getAboutLibrariesJson(): String = fetchAboutLibrariesJson().await<JsString>().toString()

// A failed fetch resolves to "", which the screen shows as an empty list, as the JVM loader does for a missing
// resource.
private fun fetchAboutLibrariesJson(): Promise<JsString> =
    js("fetch('aboutlibraries.json').then((r) => (r.ok ? r.text() : '')).catch(() => '')")
