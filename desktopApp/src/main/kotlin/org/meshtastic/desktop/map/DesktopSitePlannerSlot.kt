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
package org.meshtastic.desktop.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.meshtastic.feature.map.SharedMapViewModel
import org.meshtastic.feature.map.component.SitePlannerBrowserSheet
import org.meshtastic.feature.map.component.toSitePlannerParams
import org.meshtastic.feature.map.maplibre.SitePlannerSession

/**
 * Site Planner on the desktop: the same configuration form the Android maps show, handed off to the browser.
 *
 * The Android hosts run the planner in an embedded WebView and take the coverage estimate back through a JavaScript
 * bridge, which lands it on the map as a GeoJSON layer. Desktop has no embedded browser: the JetBrains Runtime the app
 * builds against does ship JCEF, but jlink strips it out of the packaged runtime, and putting it back measured at
 * roughly three and a half times the size of the whole application.
 *
 * So the estimate is produced and viewed in the browser rather than drawn here. The form is not skipped — the
 * transmitter is still seeded from the connected radio and the map centre is still offered as a shortcut, so the
 * planner opens with everything already filled in.
 */
@Composable
fun DesktopSitePlannerSlot(session: SitePlannerSession) {
    val sharedViewModel: SharedMapViewModel = koinViewModel()

    val ourNode by sharedViewModel.ourNodeInfo.collectAsStateWithLifecycle()
    val channelSet by sharedViewModel.channelSet.collectAsStateWithLifecycle()
    val nodes by sharedViewModel.nodes.collectAsStateWithLifecycle()

    // A deep link names the node to plan for; a toolbar launch plans for whatever we are connected to.
    val subject = session.nodeNum?.let { num -> nodes.firstOrNull { it.num == num } } ?: ourNode

    SitePlannerBrowserSheet(
        initial = subject.toSitePlannerParams(channelSet),
        onDismiss = session.onDismiss,
        onUseNodeLocation =
        subject?.takeIf { it.validPosition != null }?.let { node -> { node.latitude to node.longitude } },
        onUseMapCenter = { session.mapCenter().let { it.latitude to it.longitude } },
    )
}
