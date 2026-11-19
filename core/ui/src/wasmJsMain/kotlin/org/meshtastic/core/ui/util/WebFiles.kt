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
@file:Suppress(
    // The browser surface this file wraps takes one small interop function per JS call.
    "TooManyFunctions",
    // Every js("...") snippet references its Kotlin parameters inside the JS source, which detekt cannot see.
    "UnusedParameter",
)

package org.meshtastic.core.ui.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import org.khronos.webgl.Int8Array
import org.khronos.webgl.toByteArray
import org.khronos.webgl.toInt8Array
import org.meshtastic.core.common.util.CommonUri
import kotlin.js.Promise

/**
 * A browser hands the page file objects, never paths, so the file launchers mint a `web-file://<id>/<name>` URI per
 * pick and keep what it stands for here. [writeWebFile] and [readWebFile] are the only readers of these URIs, which
 * mean nothing after a reload.
 */
private const val WEB_FILE_SCHEME = "web-file"

private sealed interface WebFileTarget {
    /** No save picker: the bytes leave as a browser download named [fileName]. */
    class Download(val fileName: String, val mimeType: String) : WebFileTarget

    /** A `FileSystemFileHandle` from Chromium's `showSaveFilePicker`. */
    class SaveHandle(val handle: JsAny) : WebFileTarget

    /** A `File` from an `<input type=file>` pick. */
    class Picked(val file: JsAny) : WebFileTarget
}

private val targets = mutableMapOf<Int, WebFileTarget>()
private var nextId = 0

private fun register(target: WebFileTarget, fileName: String): CommonUri {
    val id = nextId++
    targets[id] = target
    return CommonUri.parse("$WEB_FILE_SCHEME://$id/${CommonUri.encode(fileName)}")
}

private fun targetFor(uri: CommonUri): WebFileTarget? =
    if (uri.scheme == WEB_FILE_SCHEME) uri.authority?.toIntOrNull()?.let(targets::get) else null

/** Writes [bytes] to the file [uri] was minted for. False when [uri] is not a save target or the write failed. */
suspend fun writeWebFile(uri: CommonUri, bytes: ByteArray): Boolean = when (val target = targetFor(uri)) {
    is WebFileTarget.Download -> {
        downloadBytes(bytes.toInt8Array(), target.fileName, target.mimeType)
        true
    }

    is WebFileTarget.SaveHandle -> writeToHandle(target.handle, bytes.toInt8Array()).await<JsBoolean>().toBoolean()

    is WebFileTarget.Picked,
    null,
    -> false
}

/** The bytes of the file [uri] was minted for, or null when [uri] is not a picked file. */
suspend fun readWebFile(uri: CommonUri): ByteArray? {
    val target = targetFor(uri) as? WebFileTarget.Picked ?: return null
    return int8View(fileArrayBuffer(target.file).await()).toByteArray()
}

/**
 * Shows Chromium's save picker when it exists, otherwise hands back a URI whose write becomes a download. Must run
 * inside the click that asked for it: the picker needs the click's user activation, so it is called before any
 * suspension.
 */
internal fun launchSaveFile(
    scope: CoroutineScope,
    fileName: String,
    mimeType: String,
    onUriReceived: (CommonUri) -> Unit,
) {
    if (!hasSaveFilePicker()) {
        onUriReceived(register(WebFileTarget.Download(fileName, mimeType), fileName))
        return
    }
    val pick = showSaveFilePicker(fileName)
    scope.launch {
        val result = pick.await<JsAny>()
        val handle = pickedHandle(result)
        when {
            handle != null -> onUriReceived(register(WebFileTarget.SaveHandle(handle), fileName))

            // The user closed the picker: nothing to save, as when a native save dialog is cancelled.
            pickAborted(result) -> Unit

            // Refused for another reason (no activation, a cross-origin frame): fall back to a download.
            else -> onUriReceived(register(WebFileTarget.Download(fileName, mimeType), fileName))
        }
    }
}

/** Opens the browser's file chooser; [onUriReceived] gets null when the user cancels. */
internal fun launchOpenFile(mimeType: String, onUriReceived: (CommonUri?) -> Unit) {
    // A generic type would grey out files the browser cannot classify (a .db backup has no registered type).
    val accept = mimeType.takeUnless { it.isBlank() || it == "*/*" || it == "application/octet-stream" }
    openFilePicker(accept) { file ->
        onUriReceived(file?.let { register(WebFileTarget.Picked(it), fileName(it)) })
    }
}

/** Opens the file picker for an image and hands back the picked `File`, or null when the user cancels. */
internal fun pickImageFile(onPicked: (JsAny?) -> Unit) = openFilePicker("image/*", onPicked)

private fun hasSaveFilePicker(): Boolean = js("typeof window.showSaveFilePicker === 'function'")

/** Resolves to `{ handle }` on a pick, `{ aborted: true }` on cancel, `{}` on any other refusal; never rejects. */
private fun showSaveFilePicker(name: String): Promise<JsAny> = js(
    """window.showSaveFilePicker({ suggestedName: name }).then(
        (h) => ({ handle: h }),
        (e) => (e && e.name === 'AbortError' ? { aborted: true } : {}))""",
)

private fun pickedHandle(result: JsAny): JsAny? = js("result.handle || null")

private fun pickAborted(result: JsAny): Boolean = js("result.aborted === true")

/** Resolves true once the file is written and closed, false when the browser refused any step; never rejects. */
private fun writeToHandle(handle: JsAny, bytes: Int8Array): Promise<JsBoolean> =
    js("handle.createWritable().then((w) => w.write(bytes).then(() => w.close())).then(() => true, () => false)")

private fun downloadBytes(bytes: Int8Array, name: String, mime: String) {
    js(
        """{
        const url = URL.createObjectURL(new Blob([bytes], { type: mime }));
        const a = document.createElement('a');
        a.href = url;
        a.download = name;
        a.style.display = 'none';
        document.body.appendChild(a);
        a.click();
        a.remove();
        setTimeout(() => URL.revokeObjectURL(url), 60000);
    }""",
    )
}

private fun openFilePicker(accept: String?, callback: (JsAny?) -> Unit) {
    js(
        """{
        const input = document.createElement('input');
        input.type = 'file';
        if (accept) input.accept = accept;
        input.style.display = 'none';
        let done = false;
        const finish = (file) => {
            if (done) return;
            done = true;
            input.remove();
            callback(file);
        };
        input.addEventListener('change', () => finish(input.files && input.files.length ? input.files[0] : null));
        input.addEventListener('cancel', () => finish(null));
        document.body.appendChild(input);
        input.click();
    }""",
    )
}

private fun fileName(file: JsAny): String = js("file.name")

private fun fileArrayBuffer(file: JsAny): Promise<JsAny> = js("file.arrayBuffer()")

private fun int8View(buffer: JsAny): Int8Array = js("new Int8Array(buffer)")
