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
package org.meshtastic.core.network.repository

import io.ktor.network.tls.TLSConfigBuilder
import org.meshtastic.mqtt.MqttTransportFactory

/**
 * The transport(s) registered for outbound MQTT connections, composed per platform: `nonWebMain` gets TCP/TLS (the
 * default, `tcp://`/`ssl://`) plus WebSocket (`ws://`/`wss://`); `wasmJs` gets WebSocket only — a browser cannot open a
 * raw TCP socket at all (permanent sandbox limitation, not a library gap; `mqtt-client-transport-tcp` publishes no
 * wasmJs variant at all, confirmed via its Gradle Module Metadata).
 */
internal expect fun mqttTransportFactory(tls: (TLSConfigBuilder.() -> Unit)?): MqttTransportFactory
