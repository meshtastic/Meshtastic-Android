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
package org.meshtastic.core.ui.util

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import kotlinx.coroutines.await
import kotlin.js.Promise

// Compose writes this through the Async Clipboard API; the browser has no label or sensitive flag, so both are ignored.
@OptIn(ExperimentalComposeUiApi::class)
actual fun createClipEntry(text: String, label: String, sensitive: Boolean): ClipEntry = ClipEntry.withPlainText(text)

// Reads navigator.clipboard directly. A denied permission, an unfocused page or an insecure context resolve to null.
actual suspend fun Clipboard.readPlainText(): String? = readClipboardText().await<JsString?>()?.toString()

// Inside then(), a missing navigator.clipboard (insecure context) rejects like a denied read instead of throwing.
private fun readClipboardText(): Promise<JsString?> =
    js("Promise.resolve().then(() => navigator.clipboard.readText()).catch(() => null)")
