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

// Android's host provides its NFC effects; desktop and iOS have none.
@Composable internal actual fun PlatformNfcScannerEffect(onResult: (String?) -> Unit, onNfcOff: () -> Unit) = Unit

@Composable
internal actual fun PlatformNfcWriterEffect(url: String, onResult: (Boolean) -> Unit, onNfcOff: () -> Unit) = Unit

internal actual val platformNfcSupported: Boolean = false
