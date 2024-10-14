package com.geeksville.mesh.android

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.IInterface
import com.geeksville.mesh.util.exceptionReporter
import java.io.Closeable
import java.lang.IllegalArgumentException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class BindFailedException : Exception("bindService failed")

/**
 * A wrapper that cleans up the service binding process
 */
open class ServiceClient<T : IInterface>(private val stubFactory: (IBinder) -> T) : Closeable,
    Logging {

    var serviceP: T? = null

    // A getter that returns the bound service or throws if not bound
    val service: T
        get() {
            waitConnect() // Wait for at least the initial connection to happen
            return serviceP ?: throw Exception("Service not bound")
        }

    private var context: Context? = null

    private var isClosed = true

    private val lock = ReentrantLock()
    private val condition = lock.newCondition()

    /** Call this if you want to stall until the connection is completed */
    fun waitConnect() {
        // Wait until this service is connected
        lock.withLock {
            if (context == null) {
                throw Exception("Haven't called connect")
            }

            if (serviceP == null) {
                condition.await()
            }
        }
    }

    fun connect(c: Context, intent: Intent, flags: Int) {
        context = c
        if (isClosed) {
            isClosed = false
            if (!c.bindService(intent, connection, flags)) {

                // Some phones seem to ahve a race where if you unbind and quickly rebind bindService returns false.  Try
                // a short sleep to see if that helps
                errormsg("Needed to use the second bind attempt hack")
                Thread.sleep(500) // was 200ms, but received an autobug from a Galaxy Note4, android 6.0.1
                if (!c.bindService(intent, connection, flags)) {
                    throw BindFailedException()
                }
            }
        } else {
            warn("Ignoring rebind attempt for service")
        }
    }

    override fun close() {
        isClosed = true
        try {
            context?.unbindService(connection)
        } catch (ex: IllegalArgumentException) {
            // Autobugs show this can generate an illegal arg exception for "service not registered" during reinstall?
            warn("Ignoring error in ServiceClient.close, probably harmless")
        }
        serviceP = null
        context = null
    }

    // Called when we become connected
    open fun onConnected(service: T) {
    }

    // called on loss of connection
    open fun onDisconnected() {
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) = exceptionReporter {
            if (!isClosed) {
                val s = stubFactory(binder)
                serviceP = s
                onConnected(s)

                // after calling our handler, tell anyone who was waiting for this connection to complete
                lock.withLock {
                    condition.signalAll()
                }
            } else {
                // If we start to close a service, it seems that there is a possibility a onServiceConnected event is the queue
                // for us.  Be careful not to process that stale event
                warn("A service connected while we were closing it, ignoring")
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) = exceptionReporter {
            serviceP = null
            onDisconnected()
        }
    }
}
