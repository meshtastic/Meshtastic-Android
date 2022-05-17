package com.geeksville.mesh.repository.nsd

import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.geeksville.android.Logging
import com.geeksville.mesh.CoroutineDispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.collections.ArrayList

@Singleton
class NsdRepository @Inject constructor(
    private val dispatchers: CoroutineDispatchers,
    private val nsdManagerLazy: dagger.Lazy<NsdManager?>,
) : Logging {

    private val resolveQueue = Semaphore(1)
    private var hostsList: ArrayList<NsdServiceInfo>? = ArrayList()

    val resolvedList: List<NsdServiceInfo>? get() = hostsList

    private val _networkDiscovery: Flow<NsdServiceInfo> = callbackFlow {
        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                debug("Service discovery started: $regType")
                hostsList?.clear()
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                debug("Service discovery success: $service")
                if (service.serviceType == SERVICE_TYPE &&
                    service.serviceName.contains(serviceName)
                ) {
                    val resolveListener = object : NsdManager.ResolveListener {
                        override fun onServiceResolved(service: NsdServiceInfo) {
                            debug("Resolve Succeeded: $service")
                            hostsList?.add(service)
                            trySend(service)
                        }

                        override fun onResolveFailed(service: NsdServiceInfo, errorCode: Int) {
                            debug("Resolve failed: $service - Error code: $errorCode")
                        }
                    }
                    // one resolveService at a time to avoid: Error Code 3: Failure Already active
                    launch {
                        try {
                            resolveQueue.acquire()
                            nsdManagerLazy.get()?.resolveService(service, resolveListener)
                        } finally {
                            resolveQueue.release()
                        }
                    }
                } else {
                    debug("Not our Service - Name: ${service.serviceName}, Type: ${service.serviceType}")
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                debug("Service lost: $service")
            }

            override fun onDiscoveryStopped(serviceType: String) {
                debug("Discovery stopped: $serviceType")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                debug("Start Discovery failed: Error code: $errorCode")
                nsdManagerLazy.get()?.stopServiceDiscovery(this)
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                debug("Stop Discovery failed: Error code: $errorCode")
                nsdManagerLazy.get()?.stopServiceDiscovery(this)
            }
        }
        nsdManagerLazy.get()
            ?.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        awaitClose { nsdManagerLazy.get()?.stopServiceDiscovery(discoveryListener) }
    }.flowOn(dispatchers.default)

    fun networkDiscoveryFlow(): Flow<NsdServiceInfo> {
        return _networkDiscovery
    }

    companion object {
        //To find all the available networks SERVICE_TYPE = "_services._dns-sd._udp"
        const val SERVICE_TYPE = "_http._tcp."
        const val serviceName = "Meshtastic"
    }
}
