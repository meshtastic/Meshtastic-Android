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
package org.meshtastic.core.ui.component

import androidx.compose.runtime.Composable
import org.meshtastic.core.ui.viewmodel.UIViewModel

/**
 * Common application-level setup for all Meshtastic platforms (Android, Desktop, etc.).
 *
 * This component encapsulates headless global UI logic that must reside at the root of the application hierarchy. It
 * manages:
 * - Shared system dialogs (e.g. contact/channel import)
 * - Global version and firmware checks
 * - System-wide alerts and snackbar hosts
 * - Deep link navigation interception logic
 *
 * Platform hosts should invoke this near the root before rendering `MeshtasticNavDisplay`.
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
