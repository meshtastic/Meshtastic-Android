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
package org.meshtastic.core.data.radio

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.MeshActivity
import org.meshtastic.core.repository.PacketHandler
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.QueueStatus
import org.meshtastic.proto.ToRadio

/**
 * SDK-backed [PacketHandler] that sends packets through the active [RadioClient].
 *
 * Replaces the monolithic [PacketHandlerImpl] which routed through the old
 * `RadioInterfaceService.sendToRadio()` pipeline. This thin implementation only supports the
 * `sendToRadio` surface needed by MQTT, XModem, and History managers.
 *
 * Queue management (QueueStatus, packet ordering) is handled internally by the SDK engine.
 */
@Single(binds = [PacketHandler::class])
class SdkPacketHandler(
    private val accessor: RadioClientAccessor,
    private val serviceRepository: ServiceRepository,
    private val dispatchers: CoroutineDispatchers,
) : PacketHandler {

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.io)

    override fun sendToRadio(p: ToRadio) {
        val packet = p.packet
        if (packet != null) {
            // Regular MeshPacket — route through the tracked send path.
            sendToRadio(packet)
            return
        }
        // Non-packet ToRadio (mqttClientProxyMessage, xmodemPacket) — send as raw frame.
        val client = accessor.client.value ?: run {
            Logger.w { "SdkPacketHandler: no client, dropping non-packet ToRadio" }
            return
        }
        scope.launch {
            runCatching { client.sendRaw(p) }
                .onFailure { e -> Logger.w(e) { "SdkPacketHandler: sendRaw(ToRadio) failed" } }
        }
    }

    override fun sendToRadio(packet: MeshPacket) {
        val client = accessor.client.value ?: run {
            Logger.w { "SdkPacketHandler: no client, dropping packet id=${packet.id}" }
            return
        }
        client.send(packet)
        serviceRepository.emitMeshActivity(MeshActivity.Send)
    }

    override suspend fun sendToRadioAndAwait(packet: MeshPacket): Boolean {
        val client = accessor.client.value ?: return false
        return runCatching { client.send(packet) }.isSuccess
    }

    override fun handleQueueStatus(queueStatus: QueueStatus) {
        // Queue management is internal to the SDK engine; no-op.
    }

    override fun removeResponse(dataRequestId: Int, complete: Boolean) {
        // Response tracking is internal to the SDK engine; no-op.
    }

    override fun stopPacketQueue() {
        // Queue management is internal to the SDK engine; no-op.
    }
}
