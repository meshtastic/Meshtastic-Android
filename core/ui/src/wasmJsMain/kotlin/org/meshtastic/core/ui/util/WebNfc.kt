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
    // Every js("...") snippet references its Kotlin parameters inside the JS source, which detekt cannot see.
    "UnusedParameter",
)

package org.meshtastic.core.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState

// Web NFC (NDEFReader) ships in Chrome on Android only. Each effect owns an AbortController, so leaving the
// composition stops the scan or cancels the pending write. NotReadableError is Chrome's answer when NFC is off.

@Composable
internal actual fun PlatformNfcScannerEffect(onResult: (String?) -> Unit, onNfcOff: () -> Unit) {
    val currentOnResult by rememberUpdatedState(onResult)
    val currentOnNfcDisabled by rememberUpdatedState(onNfcOff)
    DisposableEffect(Unit) {
        val controller = startNfcScan(onUrl = { currentOnResult(it) }, onDisabled = { currentOnNfcDisabled() })
        onDispose { abortNfc(controller) }
    }
}

@Composable
internal actual fun PlatformNfcWriterEffect(url: String, onResult: (Boolean) -> Unit, onNfcOff: () -> Unit) {
    val currentOnResult by rememberUpdatedState(onResult)
    val currentOnNfcDisabled by rememberUpdatedState(onNfcOff)
    DisposableEffect(url) {
        val controller =
            startNfcWrite(url, onWritten = { currentOnResult(it) }, onDisabled = { currentOnNfcDisabled() })
        onDispose { abortNfc(controller) }
    }
}

internal actual val platformNfcSupported: Boolean = webNfcAvailable()

private fun webNfcAvailable(): Boolean = js("typeof window !== 'undefined' && 'NDEFReader' in window")

// Hands back the first URL or text record of each tag read; a tag with neither is ignored, as on Android.
private fun startNfcScan(onUrl: (String) -> Unit, onDisabled: () -> Unit): JsAny = js(
    """{
    const controller = new AbortController();
    const reader = new NDEFReader();
    reader.addEventListener('reading', (event) => {
        for (const record of event.message.records) {
            if (record.recordType === 'url' || record.recordType === 'absolute-url' || record.recordType === 'text') {
                const decoder = new TextDecoder(record.encoding || 'utf-8');
                onUrl(decoder.decode(record.data));
                return;
            }
        }
    }, { signal: controller.signal });
    reader.scan({ signal: controller.signal }).catch((e) => {
        if (e && e.name === 'NotReadableError') onDisabled();
    });
    return controller;
}""",
)

private fun startNfcWrite(url: String, onWritten: (Boolean) -> Unit, onDisabled: () -> Unit): JsAny = js(
    """{
    const controller = new AbortController();
    new NDEFReader().write({ records: [{ recordType: 'url', data: url }] }, { signal: controller.signal }).then(
        () => onWritten(true),
        (e) => {
            if (e && e.name === 'AbortError') return;
            if (e && e.name === 'NotReadableError') onDisabled(); else onWritten(false);
        });
    return controller;
}""",
)

private fun abortNfc(controller: JsAny) {
    js("controller.abort()")
}
