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
import org.jetbrains.compose.resources.StringResource

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
@Composable expect fun rememberSaveFileLauncher(onUriReceived: (org.meshtastic.core.common.util.MeshtasticUri) -> Unit): (defaultFilename: String, mimeType: String) -> Unit

/** Returns a launcher to request location permissions. */
@Composable expect fun rememberRequestLocationPermission(onGranted: () -> Unit, onDenied: () -> Unit = {}): () -> Unit

/** Returns a launcher to open the platform's location settings. */
@Composable expect fun rememberOpenLocationSettings(): () -> Unit
