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

// Web has no native clip-data object comparable to Android's `ClipData`/AWT's `StringSelection` — there is no
// `ClipEntry(nativeType)` constructor overload for wasmJs at all (confirmed against Compose Multiplatform's own
// wasmJs `ui` klib: only the common `ClipEntry.withPlainText` factory is present in that target's metadata). That
// factory is real, not a fallback: writing the returned `ClipEntry` to the system clipboard still goes through the
// genuine `navigator.clipboard` Async Clipboard API — that's handled inside Compose Multiplatform's own
// `Clipboard.setClipEntry()` implementation for this target, which every call site here already goes through (see
// `CopyIconButton.kt`, `QrDialog.kt`, etc.) — so no extra `navigator.clipboard.writeText` interop is needed here.
// `label`/`sensitive` have no browser equivalent (there is no OS-level "don't show a paste-preview toast" concept, nor
// a "mark as secret" clipboard flag in the Clipboard API), so — like the JVM/AWT actual's `sensitive` — both are
// accepted and ignored.
@OptIn(ExperimentalComposeUiApi::class)
actual fun createClipEntry(text: String, label: String, sensitive: Boolean): ClipEntry = ClipEntry.withPlainText(text)
