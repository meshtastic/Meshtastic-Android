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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.touchlab.kermit.Logger
import kotlinx.coroutines.launch
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.okay
import org.meshtastic.core.resources.traceroute
import org.meshtastic.core.resources.view_on_map
import org.meshtastic.core.ui.theme.StatusColors.StatusGreen
import org.meshtastic.core.ui.theme.StatusColors.StatusOrange
import org.meshtastic.core.ui.theme.StatusColors.StatusYellow
import org.meshtastic.core.ui.util.annotateTraceroute
import org.meshtastic.core.ui.util.toMessageRes
import org.meshtastic.core.ui.viewmodel.UIViewModel

/**
 * Handles the display of the traceroute alert when a response is received. Consolidates the side effect logic from the
 * main application screens into common code.
 */
@Composable
fun TracerouteAlertHandler(
    uiViewModel: UIViewModel,
    onNavigateToMap: (destinationNodeNum: Int, requestId: Int, logUuid: String?) -> Unit,
) {
    val traceRouteResponse by uiViewModel.tracerouteResponse.collectAsStateWithLifecycle(null)
    var dismissedTracerouteRequestId by remember { mutableStateOf<Int?>(null) }
    val colorScheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    LaunchedEffect(traceRouteResponse, dismissedTracerouteRequestId) {
        val response = traceRouteResponse
        if (response != null && response.requestId != dismissedTracerouteRequestId) {
            uiViewModel.showAlert(
                titleRes = Res.string.traceroute,
                composableMessage = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Text(
                            text =
                            annotateTraceroute(
                                response.message,
                                statusGreen = colorScheme.StatusGreen,
                                statusYellow = colorScheme.StatusYellow,
                                statusOrange = colorScheme.StatusOrange,
                            ),
                        )
                    }
                },
                confirmTextRes = Res.string.view_on_map,
                onConfirm = {
                    val availability =
                        uiViewModel.tracerouteMapAvailability(
                            forwardRoute = response.forwardRoute,
                            returnRoute = response.returnRoute,
                        )
                    val errorRes = availability.toMessageRes()
                    if (errorRes == null) {
                        dismissedTracerouteRequestId = response.requestId
                        onNavigateToMap(response.destinationNodeNum, response.requestId, response.logUuid)
                    } else {
                        uiViewModel.clearTracerouteResponse()
                        // Post the error alert after the current alert is dismissed to avoid
                        // the wrapping dismissAlert() in AlertManager immediately clearing it.
                        @Suppress("TooGenericExceptionCaught")
                        scope.launch {
                            try {
                                uiViewModel.showAlert(titleRes = Res.string.traceroute, messageRes = errorRes)
                            } catch (e: Exception) {
                                Logger.e(e) { "[TracerouteAlertHandler] Failed to show error alert" }
                            }
                        }
                    }
                },
                dismissTextRes = Res.string.okay,
                onDismiss = {
                    uiViewModel.clearTracerouteResponse()
                    dismissedTracerouteRequestId = null
                },
            )
        }
    }
}
