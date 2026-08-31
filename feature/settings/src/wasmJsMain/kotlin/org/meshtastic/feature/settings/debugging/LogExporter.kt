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
package org.meshtastic.feature.settings.debugging

import androidx.compose.runtime.Composable

// No-op, matching core:ui's own rememberSaveFileLauncher() -- the general "save a file to disk" capability is
// already deferred on wasmJs at that layer; a Blob + <a download> click could implement this cheaply, but doing so
// only for log export would be an inconsistent, one-off web capability the rest of the app doesn't have.
@Composable actual fun rememberLogExporter(contentProvider: suspend () -> String): (fileName: String) -> Unit = { _ -> }

// No in-memory Kermit writer is installed for web yet (no webApp module exists to call
// Logger.setLogWriters(...) at startup, the way desktopApp does for InMemoryLogBuffer) -- same "no log capture"
// posture as iOS today, not a wasmJs-specific gap.
actual fun captureAppLogcat(): String = ""
