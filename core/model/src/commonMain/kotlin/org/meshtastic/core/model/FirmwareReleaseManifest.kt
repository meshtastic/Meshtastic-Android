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
package org.meshtastic.core.model

import kotlinx.serialization.Serializable

/** Authoritative target catalogue referenced by a firmware release's `zip_url`. */
@Serializable
data class FirmwareReleaseManifest(val version: String = "", val targets: List<FirmwareTarget> = emptyList())

/** One firmware target declared by a [FirmwareReleaseManifest]. */
@Serializable data class FirmwareTarget(val board: String = "", val platform: String = "")
