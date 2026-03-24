/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package org.meshtastic.core.ui.component

import androidx.compose.runtime.Composable
import org.meshtastic.core.ui.viewmodel.UIViewModel

/**
 * Encapsulates the headless, global UI components (dialogs, version checks, traceroute alerts) that need to be active
 * across all platforms at the root of the application hierarchy.
 *
 * This deduplicates the setup boilerplate from Android's MainScreen and DesktopMainScreen.
 */
@Composable
fun MeshtasticCommonAppSetup(
    uiViewModel: UIViewModel,
    onNavigateToTracerouteMap: (destinationNodeNum: Int, requestId: Int, logUuid: String?) -> Unit,
) {
    SharedDialogs(uiViewModel = uiViewModel)
    FirmwareVersionCheck(viewModel = uiViewModel)
    AlertHost(alertManager = uiViewModel.alertManager)
    TracerouteAlertHandler(uiViewModel = uiViewModel, onNavigateToMap = onNavigateToTracerouteMap)
}
