package org.meshtastic.core.network.repository

import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import org.koin.core.annotation.Single
import java.net.InetAddress
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceListener

@Single
class JvmServiceDiscovery : ServiceDiscovery {
    override val resolvedServices: Flow<List<DiscoveredService>> = callbackFlow {
        val jmdns = try {
            JmDNS.create(InetAddress.getLocalHost())
        } catch (e: Exception) {
            Logger.e(e) { "Failed to create JmDNS" }
            null
        }

        val services = mutableMapOf<String, DiscoveredService>()

        val listener = object : ServiceListener {
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
                    info.getPropertyBytes(key)?.let { value ->
                        txtMap[key] = value
                    }
                }
                val discovered = DiscoveredService(
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
            } catch (e: Exception) {
                Logger.e(e) { "Failed to close JmDNS" }
            }
        }
    }.flowOn(Dispatchers.IO)
}
