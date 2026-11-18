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
@file:Suppress("UnusedParameter", "TooManyFunctions")

package org.meshtastic.core.database

import androidx.sqlite.driver.web.WebWorkerSQLiteDriver
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.w3c.dom.Worker

/** Whether this tab holds the web database. The `opfs-sahpool` VFS lets one tab at a time open its files. */
enum class WebDatabaseAccess {
    Starting,

    /** Another tab holds the database; this one gets it once that tab closes. */
    Busy,
    Ready,

    /** The browser offers no usable OPFS, as in some private windows. */
    Failed,
}

/** This tab's database access; the worker, and with it the claim on the database, starts on first read. */
val webDatabaseAccess: StateFlow<WebDatabaseAccess>
    get() = SqliteWasmWorker.access

/**
 * The tab's one sqlite-wasm worker, shared by every connection so they all go through the one VFS registration that
 * holds the pool. Beside androidx's own protocol it carries a control port for whole-file requests.
 */
internal object SqliteWasmWorker {
    private val mutableAccess = MutableStateFlow(WebDatabaseAccess.Starting)
    val access: StateFlow<WebDatabaseAccess>
        get() = mutableAccess.asStateFlow().also { worker }

    private val pending = mutableMapOf<Int, CompletableDeferred<JsAny?>>()
    private var nextId = 0
    private lateinit var controlPort: JsAny

    val worker: Worker by lazy {
        val created = createWorker()
        controlPort = connectControlPort(created) { message -> onControlMessage(message) }
        created
    }

    /** Each driver claims the worker's `onmessage` and numbers its messages from zero, so there is exactly one. */
    val driver: WebWorkerSQLiteDriver by lazy { WebWorkerSQLiteDriver(worker) }

    /** The bytes of pool file [path], or null when it does not exist. */
    suspend fun exportFile(path: String): ByteArray? =
        request(exportFileRequest(nextRequestId(), path))?.let { it.unsafeCast<JsUint8Array>().toByteArray() }

    /** Writes [bytes] as pool file [path]; the worker refuses an open file or bytes that are not a database. */
    suspend fun importFile(path: String, bytes: ByteArray) {
        request(importFileRequest(nextRequestId(), path, bytes.toUint8Array()))
    }

    suspend fun moveFile(from: String, to: String) {
        request(moveFileRequest(nextRequestId(), from, to))
    }

    /** Opens pool file [path] on its own and reports its `user_version` and `PRAGMA integrity_check` result. */
    suspend fun inspectDatabase(path: String): DatabaseInspection =
        checkNotNull(request(inspectDatabaseRequest(nextRequestId(), path))).unsafeCast<DatabaseInspection>()

    fun deleteFileDetached(path: String) {
        send(deleteFileRequest(nextRequestId(), path)).invokeOnCompletion { cause ->
            if (cause != null) Logger.w(cause) { "Deleting a web database file failed" }
        }
    }

    private fun nextRequestId(): Int = nextId++

    private suspend fun request(message: JsAny): JsAny? = send(message).await()

    private fun send(message: JsAny): CompletableDeferred<JsAny?> {
        worker
        val reply = CompletableDeferred<JsAny?>()
        pending[requestId(message)] = reply
        postControl(controlPort, message)
        return reply
    }

    private fun onControlMessage(message: ControlMessage) {
        if (message.event == "state") {
            mutableAccess.value =
                when (message.state) {
                    "ready" -> WebDatabaseAccess.Ready
                    "busy" -> WebDatabaseAccess.Busy
                    "failed" -> WebDatabaseAccess.Failed
                    else -> WebDatabaseAccess.Starting
                }
            return
        }
        val reply = pending.remove(message.id) ?: return
        val error = message.error
        if (error != null) {
            reply.completeExceptionally(WebDatabaseFileException(error))
        } else {
            reply.complete(message.data)
        }
    }
}

class WebDatabaseFileException(message: String) : RuntimeException(message)

private external interface ControlMessage : JsAny {
    val id: Int
    val event: String?
    val state: String?
    val error: String?
    val data: JsAny?
}

internal external interface JsUint8Array : JsAny

internal external interface DatabaseInspection : JsAny {
    val userVersion: Int
    val integrity: String?
}

/** Starts the `sqlite-wasm-worker` npm package's worker; webpack resolves the URL. */
private fun createWorker(): Worker = js("""new Worker(new URL("sqlite-wasm-worker/worker.js", import.meta.url))""")

private fun connectControlPort(worker: Worker, onMessage: (ControlMessage) -> Unit): JsAny = js(
    """{
        const channel = new MessageChannel();
        channel.port1.onmessage = (e) => onMessage(e.data);
        worker.postMessage({ controlPort: channel.port2 }, [channel.port2]);
        return channel.port1;
    }""",
)

private fun postControl(port: JsAny, message: JsAny): Unit =
    js("message.bytes ? port.postMessage(message, [message.bytes.buffer]) : port.postMessage(message)")

private fun requestId(message: JsAny): Int = js("message.id")

private fun exportFileRequest(id: Int, path: String): JsAny = js("({ id: id, cmd: 'exportFile', path: path })")

private fun importFileRequest(id: Int, path: String, bytes: JsUint8Array): JsAny =
    js("({ id: id, cmd: 'importFile', path: path, bytes: bytes })")

private fun moveFileRequest(id: Int, from: String, to: String): JsAny =
    js("({ id: id, cmd: 'moveFile', from: from, to: to })")

private fun inspectDatabaseRequest(id: Int, path: String): JsAny =
    js("({ id: id, cmd: 'inspectDatabase', path: path })")

private fun deleteFileRequest(id: Int, path: String): JsAny = js("({ id: id, cmd: 'deleteFile', path: path })")

private fun newUint8Array(size: Int): JsUint8Array = js("new Uint8Array(size)")

private fun setByte(array: JsUint8Array, index: Int, value: Byte): Unit = js("array[index] = value")

private fun byteLength(array: JsUint8Array): Int = js("array.byteLength")

private fun signedByteAt(array: JsUint8Array, index: Int): Byte = js("(array[index] << 24) >> 24")

internal fun ByteArray.toUint8Array(): JsUint8Array {
    val array = newUint8Array(size)
    for (i in indices) setByte(array, i, this[i])
    return array
}

internal fun JsUint8Array.toByteArray(): ByteArray = ByteArray(byteLength(this)) { signedByteAt(this, it) }
