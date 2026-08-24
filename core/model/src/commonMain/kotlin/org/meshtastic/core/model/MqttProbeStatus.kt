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
package org.meshtastic.core.model

/**
 * UI-friendly outcome of a one-shot MQTT broker reachability probe.
 *
 * Mirrors the failure shapes of `org.meshtastic.mqtt.ProbeResult` but stays in the model module so feature/UI code can
 * consume the result without depending on the MQTT library.
 */
sealed class MqttProbeStatus {
    /** Probe is currently in flight. */
    data object Probing : MqttProbeStatus()

    /**
     * Broker accepted the connection. [serverInfo] is a short human-readable summary of any CONNACK properties that are
     * useful to surface to the user.
     */
    data class Success(val serverInfo: String?) : MqttProbeStatus()

    /** Broker rejected the connection (CONNACK with non-zero reason code). */
    data class Rejected(val reasonCode: Int, val reason: String?, val serverReference: String?) : MqttProbeStatus()

    /** DNS lookup failed. */
    data class DnsFailure(val message: String?) : MqttProbeStatus()

    /** TCP socket could not be opened. */
    data class TcpFailure(val message: String?) : MqttProbeStatus()

    /** TLS handshake failed. */
    data class TlsFailure(val message: String?) : MqttProbeStatus()

    /** Probe exceeded its timeout. */
    data class Timeout(val timeoutMs: Long) : MqttProbeStatus()

    /** Any other / unclassified failure. */
    data class Other(val message: String?) : MqttProbeStatus()
}
