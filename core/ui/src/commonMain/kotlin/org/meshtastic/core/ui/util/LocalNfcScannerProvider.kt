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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

/** Reads NFC tags while composed; a host may provide its own, and the default is the platform's built-in reader. */
@Suppress("CompositionLocalAllowlist")
val LocalNfcScannerProvider =
    compositionLocalOf<@Composable (onResult: (String?) -> Unit, onNfcDisabled: () -> Unit) -> Unit> {
        { onResult, onNfcDisabled -> PlatformNfcScannerEffect(onResult, onNfcDisabled) }
    }

/** Whether NFC tags can be read and written here, so screens offer NFC only where it works. */
@Suppress("CompositionLocalAllowlist")
val LocalNfcScannerSupported = compositionLocalOf { platformNfcSupported }

/** Writes a URL to the next tag while composed; the default is the platform's built-in writer. */
@Suppress("CompositionLocalAllowlist")
val LocalNfcWriterProvider =
    compositionLocalOf<@Composable (url: String, onResult: (Boolean) -> Unit, onNfcDisabled: () -> Unit) -> Unit> {
        { url, onResult, onNfcDisabled -> PlatformNfcWriterEffect(url, onResult, onNfcDisabled) }
    }

/** Offers a share URL to NFC readers for as long as the composable stays composed. */
@Suppress("CompositionLocalAllowlist")
val LocalNfcEmulatorProvider = compositionLocalOf<@Composable (url: String) -> Unit> { {} }

/** True where the platform can emulate an NFC tag (Android host card emulation). */
@Suppress("CompositionLocalAllowlist")
val LocalNfcEmulationSupported = compositionLocalOf { false }
