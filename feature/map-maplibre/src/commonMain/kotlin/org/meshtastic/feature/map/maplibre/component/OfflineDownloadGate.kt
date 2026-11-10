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
package org.meshtastic.feature.map.maplibre.component

import org.maplibre.compose.offline.OfflineStorageState

/**
 * Whether the offline sheet may start a download. [isReady] is whether the storage reported
 * [OfflineStorageState.Ready]: [OfflineStorageState.Failed] is terminal and `create` throws its cause, and while
 * [OfflineStorageState.Loading] the sheet shows progress in place of an answer it does not have yet.
 */
internal fun canDownloadOfflinePack(isReady: Boolean, styleUrl: String?, estimate: Long): Boolean =
    isReady && styleUrl != null && estimate > 0L
