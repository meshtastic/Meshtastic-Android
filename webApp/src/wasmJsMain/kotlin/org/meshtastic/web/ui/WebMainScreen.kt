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
package org.meshtastic.web.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import com.github.terrakok.navigation3.browser.HierarchicalBrowserNavigation
import com.github.terrakok.navigation3.browser.buildBrowserHistoryFragment
import org.meshtastic.appshell.MeshtasticMainScreen
import org.meshtastic.core.navigation.MultiBackstack
import org.meshtastic.core.ui.viewmodel.UIViewModel
import org.meshtastic.web.navigation.unknownWebDestination
import org.meshtastic.web.navigation.webVisibleDestinations

/** The shared [MeshtasticMainScreen] with the browser's Back, without the Map tab, and tolerant of unknown keys. */
@Suppress("ViewModelForwarding")
@Composable
fun WebMainScreen(uiViewModel: UIViewModel, multiBackstack: MultiBackstack, modifier: Modifier = Modifier) {
    BrowserBack(multiBackstack)

    Surface(modifier = modifier.fillMaxSize()) {
        MeshtasticMainScreen(
            uiViewModel = uiViewModel,
            multiBackstack = multiBackstack,
            visibleDestinations = webVisibleDestinations,
            unknownDestination = ::unknownWebDestination,
        )
    }
}

/**
 * Browser Back pops the active tab's stack and the address bar names the screen on top, through navigation3-browser
 * until Compose Multiplatform wires browser history itself (CMP-8924). Hierarchical, not chronological: Back follows
 * the in-app stack, and at a tab's root it leaves the page as it would leave any site. Forward has nothing to replay.
 */
@Composable
private fun BrowserBack(multiBackstack: MultiBackstack) {
    if (LocalNavigationEventDispatcherOwner.current == null) return
    HierarchicalBrowserNavigation {
        // A tab's root keeps the bare address, which is also what the page loads with.
        multiBackstack.activeBackStack
            .takeIf { it.size > 1 }
            ?.lastOrNull()
            ?.let { key -> key::class.simpleName?.let { buildBrowserHistoryFragment(it) } }
    }
}
