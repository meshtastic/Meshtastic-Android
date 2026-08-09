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

/**
 * TLS customisation applied to the MQTT transports, or `null` to keep the platform default trust decision.
 *
 * Self-hosted brokers are commonly fronted by a private or self-signed CA that the user installs into the device
 * credential store. Android used to grant that trust app-wide via `<certificates src="user"/>` in `base-config` of
 * `network_security_config.xml`, which widened every TLS connection the app makes — map tiles, API calls, everything.
 * mqtt-client 0.6.0/0.7.0 added a per-transport hook (`TcpTransportFactory`/`WebSocketTransportFactory`), so the grant
 * now applies to the MQTT socket alone.
 *
 * Supplying a trust manager *replaces* the platform trust decision for these connections: network-security-config
 * anchors, pinning, and Certificate Transparency policy no longer apply to them. That is a deliberately narrower blast
 * radius than the app-wide anchor it replaces.
 */
expect fun mqttTlsConfig(): (TLSConfigBuilder.() -> Unit)?
