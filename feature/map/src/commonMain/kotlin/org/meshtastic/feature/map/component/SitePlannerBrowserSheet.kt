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
package org.meshtastic.feature.map.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalUriHandler

/** The hosted Site Planner. One definition, shared by the embedded host and the browser hand-off. */
const val SITE_PLANNER_URL: String = "https://site.meshtastic.org"

/**
 * The Site Planner as a form that hands the finished parameters to the system browser.
 *
 * Used where there is no embedded browser to run the planner in. The whole configuration step is the same
 * [SitePlannerSheet] the Android hosts show, so the transmitter is still seeded from the connected radio and the
 * shortcuts still work; only the last step differs — instead of loading the planner in place, the parameters are
 * encoded into its URL and opened outside the app.
 *
 * The coverage estimate therefore stays in the browser rather than returning as a map layer. Bringing it back needs a
 * JavaScript bridge, which needs an embedded browser: on Desktop that meant bundling Chromium, which measured at
 * roughly three and a half times the size of the whole application.
 */
@Composable
fun SitePlannerBrowserSheet(
    initial: SitePlannerParams,
    onDismiss: () -> Unit,
    onUseNodeLocation: (() -> Pair<Double, Double>)? = null,
    onUseMapCenter: (() -> Pair<Double, Double>)? = null,
) {
    val uriHandler = LocalUriHandler.current
    // Held here rather than passed straight through: the sheet re-seeds its coordinate fields from `initial`, so a
    // location shortcut only shows up if the caller actually moves the value it was given.
    var params by remember(initial) { mutableStateOf(initial) }

    SitePlannerSheet(
        initial = params,
        onSubmit = { submitted ->
            uriHandler.openUri(submitted.toQueryUrl(SITE_PLANNER_URL))
            onDismiss()
        },
        onDismiss = onDismiss,
        onUseNodeLocation =
        onUseNodeLocation?.let { node ->
            {
                val (latitude, longitude) = node()
                params = params.copy(latitude = latitude, longitude = longitude)
            }
        },
        onUseMapCenter =
        onUseMapCenter?.let { center ->
            {
                val (latitude, longitude) = center()
                params = params.copy(latitude = latitude, longitude = longitude)
            }
        },
    )
}
