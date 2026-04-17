/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
@file:Suppress("TooManyFunctions")

package org.meshtastic.core.ui.util

import androidx.compose.runtime.Composable
import co.touchlab.kermit.Logger
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource
import org.meshtastic.core.common.util.CommonUri
import org.meshtastic.core.common.util.ioDispatcher
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.net.URI

/** JVM stub — NFC settings are not available on Desktop. */
@Composable
actual fun rememberOpenNfcSettings(): () -> Unit = { Logger.w { "NFC settings not available on JVM/Desktop" } }

/** JVM stub — toast messages are logged instead. */
@Composable actual fun rememberShowToast(): suspend (String) -> Unit = { message -> Logger.i { "Toast: $message" } }

/** JVM stub — toast messages are logged instead. */
@Composable
actual fun rememberShowToastResource(): suspend (StringResource) -> Unit = { _ -> Logger.i { "Toast (resource)" } }

/** JVM stub — map opening is not available on Desktop. */
@Composable
actual fun rememberOpenMap(): (latitude: Double, longitude: Double, label: String) -> Unit = { lat, lon, label ->
    Logger.i { "Open map: $lat, $lon ($label)" }
}

/** JVM stub — URL opening via Desktop browse API. */
@Composable
actual fun rememberOpenUrl(): (url: String) -> Unit = { url ->
    try {
        java.awt.Desktop.getDesktop().browse(java.net.URI(url))
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        Logger.w(e) { "Failed to open URL: $url" }
    }
}

/** JVM — Opens a native file dialog to save a file. */
@Composable
actual fun rememberSaveFileLauncher(
    onUriReceived: (CommonUri) -> Unit,
): (defaultFilename: String, mimeType: String) -> Unit = { defaultFilename, _ ->
    val dialog = FileDialog(null as Frame?, "Save File", FileDialog.SAVE)
    dialog.file = defaultFilename
    dialog.isVisible = true
    val file = dialog.file
    val dir = dialog.directory
    if (file != null && dir != null) {
        val path = File(dir, file)
        onUriReceived(CommonUri.parse(path.toURI().toString()))
    }
}

/** JVM — Opens a native file dialog to pick a file. */
@Composable
actual fun rememberOpenFileLauncher(onUriReceived: (CommonUri?) -> Unit): (mimeType: String) -> Unit = { _ ->
    val dialog = FileDialog(null as? Frame, "Open File", FileDialog.LOAD)
    dialog.isVisible = true
    val file = dialog.file
    val dir = dialog.directory
    if (file != null && dir != null) {
        val path = File(dir, file)
        onUriReceived(CommonUri.parse(path.toURI().toString()))
    }
}

/** JVM — Reads text from a file URI. */
@Composable
actual fun rememberReadTextFromUri(): suspend (uri: CommonUri, maxChars: Int) -> String? = { uri, maxChars ->
    withContext(ioDispatcher) {
        @Suppress("TooGenericExceptionCaught")
        try {
            val file = File(URI(uri.toString()))
            if (file.exists()) {
                file.bufferedReader().use { reader ->
                    val buffer = CharArray(maxChars)
                    val read = reader.read(buffer)
                    if (read > 0) String(buffer, 0, read) else null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            Logger.e(e) { "Failed to read text from URI: $uri" }
            null
        }
    }
}

/** JVM no-op — Keep screen on is not applicable on Desktop. */
@Composable
actual fun KeepScreenOn(enabled: Boolean) {
    // No-op on JVM/Desktop
}

/** JVM no-op — Desktop has no system back gesture. */
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // No-op on JVM/Desktop — no system back button
}

@Composable
actual fun rememberRequestLocationPermission(onGranted: () -> Unit, onDenied: () -> Unit): () -> Unit = {
    Logger.w { "Location permissions not implemented on Desktop" }
    onDenied()
}

@Composable
actual fun rememberOpenLocationSettings(): () -> Unit = { Logger.w { "Location settings not implemented on Desktop" } }

/** JVM no-op — Desktop does not require runtime Bluetooth permissions. */
@Composable
actual fun rememberRequestBluetoothPermission(onGranted: () -> Unit, onDenied: () -> Unit): () -> Unit = { onGranted() }

/** JVM no-op — Desktop does not require runtime notification permissions. */
@Composable
actual fun rememberRequestNotificationPermission(onGranted: () -> Unit, onDenied: () -> Unit): () -> Unit = {
    onGranted()
}

/** JVM — location permission is always considered granted on Desktop. */
@Composable actual fun isLocationPermissionGranted(): Boolean = true

/** JVM — GPS is never disabled on Desktop (concept doesn't apply). */
@Composable actual fun isGpsDisabled(): Boolean = false
