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
package org.meshtastic.core.data.manager

import co.touchlab.kermit.Logger
import okio.ByteString
import org.jetbrains.compose.resources.StringResource
import org.koin.core.annotation.Single
import org.meshtastic.core.common.di.ServiceScope
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.util.isOtaStatusNotification
import org.meshtastic.core.repository.FirmwareUpdateStatusRepository
import org.meshtastic.core.repository.MeshNotificationManager
import org.meshtastic.core.repository.MqttManager
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.Notification
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioSessionContext
import org.meshtastic.core.repository.ServiceStateWriter
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.client_notification
import org.meshtastic.core.resources.duplicated_public_key_title
import org.meshtastic.core.resources.getStringSuspend
import org.meshtastic.core.resources.key_verification_final_title
import org.meshtastic.core.resources.key_verification_request_title
import org.meshtastic.core.resources.key_verification_title
import org.meshtastic.core.resources.low_entropy_key_title
import org.meshtastic.proto.ClientNotification
import org.meshtastic.sdk.MeshEvent

/**
 * Applies the [MeshEvent]s an SDK session delivers after Ready. Callers hold the session's lease, so each synchronous
 * effect lands in the session that raised it.
 */
@Single
class RadioSessionEventHandler(
    private val serviceStateWriter: ServiceStateWriter,
    private val mqttManager: MqttManager,
    private val nodeManager: NodeManager,
    private val serviceNotifications: MeshNotificationManager,
    private val firmwareUpdateStatusRepository: FirmwareUpdateStatusRepository,
    private val radioInterfaceService: RadioInterfaceService,
    private val scope: ServiceScope,
) {
    fun handle(event: MeshEvent, session: RadioSessionContext) {
        when (event) {
            is MeshEvent.Notification -> handleClientNotification(event.notification, session)

            is MeshEvent.MqttProxyMessage -> mqttManager.handleMqttProxyMessage(event.message)

            is MeshEvent.SecurityWarning.PublicKeyMismatch ->
                recordRefusedKey(event.node.raw, event.offeredKey, session)

            else -> Unit
        }
    }

    /**
     * The SDK keeps the first key it holds and drops the announcement, as firmware does. The app still flags the node
     * and keeps the offered key for the user to compare; an announcement without a usable key says nothing.
     */
    private fun recordRefusedKey(nodeNum: Int, offeredKey: ByteString, session: RadioSessionContext) {
        if (!offeredKey.isUsableKey()) return
        if (nodeManager.nodeDBbyNodeNum[nodeNum] == null) return
        nodeManager.updateNodeForSession(nodeNum, session) { node ->
            val held = listOfNotNull(node.publicKey, node.user.public_key).firstOrNull { it.isUsableKey() }
            if (held == null || held == offeredKey) node else node.copy(keyMatch = false, newPublicKey = offeredKey)
        }
    }

    private fun ByteString.isUsableKey(): Boolean = size == Node.PUBLIC_KEY_SIZE && this != Node.ERROR_BYTE_STRING

    private fun handleClientNotification(cn: ClientNotification, session: RadioSessionContext) {
        if (!serviceNotifications.suppressClientNotificationModal(cn)) serviceStateWriter.setClientNotification(cn)
        radioInterfaceService.launchSessionWork(
            scope = scope,
            session = session,
            onRejected = { Logger.d { "Skipping client alert from stale transport session" } },
        ) {
            dispatchClientNotification(cn)
        }
    }

    /** Test seam over the title lookup; compose resources cannot load on a headless JVM. */
    internal var titleResolver: suspend (StringResource) -> String = { getStringSuspend(it) }

    private suspend fun dispatchClientNotification(cn: ClientNotification) {
        if (cn.isOtaStatusNotification() && firmwareUpdateStatusRepository.status.value.isOtaUpdateActive) {
            Logger.i { "OTA status ClientNotification received; skipping duplicate generic alert" }
            return
        }
        val (resource, type) =
            when {
                cn.key_verification_number_inform != null -> Res.string.key_verification_title to Notification.Type.Info

                cn.key_verification_number_request != null ->
                    Res.string.key_verification_request_title to Notification.Type.Info

                cn.key_verification_final != null -> Res.string.key_verification_final_title to Notification.Type.Info

                cn.duplicated_public_key != null -> Res.string.duplicated_public_key_title to Notification.Type.Warning

                cn.low_entropy_key != null -> Res.string.low_entropy_key_title to Notification.Type.Warning

                else -> Res.string.client_notification to Notification.Type.Info
            }
        val title = titleResolver(resource)
        serviceNotifications.showClientNotification(cn, title, type)
    }
}
