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

import co.touchlab.kermit.Logger
import io.ktor.network.tls.TLSConfigBuilder
import java.security.KeyStore
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * `AndroidCAStore` exposes the system CAs *and* the CAs the user has installed from Settings. Anchoring MQTT on it
 * reproduces what `<certificates src="system"/>` + `<certificates src="user"/>` used to give the whole app, but only
 * for the MQTT socket.
 */
private const val ANDROID_CA_STORE = "AndroidCAStore"

actual fun mqttTlsConfig(): (TLSConfigBuilder.() -> Unit)? {
    val manager = userCaTrustManager() ?: return null
    return { trustManager = manager }
}

/**
 * Built through a [TrustManagerFactory] so the result is one `X509TrustManagerExtensions` can wrap — the transport
 * needs that to reach the hostname-aware `checkServerTrusted(chain, authType, host)` overload the platform requires
 * whenever `network_security_config.xml` carries any domain-specific config (ours does, for the localhost cleartext
 * exemptions).
 *
 * Returning `null` on failure falls back to the platform default, which is the safe direction: a private-CA broker
 * stops connecting, rather than trust silently widening.
 */
private fun userCaTrustManager(): X509TrustManager? = runCatching {
    val keyStore = KeyStore.getInstance(ANDROID_CA_STORE).apply { load(null) }
    TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        .apply { init(keyStore) }
        .trustManagers
        .filterIsInstance<X509TrustManager>()
        .firstOrNull()
}
    .onFailure {
        Logger.w(throwable = it) { "Could not build the MQTT user-CA trust manager; using platform trust" }
    }
    .getOrNull()
