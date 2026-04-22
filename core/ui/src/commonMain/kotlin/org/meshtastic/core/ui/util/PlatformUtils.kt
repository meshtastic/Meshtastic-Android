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
import org.jetbrains.compose.resources.StringResource
import org.meshtastic.core.common.util.CommonUri

/** Returns a function to open the platform's NFC settings. */
@Composable expect fun rememberOpenNfcSettings(): () -> Unit

/** Returns a function to show a toast message. */
@Composable expect fun rememberShowToast(): suspend (String) -> Unit

/** Returns a function to show a toast message from a string resource. */
@Composable expect fun rememberShowToastResource(): suspend (StringResource) -> Unit

/** Returns a function to open the platform's map application at the given coordinates. */
@Composable expect fun rememberOpenMap(): (latitude: Double, longitude: Double, label: String) -> Unit

/** Returns a function to open the platform's browser with the given URL. */
@Composable expect fun rememberOpenUrl(): (url: String) -> Unit

/** Returns a launcher function to prompt the user to save a file. The callback receives the saved file URI. */
@Composable
expect fun rememberSaveFileLauncher(
    onUriReceived: (CommonUri) -> Unit,
): (defaultFilename: String, mimeType: String) -> Unit

/** Returns a launcher function to prompt the user to open/pick a file. The callback receives the selected file URI. */
@Composable expect fun rememberOpenFileLauncher(onUriReceived: (CommonUri?) -> Unit): (mimeType: String) -> Unit

/**
 * Returns a suspend function that reads up to [maxChars] characters of text from a [CommonUri]. Returns `null` if the
 * file is empty or cannot be read.
 */
@Composable expect fun rememberReadTextFromUri(): suspend (uri: CommonUri, maxChars: Int) -> String?

/** Keeps the screen awake while [enabled] is true. No-op on platforms that don't support it. */
@Composable expect fun KeepScreenOn(enabled: Boolean)

/** Intercepts the platform back gesture/button while [enabled] is true. No-op on platforms without a system back. */
@Composable expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)

/** Returns a launcher to request location permissions. */
@Composable expect fun rememberRequestLocationPermission(onGranted: () -> Unit, onDenied: () -> Unit = {}): () -> Unit

/** Returns a launcher to open the platform's location settings. */
@Composable expect fun rememberOpenLocationSettings(): () -> Unit

/** Returns a launcher to request Bluetooth scan + connect permissions. No-op on platforms without runtime BLE perms. */
@Composable expect fun rememberRequestBluetoothPermission(onGranted: () -> Unit, onDenied: () -> Unit = {}): () -> Unit

/** Returns a launcher to request the ACCESS_LOCAL_NETWORK permission. No-op on platforms that don't require it. */
@Composable
expect fun rememberRequestLocalNetworkPermission(onGranted: () -> Unit, onDenied: () -> Unit = {}): () -> Unit

/**
 * Returns whether ACCESS_LOCAL_NETWORK is currently granted. Always `true` on platforms / API levels that don't gate
 * local-network access behind a runtime permission.
 */
@Composable expect fun isLocalNetworkPermissionGranted(): Boolean

/** Returns a launcher to request the POST_NOTIFICATIONS permission. No-op on platforms that don't require it. */
@Composable
expect fun rememberRequestNotificationPermission(onGranted: () -> Unit, onDenied: () -> Unit = {}): () -> Unit

/**
 * Returns whether location permissions are currently granted. Always `true` on platforms without runtime permissions.
 */
@Composable expect fun isLocationPermissionGranted(): Boolean

/**
 * Returns whether GPS/location services are currently disabled at the system level. Always `false` on platforms where
 * this concept doesn't apply.
 */
@Composable expect fun isGpsDisabled(): Boolean
