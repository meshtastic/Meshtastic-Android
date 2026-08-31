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
package org.meshtastic.feature.settings.tak

import androidx.compose.runtime.Composable

/**
 * Platform-specific composable that returns a launcher for exporting a TAK data package zip.
 *
 * @param dataPackageProvider suspend function producing the zip [ByteArray]
 * @return a lambda accepting the suggested file name to trigger the export
 */
@Composable
expect fun rememberDataPackageExporter(dataPackageProvider: suspend () -> ByteArray): (fileName: String) -> Unit
