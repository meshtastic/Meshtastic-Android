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
package org.meshtastic.feature.node.detail

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.StringResource
import org.koin.core.annotation.Single
import org.meshtastic.core.common.di.ServiceScope
import org.meshtastic.core.model.getStringResFrom
import org.meshtastic.core.model.routingNakOrNull
import org.meshtastic.core.repository.RadioController
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.UiText
import org.meshtastic.core.resources.request_failed_from
import org.meshtastic.core.ui.util.SnackbarManager
import kotlin.time.Duration.Companion.minutes

/** Shows a snackbar when a routing NAK answers a node request, which otherwise just never gets its reply. */
@Single
class RequestNakWatcher(
    private val radioController: RadioController,
    private val serviceRepository: ServiceRepository,
    private val serviceScope: ServiceScope,
    private val snackbarManager: SnackbarManager,
    private val resolveUiText: suspend (UiText) -> String = { it.resolve() },
) {
    /** Runs [send] with a fresh packet id, watching for a NAK to that id from before the send until [WINDOW] after. */
    suspend fun watch(request: StringResource, longName: String, send: suspend (requestId: Int) -> Unit) {
        val requestId = radioController.generatePacketId()
        val watcher =
            serviceScope.launch(start = CoroutineStart.UNDISPATCHED) {
                val nak =
                    withTimeoutOrNull(WINDOW) {
                        serviceRepository.meshPacketFlow
                            .mapNotNull { it.routingNakOrNull() }
                            .first { it.requestId == requestId }
                    } ?: return@launch
                val reason = getStringResFrom(nak.routingError)
                snackbarManager.showSnackbar(
                    resolveUiText(UiText.Resource(Res.string.request_failed_from, request, longName, reason)),
                )
            }
        var sent = false
        try {
            send(requestId)
            sent = true
        } finally {
            if (!sent) watcher.cancel()
        }
    }

    companion object {
        /** Long enough for a multi-hop request to exhaust its retransmissions on the slowest presets. */
        val WINDOW = 5.minutes
    }
}
