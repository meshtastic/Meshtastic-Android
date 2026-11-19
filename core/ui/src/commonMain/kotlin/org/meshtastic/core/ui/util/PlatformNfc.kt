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

/**
 * The NFC tag reader a platform offers without any wiring from its host, the default of [LocalNfcScannerProvider]. It
 * reads tags while composed and stops when it leaves the composition.
 */
@Composable internal expect fun PlatformNfcScannerEffect(onResult: (String?) -> Unit, onNfcOff: () -> Unit)

/** Writes [url] to the next tag tapped while composed, the default of [LocalNfcWriterProvider]. */
@Composable internal expect fun PlatformNfcWriterEffect(url: String, onResult: (Boolean) -> Unit, onNfcOff: () -> Unit)

/** Whether the platform effects above read and write tags, the default of [LocalNfcScannerSupported]. */
internal expect val platformNfcSupported: Boolean
