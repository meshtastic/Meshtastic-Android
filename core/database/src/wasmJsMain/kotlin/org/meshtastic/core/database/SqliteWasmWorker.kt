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
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.ArrayBufferView
import org.meshtastic.core.common.util.copyBytes
import org.meshtastic.core.common.util.toUint8Array
import org.w3c.dom.MessageChannel
import org.w3c.dom.MessagePort
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
    private lateinit var controlPort: MessagePort

    val worker: Worker by lazy {
        val created = createWorker()
        val channel = MessageChannel()
        channel.port1.onmessage = { event -> event.data?.let { onControlMessage(it.unsafeCast<ControlMessage>()) } }
        created.postMessage(controlPortMessage(channel.port2), listOf<JsAny>(channel.port2).toJsArray())
        controlPort = channel.port1
        created
    }

    /** Each driver claims the worker's `onmessage` and numbers its messages from zero, so there is exactly one. */
    val driver: WebWorkerSQLiteDriver by lazy { WebWorkerSQLiteDriver(worker) }

    /** The bytes of pool file [path], or null when it does not exist. */
    suspend fun exportFile(path: String): ByteArray? = request { id ->
        exportFileRequest(id, path)
    }
        ?.unsafeCast<ArrayBufferView>()
        ?.copyBytes()

    /** Writes [bytes] as pool file [path]; the worker refuses an open file or bytes that are not a database. */
    suspend fun importFile(path: String, bytes: ByteArray) {
        val array = bytes.toUint8Array()
        request(transfer = array.buffer) { id -> importFileRequest(id, path, array) }
    }

    suspend fun moveFile(from: String, to: String) {
        request { id -> moveFileRequest(id, from, to) }
    }

    /** Opens pool file [path] on its own and reports its `user_version` and `PRAGMA integrity_check` result. */
    suspend fun inspectDatabase(path: String): DatabaseInspection =
        checkNotNull(request { id -> inspectDatabaseRequest(id, path) }).unsafeCast<DatabaseInspection>()

    fun deleteFileDetached(path: String) {
        send(transfer = null) { id -> deleteFileRequest(id, path) }
            .invokeOnCompletion { cause ->
                if (cause != null) Logger.w(cause) { "Deleting a web database file failed" }
            }
    }

    private suspend fun request(transfer: ArrayBuffer? = null, message: (id: Int) -> JsAny): JsAny? =
        send(transfer, message).await()

    /** Posts the request [message] builds for a fresh id, handing [transfer] over to the worker without a copy. */
    private fun send(transfer: ArrayBuffer?, message: (id: Int) -> JsAny): CompletableDeferred<JsAny?> {
        worker
        val id = nextId++
        val reply = CompletableDeferred<JsAny?>()
        pending[id] = reply
        if (transfer == null) {
            controlPort.postMessage(message(id))
        } else {
            controlPort.postMessage(message(id), listOf<JsAny>(transfer).toJsArray())
        }
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

internal external interface DatabaseInspection : JsAny {
    val userVersion: Int
    val integrity: String?
}

/** Starts the `sqlite-wasm-worker` npm package's worker; webpack resolves the URL. */
private fun createWorker(): Worker = js("""new Worker(new URL("sqlite-wasm-worker/worker.js", import.meta.url))""")

private fun controlPortMessage(port: MessagePort): JsAny = js("({ controlPort: port })")

private fun exportFileRequest(id: Int, path: String): JsAny = js("({ id: id, cmd: 'exportFile', path: path })")

private fun importFileRequest(id: Int, path: String, bytes: JsAny): JsAny =
    js("({ id: id, cmd: 'importFile', path: path, bytes: bytes })")

private fun moveFileRequest(id: Int, from: String, to: String): JsAny =
    js("({ id: id, cmd: 'moveFile', from: from, to: to })")

private fun inspectDatabaseRequest(id: Int, path: String): JsAny =
    js("({ id: id, cmd: 'inspectDatabase', path: path })")

private fun deleteFileRequest(id: Int, path: String): JsAny = js("({ id: id, cmd: 'deleteFile', path: path })")
