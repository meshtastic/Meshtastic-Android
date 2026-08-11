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
package org.meshtastic.core.network

import co.touchlab.kermit.Logger
import com.fazecast.jSerialComm.SerialPort
import com.fazecast.jSerialComm.SerialPortTimeoutException
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.meshtastic.core.common.util.handledLaunch
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.network.radio.StreamTransport
import org.meshtastic.core.network.radio.TransportLifecycleGate
import org.meshtastic.core.network.transport.HeartbeatSender
import org.meshtastic.core.network.transport.StreamFrameCodec
import org.meshtastic.core.repository.RadioTransportCallback
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

private const val SERIAL_WRITE_RETRY_DELAY_MS = 10L
private const val NANOS_PER_MILLISECOND = 1_000_000L
private const val SERIAL_DATA_BITS = 8
private const val SERIAL_READ_TIMEOUT_MS = 100
private const val SERIAL_READ_BUFFER_SIZE = 1024
private const val SERIAL_WRITE_TIMEOUT_MS = 5_000
private const val SERIAL_GROUP_LOOKUP_TIMEOUT_MS = 2_000L
private val SERIAL_START_JOIN_TIMEOUT = 10.seconds

private val IS_WINDOWS_HOST = System.getProperty("os.name", "").lowercase().startsWith("windows")

private class SerialPortState {
    private val lock = SynchronizedObject()
    private var port: SerialPort? = null
    private var startJob: Job? = null
    private var readJob: Job? = null
    private var closing = false

    fun publishStartJob(job: Job, lifecycleClosed: Boolean): Boolean = synchronized(lock) {
        val unavailable = closing || lifecycleClosed || startJob?.isCompleted == false || port?.isOpen == true
        if (unavailable) false else true.also { startJob = job }
    }

    fun isClosing(lifecycleClosed: Boolean): Boolean = synchronized(lock) { closing || lifecycleClosed }

    fun publishPort(candidate: SerialPort, lifecycleClosed: Boolean): Boolean = synchronized(lock) {
        val unavailable = closing || lifecycleClosed || port != null
        if (unavailable) false else true.also { port = candidate }
    }

    fun currentOpenPort(): SerialPort? = synchronized(lock) { port?.takeIf { !closing && it.isOpen } }

    fun publishReadJob(expectedPort: SerialPort, job: Job, lifecycleClosed: Boolean): Boolean = synchronized(lock) {
        val unavailable = closing || lifecycleClosed || port !== expectedPort || readJob?.isActive == true
        if (unavailable) false else true.also { readJob = job }
    }

    fun canPublish(expectedPort: SerialPort?, requireNoPublishedPort: Boolean): Boolean = synchronized(lock) {
        !closing && (expectedPort == null || port === expectedPort) && (!requireNoPublishedPort || port == null)
    }

    fun retirePort(expectedPort: SerialPort): Boolean =
        synchronized(lock) { (port === expectedPort).also { owned -> if (owned) port = null } }

    fun beginClose(): Job? = synchronized(lock) {
        closing = true
        startJob.also { startJob = null }
    }

    fun takeReadJob(): Job? = synchronized(lock) { readJob.also { readJob = null } }

    fun takePort(): SerialPort? = synchronized(lock) { port.also { port = null } }
}

private fun writeFullyWithDeadline(port: SerialPort, payload: ByteArray, portName: String, timeoutMs: Int) {
    val startedNanos = System.nanoTime()
    val timeoutNanos = timeoutMs.toLong() * NANOS_PER_MILLISECOND
    var offset = 0
    while (offset < payload.size) {
        val written = port.writeBytes(payload, payload.size - offset, offset)
        if (written < 0) {
            throw IOException("[$portName] Serial write failed after $offset/${payload.size} bytes")
        }
        offset += written
        if (offset < payload.size) {
            if (System.nanoTime() - startedNanos >= timeoutNanos) {
                throw IOException(
                    "[$portName] Serial write timed out after ${timeoutMs}ms at $offset/${payload.size} bytes",
                )
            }
            // Partial writes can make progress while still keeping the driver continuously writable. Yield between
            // every retry so a slow device cannot turn this bounded loop into a CPU spin.
            Thread.sleep(SERIAL_WRITE_RETRY_DELAY_MS)
        }
    }
}

private fun configureSerialPort(port: SerialPort, baudRate: Int) {
    port.setComPortParameters(baudRate, SERIAL_DATA_BITS, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY)
    // jSerialComm honors write timeouts only on Windows. TIMEOUT_NONBLOCKING is zero, so on other hosts the supplied
    // write-timeout argument is intentionally inert; writeFullyWithDeadline owns the cross-platform deadline instead.
    val timeoutMode =
        SerialPort.TIMEOUT_READ_SEMI_BLOCKING or
            if (IS_WINDOWS_HOST) SerialPort.TIMEOUT_WRITE_BLOCKING else SerialPort.TIMEOUT_NONBLOCKING
    port.setComPortTimeouts(timeoutMode, SERIAL_READ_TIMEOUT_MS, SERIAL_WRITE_TIMEOUT_MS)
}

private fun diagnoseSerialOpenFailure(portName: String): String {
    val osName = System.getProperty("os.name", "").lowercase()
    val devPath = if (portName.startsWith("/")) portName else "/dev/$portName"
    val portFile = File(devPath)
    return when {
        !osName.contains("linux") -> "Could not open serial port: $portName"

        !portFile.exists() -> "Serial port $portName not found. Is the device still connected?"

        !portFile.canRead() || !portFile.canWrite() -> {
            val group = detectSerialGroup(devPath)
            "Permission denied for $devPath. Add your account to the $group group, then log out and back in."
        }

        else -> "Could not open serial port: $portName"
    }
}

private fun detectSerialGroup(devPath: String): String = runCatching {
    val process = ProcessBuilder("stat", "-c", "%G", devPath).redirectErrorStream(true).start()
    try {
        if (!process.waitFor(SERIAL_GROUP_LOOKUP_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            Logger.w { "stat lookup for $devPath timed out" }
            "dialout"
        } else {
            val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
            if (process.exitValue() != 0) {
                val detail = output.takeIf(String::isNotEmpty)?.let { ": $it" }.orEmpty()
                Logger.w { "stat lookup for $devPath failed with exit code ${process.exitValue()}$detail" }
                "dialout"
            } else {
                output.takeIf { it.isNotEmpty() && it.none(Char::isWhitespace) } ?: "dialout"
            }
        }
    } finally {
        process.destroy()
        if (process.isAlive) process.destroyForcibly()
    }
}
    .getOrDefault("dialout")

private fun closeSerialPort(portName: String, port: SerialPort) {
    val failure = runCatching { if (port.isOpen) port.closePort() }.exceptionOrNull()
    if (failure != null) Logger.w(failure) { "[$portName] Failed to close serial port" }
}

private data class SerialReadStep(val continueReading: Boolean, val failed: Boolean = false)

private fun processSerialRead(
    readResult: Result<Int>,
    buffer: ByteArray,
    portName: String,
    lifecycle: TransportLifecycleGate,
    readChar: (Byte) -> Unit,
): SerialReadStep {
    val failure = readResult.exceptionOrNull()
    return when {
        failure == null -> {
            val numRead = readResult.getOrThrow()
            when {
                numRead == -1 -> SerialReadStep(continueReading = false, failed = true)

                numRead <= 0 -> SerialReadStep(continueReading = true)

                else -> {
                    val admitted = lifecycle.runIfOpen { for (i in 0 until numRead) readChar(buffer[i]) }
                    SerialReadStep(continueReading = admitted != null)
                }
            }
        }

        failure is SerialPortTimeoutException -> SerialReadStep(continueReading = true)

        failure is CancellationException -> throw failure

        failure !is Exception -> throw failure

        else -> {
            Logger.w(failure) { "[$portName] Serial read error" }
            SerialReadStep(continueReading = false, failed = true)
        }
    }
}

private suspend fun runSerialReadLoop(
    port: SerialPort,
    portName: String,
    lifecycle: TransportLifecycleGate,
    readChar: (Byte) -> Unit,
): Boolean {
    val input = port.inputStream
    val buffer = ByteArray(SERIAL_READ_BUFFER_SIZE)
    var endedUnexpectedly = false
    try {
        var reading = true
        while (currentCoroutineContext().isActive && port.isOpen && reading) {
            val step = processSerialRead(runCatching { input.read(buffer) }, buffer, portName, lifecycle, readChar)
            reading = step.continueReading
            endedUnexpectedly = endedUnexpectedly || step.failed
        }
        endedUnexpectedly =
            if (currentCoroutineContext().isActive) {
                endedUnexpectedly || !lifecycle.isClosed
            } else {
                false
            }
    } finally {
        runCatching { input.close() }
    }
    return endedUnexpectedly
}

private fun publishSerialCallbackIfOpen(
    lifecycle: TransportLifecycleGate,
    state: SerialPortState,
    expectedPort: SerialPort? = null,
    requireNoPublishedPort: Boolean = false,
    block: () -> Unit,
): Boolean = lifecycle.runIfOpen {
    val publish = state.canPublish(expectedPort, requireNoPublishedPort)
    if (publish) block()
    publish
} ?: false

/**
 * JVM-specific implementation of [RadioTransport] using jSerialComm. Uses [StreamTransport] for START1/START2 packet
 * framing.
 *
 * Use the [create] factory method instead of the constructor directly. Construction is side-effect free; [start] opens
 * the serial port and publishes the connection.
 */
class SerialTransport
private constructor(
    private val portName: String,
    private val baudRate: Int = DEFAULT_BAUD_RATE,
    callback: RadioTransportCallback,
    scope: CoroutineScope,
    private val dispatchers: CoroutineDispatchers,
) : StreamTransport(callback, scope) {
    private val lifecycle = TransportLifecycleGate("JVM serial")
    private val portState = SerialPortState()
    private val heartbeatSender = HeartbeatSender(sendToRadio = { handleSendToRadio(it) }, logTag = "Serial[$portName]")

    override fun start() {
        val job = scope.launch(dispatchers.io, start = CoroutineStart.LAZY) { runConnectionStart() }
        if (portState.publishStartJob(job, lifecycle.isClosed)) {
            job.start()
        } else {
            job.cancel()
            if (lifecycle.isClosed) Logger.d { "[$portName] Ignoring start after serial transport close" }
        }
    }

    private suspend fun runConnectionStart() {
        val lease = lifecycle.tryAcquire() ?: return
        try {
            if (!startConnection() && !lifecycle.isClosed) {
                val errorMessage = diagnoseSerialOpenFailure(portName)
                Logger.w { "[$portName] Serial port could not be opened; signalling disconnect" }
                publishSerialCallbackIfOpen(lifecycle, portState, requireNoPublishedPort = true) {
                    callback.onDisconnect(isPermanent = false, errorMessage = errorMessage)
                }
            }
        } finally {
            lease.release()
        }
    }

    /** Attempts to open the serial port and starts the read loop. Returns true if successful, false otherwise. */
    private suspend fun startConnection(): Boolean {
        if (portState.isClosing(lifecycle.isClosed)) return false
        var candidatePort: SerialPort? = null
        val attempt = runCatching {
            val port = SerialPort.getCommPort(portName)
            if (port == null) {
                false
            } else {
                candidatePort = port
                configureSerialPort(port, baudRate)
                if (!runInterruptible(dispatchers.io) { port.openPort() }) {
                    Logger.w { "[$portName] Serial port openPort() returned false" }
                    closeSerialPort(portName, port)
                    candidatePort = null
                    false
                } else {
                    port.setDTR()
                    port.setRTS()
                    if (!portState.publishPort(port, lifecycle.isClosed)) {
                        closeSerialPort(portName, port)
                        candidatePort = null
                        false
                    } else {
                        candidatePort = null
                        finishPublishedPort(port)
                    }
                }
            }
        }
        val failure = attempt.exceptionOrNull()
        candidatePort?.let { closeSerialPort(portName, it) }
        return when {
            failure == null -> attempt.getOrDefault(false)

            failure is CancellationException -> {
                portState.takePort()?.let { closeSerialPort(portName, it) }
                throw failure
            }

            failure !is Exception -> {
                portState.takePort()?.let { closeSerialPort(portName, it) }
                throw failure
            }

            else -> {
                portState.takePort()?.let { closeSerialPort(portName, it) }
                Logger.w(failure) { "[$portName] Serial connection failed" }
                false
            }
        }
    }

    private suspend fun finishPublishedPort(port: SerialPort): Boolean {
        Logger.i { "[$portName] Serial port opened (baud=$baudRate)" }
        runInterruptible(dispatchers.io) {
            writeFullyWithDeadline(port, StreamFrameCodec.WAKE_BYTES, portName, SERIAL_WRITE_TIMEOUT_MS)
        }
        val connected = publishSerialCallbackIfOpen(lifecycle, portState, expectedPort = port) { callback.onConnect() }
        if (connected) startReadLoop(port)
        return connected
    }

    private fun startReadLoop(port: SerialPort) {
        val job =
            scope.launch(dispatchers.io, start = CoroutineStart.LAZY) {
                val endedUnexpectedly = runSerialReadLoop(port, portName, lifecycle, ::readChar)
                Logger.d { "[$portName] Serial read loop exiting" }
                if (portState.retirePort(port)) closeSerialPort(portName, port)
                if (endedUnexpectedly) {
                    publishSerialCallbackIfOpen(lifecycle, portState, requireNoPublishedPort = true) {
                        onDeviceDisconnect(waitForStopped = true, isPermanent = false)
                    }
                }
            }
        if (portState.publishReadJob(port, job, lifecycle.isClosed)) {
            Logger.d { "[$portName] Starting serial read loop" }
            job.start()
        } else {
            job.cancel()
        }
    }

    override fun handleSendToRadio(p: ByteArray): Boolean {
        val lease = lifecycle.tryAcquire() ?: return false
        val port = portState.currentOpenPort()
        return if (port == null) {
            lease.release()
            false
        } else {
            queueFramedSend(
                payload = p,
                writer = { bytes ->
                    runInterruptible(dispatchers.io) {
                        writeFullyWithDeadline(port, bytes, portName, SERIAL_WRITE_TIMEOUT_MS)
                    }
                },
                flusher = {},
                onCompletion = lease::release,
            )
        }
    }

    override fun sendBytes(p: ByteArray) {
        portState.currentOpenPort()?.let { port -> writeFullyWithDeadline(port, p, portName, SERIAL_WRITE_TIMEOUT_MS) }
    }

    override fun flushBytes() {
        // Raw SerialPort.writeBytes writes directly to the driver buffer; there is no user-space stream to flush.
    }

    override fun keepAlive() {
        scope.handledLaunch { heartbeatSender.sendHeartbeat() }
    }

    override suspend fun close() {
        withContext(NonCancellable) {
            Logger.d { "[$portName] Closing serial transport" }
            val currentStartJob = portState.beginClose()
            if (currentStartJob != null) {
                currentStartJob.cancel()
                val joined = withTimeoutOrNull(SERIAL_START_JOIN_TIMEOUT) { currentStartJob.join() } != null
                if (!joined) {
                    Logger.w { "[$portName] Serial open did not stop within $SERIAL_START_JOIN_TIMEOUT" }
                }
            }
            // beginClose makes new sends reject synchronously. Stop the framed-send worker now so queued sends release
            // their lifecycle leases before the gate waits for admitted operations.
            super.close()
            val completed =
                lifecycle.close {
                    val currentReadJob = portState.takeReadJob()
                    runInterruptible(dispatchers.io) { portState.takePort()?.let { closeSerialPort(portName, it) } }
                    currentReadJob?.cancelAndJoin()
                }
            if (!completed) Logger.w { "[$portName] JVM serial teardown did not complete within its lifecycle bounds" }
        }
    }

    companion object {
        private const val DEFAULT_BAUD_RATE = 115200

        /**
         * Creates a side-effect-free [SerialTransport]. The owning transport service invokes [SerialTransport.start]
         * after it has published session ownership, so even immediate serial callbacks carry the correct generation.
         */
        fun create(
            portName: String,
            baudRate: Int = DEFAULT_BAUD_RATE,
            callback: RadioTransportCallback,
            scope: CoroutineScope,
            dispatchers: CoroutineDispatchers,
        ): SerialTransport = SerialTransport(portName, baudRate, callback, scope, dispatchers)

        /** Returns names of all available serial ports. */
        fun getAvailablePorts(): List<String> = SerialPort.getCommPorts().map { it.systemPortName }
    }
}
