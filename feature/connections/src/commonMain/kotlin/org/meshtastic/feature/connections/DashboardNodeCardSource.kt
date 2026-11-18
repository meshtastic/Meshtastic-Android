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
package org.meshtastic.feature.connections

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import org.jetbrains.compose.resources.StringResource
import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.network.NodeDashboardClient
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.node_status_title_remote
import org.meshtastic.feature.connections.model.NodeStatusUi
import org.meshtastic.feature.connections.model.dashboardNodeStatus
import org.meshtastic.feature.connections.model.extraRadioNumber
import org.meshtastic.feature.connections.model.host.Bearer
import org.meshtastic.feature.connections.model.parse
import org.meshtastic.proto.Config.TransportConfig.Transport
import kotlin.time.Duration.Companion.seconds

/**
 * A node-kmp host reached over its dashboard's phone link: read from the host's dashboard API on the link's own origin,
 * polled while the card is shown.
 */
@Single(binds = [NodeCardSource::class])
class DashboardNodeCardSource(private val dashboard: NodeDashboardClient) : NodeCardSource {

    override fun claims(address: String): Boolean = NodeDashboardClient.baseOf(address) != null

    override val title: StringResource
        get() = Res.string.node_status_title_remote

    // A permission is the host's to grant, on its own device.
    override val grantsPermissions: Boolean
        get() = false

    override fun status(address: String): Flow<NodeStatusUi?> = flow {
        val origin = NodeDashboardClient.baseOf(address) ?: return@flow emit(null)
        while (true) {
            emit(read(origin))
            delay(POLL)
        }
    }

    private suspend fun read(origin: String): NodeStatusUi? {
        val status = dashboard.get(origin, "status") ?: return null
        return dashboardNodeStatus(
            status,
            dashboard.get(origin, "bearers"),
            dashboard.get(origin, "sensors"),
            nowMillis,
        )
    }

    /**
     * Switch [bearer] in the node's stored transport section, read back from the host so an entry the card does not
     * show is kept, as the in-process host's switch keeps it.
     */
    override suspend fun setBearer(address: String, bearer: Bearer, on: Boolean) {
        val origin = NodeDashboardClient.baseOf(address) ?: return
        val transport = parse(dashboard.get(origin, "config"))?.get("transport") as? JsonObject
        val stored =
            (transport?.get("enabled") as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
        val entry = bearer.transport().name
        val next = if (on) (stored + entry).distinct() else stored - entry
        val body = next.joinToString(prefix = "{\"enabled\":[", postfix = "]}") { "\"$it\"" }
        dashboard.put(origin, "config/transport", body)
    }

    /**
     * Switch one extra LoRa radio by itself through the host's dashboard. A switch the host made and could not keep is
     * still made; any other refusal is a failure.
     */
    override suspend fun setExtraRadio(address: String, name: String, on: Boolean): ExtraRadioSwitch {
        val origin = NodeDashboardClient.baseOf(address) ?: return ExtraRadioSwitch.FAILED
        val reply = dashboard.putForReply(origin, "extra-radios/$name", "{\"on\":$on}")
        val kept = reply?.takeIf { it.first in HTTP_OK_RANGE }?.let { (_, body) -> parse(body)?.get("kept") }
        return when {
            kept == null -> ExtraRadioSwitch.FAILED

            (kept as? JsonPrimitive)?.booleanOrNull == false && extraRadioNumber(name) != null ->
                ExtraRadioSwitch.NOT_KEPT

            else -> ExtraRadioSwitch.KEPT
        }
    }

    private companion object {
        val POLL = 2.seconds
        val HTTP_OK_RANGE = 200..299
    }
}

/** The bearer's entry in the stored transport section, which the dashboard takes by name. */
private fun Bearer.transport(): Transport = when (this) {
    Bearer.UDP -> Transport.TRANSPORT_MULTICAST_UDP
    Bearer.BLE_ADV -> Transport.TRANSPORT_BLE_ADV
    Bearer.GATT -> Transport.TRANSPORT_BLE_GATT
    Bearer.LORA -> Transport.TRANSPORT_LORA
    Bearer.MQTT -> Transport.TRANSPORT_MQTT
    Bearer.WIFI_AWARE -> Transport.TRANSPORT_WIFI_AWARE
    Bearer.AUDIO -> Transport.TRANSPORT_AUDIO
    Bearer.VIDEO -> Transport.TRANSPORT_VIDEO
}
