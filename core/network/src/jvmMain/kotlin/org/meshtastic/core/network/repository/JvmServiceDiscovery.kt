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
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import org.koin.core.annotation.Single
import org.meshtastic.core.di.CoroutineDispatchers
import java.io.IOException
import java.net.InetAddress
import java.net.NetworkInterface
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceListener

@Single(binds = [ServiceDiscovery::class])
class JvmServiceDiscovery(private val dispatchers: CoroutineDispatchers) : ServiceDiscovery {
    @Suppress("TooGenericExceptionCaught")
    override val resolvedServices: Flow<List<DiscoveredService>> =
        callbackFlow {
            trySend(emptyList()) // Emit initial empty list so downstream combine() is not blocked

            val bindAddress = findLanAddress() ?: InetAddress.getLocalHost()
            Logger.i { "JmDNS binding to ${bindAddress.hostAddress}" }

            val jmdns =
                try {
                    JmDNS.create(bindAddress)
                } catch (e: IOException) {
                    Logger.e(e) { "Failed to create JmDNS" }
                    null
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Logger.e(e) { "Unexpected error creating JmDNS" }
                    null
                }

            val services = mutableMapOf<String, DiscoveredService>()

            val listener =
                object : ServiceListener {
                    override fun serviceAdded(event: ServiceEvent) {
                        jmdns?.requestServiceInfo(event.type, event.name)
                    }

                    override fun serviceRemoved(event: ServiceEvent) {
                        services.remove(event.name)
                        trySend(services.values.toList())
                    }

                    override fun serviceResolved(event: ServiceEvent) {
                        val info = event.info
                        val txtMap = mutableMapOf<String, ByteArray>()
                        info.propertyNames.toList().forEach { key ->
                            info.getPropertyBytes(key)?.let { value -> txtMap[key] = value }
                        }
                        val discovered =
                            DiscoveredService(
                                name = info.name,
                                hostAddress = info.hostAddresses.firstOrNull() ?: "",
                                port = info.port,
                                txt = txtMap,
                            )
                        services[info.name] = discovered
                        trySend(services.values.toList())
                    }
                }

            val type = "${NetworkConstants.SERVICE_TYPE}.local."
            jmdns?.addServiceListener(type, listener)

            awaitClose {
                jmdns?.removeServiceListener(type, listener)
                try {
                    jmdns?.close()
                } catch (e: IOException) {
                    Logger.e(e) { "Failed to close JmDNS" }
                } catch (e: Exception) {
                    Logger.e(e) { "Unexpected error closing JmDNS" }
                }
            }
        }
            .flowOn(dispatchers.io)

    companion object {
        /**
         * Finds a non-loopback, up, IPv4 LAN address for JmDNS to bind to. On many systems (especially Windows),
         * [InetAddress.getLocalHost] resolves to `127.0.0.1` or `::1`, which prevents JmDNS from seeing multicast
         * traffic on the actual LAN interface.
         */
        @Suppress("TooGenericExceptionCaught", "LoopWithTooManyJumpStatements")
        internal fun findLanAddress(): InetAddress? = try {
            NetworkInterface.getNetworkInterfaces()
                ?.toList()
                .orEmpty()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { !it.isLoopbackAddress && it is java.net.Inet4Address }
        } catch (e: Exception) {
            Logger.w(e) { "Failed to enumerate network interfaces, falling back to getLocalHost()" }
            null
        }
    }
}
