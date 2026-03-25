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
package org.meshtastic.core.ui.util

import androidx.compose.runtime.Composable
import co.touchlab.kermit.Logger
import org.jetbrains.compose.resources.StringResource

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

/** JVM stub — Save file launcher is a no-op on desktop until implemented. */
@Composable
actual fun rememberSaveFileLauncher(
    onUriReceived: (org.meshtastic.core.common.util.MeshtasticUri) -> Unit
): (defaultFilename: String, mimeType: String) -> Unit = { _, _ ->
    Logger.w { "File saving not implemented on Desktop" }
}

@Composable
actual fun rememberRequestLocationPermission(onGranted: () -> Unit, onDenied: () -> Unit): () -> Unit = {
    Logger.w { "Location permissions not implemented on Desktop" }
    onDenied()
}

@Composable
actual fun rememberOpenLocationSettings(): () -> Unit = {
    Logger.w { "Location settings not implemented on Desktop" }
}
