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

/** Returns a launcher to open the platform's location settings. */
@Composable expect fun rememberOpenLocationSettings(): () -> Unit

/** Returns a launcher to open the platform's Bluetooth settings. */
@Composable expect fun rememberOpenBluetoothSettings(): () -> Unit

/** Returns a launcher to open the platform's Wi-Fi settings. */
@Composable expect fun rememberOpenWifiSettings(): () -> Unit

/**
 * Returns whether GPS/location services are currently disabled at the system level. Always `false` on platforms where
 * this concept doesn't apply.
 */
@Composable expect fun isGpsDisabled(): Boolean

/**
 * Returns whether Bluetooth is currently turned off at the system level (the adapter exists but is disabled). Always
 * `false` on devices without Bluetooth and on platforms where the concept doesn't apply.
 */
@Composable expect fun isBluetoothDisabled(): Boolean

/**
 * Returns whether the device currently lacks any transport that can back the network-scan discovery (no active Wi-Fi,
 * Ethernet, or VPN). Cellular alone is **not** sufficient — a carrier uplink does not place the device on the same
 * segment as a Meshtastic node — so a cellular-only state surfaces the "connect to Wi-Fi" hint. The function name is
 * historical: the original implementation checked Wi-Fi alone, later widened to Ethernet, and now also recognizes VPN
 * (ZeroTier/Tailscale) as a valid reachability path for a TCP node. The name is retained to avoid churning the
 * expect/actual contract and every consumer. Always `false` where the concept doesn't apply.
 */
@Composable expect fun isWifiUnavailable(): Boolean

/** Returns a function that opens this app's system settings page (where the user can change any permission). */
@Composable expect fun rememberOpenAppSettings(): () -> Unit

/**
 * Returns the reactive [PermissionUiState] for the location permissions, recomputed on `ON_RESUME`. On platforms
 * without runtime permissions the status is always [PermissionStatus.GRANTED].
 */
@Composable expect fun rememberLocationPermissionState(): PermissionUiState

/**
 * Returns the reactive [PermissionUiState] for the Bluetooth scan/connect permissions. On pre-Android-12 devices BLE
 * scanning is gated by the location permission, so the returned state delegates to [rememberLocationPermissionState].
 */
@Composable expect fun rememberBluetoothPermissionState(): PermissionUiState

/**
 * Returns the reactive [PermissionUiState] for the POST_NOTIFICATIONS permission. Always [PermissionStatus.GRANTED] on
 * API levels / platforms that don't gate notifications behind a runtime permission.
 */
@Composable expect fun rememberNotificationPermissionState(): PermissionUiState

/**
 * Returns the reactive [PermissionUiState] for the ACCESS_LOCAL_NETWORK permission. Always [PermissionStatus.GRANTED]
 * on API levels / platforms that don't gate local-network access behind a runtime permission.
 */
@Composable expect fun rememberLocalNetworkPermissionState(): PermissionUiState

/**
 * Returns the reactive [PermissionUiState] for the CAMERA permission. Always [PermissionStatus.GRANTED] on platforms
 * that don't require a runtime camera permission.
 */
@Composable expect fun rememberCameraPermissionState(): PermissionUiState
