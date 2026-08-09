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
 * Desktop has no equivalent of Android's user credential store: the JVM already trusts whatever the running JDK's
 * `cacerts` holds, and a self-hosted CA is added there (or via `-Djavax.net.ssl.trustStore`) rather than by the app.
 * Use the platform trust decision unchanged.
 */
actual fun mqttTlsConfig(): (TLSConfigBuilder.() -> Unit)? = null
