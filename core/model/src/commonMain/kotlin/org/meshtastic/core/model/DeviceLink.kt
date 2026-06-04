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

/**
 * A msh.to short-link associated with a piece of hardware. Imported from the bundled `urls.json` (sourced from the
 * meshtastic/msh.to repo). Every link resolves through the msh.to redirect service.
 *
 * @param shortCode the msh.to short code, e.g. `rak_wismeshtag`, `rokland-heltec-v3`.
 * @param originalUrl the destination URL recorded in `urls.json` (informational; the app links to msh.to).
 * @param description human-readable label shown to the user.
 * @param isVendor true when [shortCode] is itself a known device `platformioTarget` (the primary vendor link).
 * @param regions marketplace shipping regions (ISO 3166-1 alpha-2). `null` = vendor/variant (not region-filtered);
 *   empty = worldwide marketplace; non-empty = limited to the listed countries.
 */
@Serializable
data class DeviceLink(
    val shortCode: String,
    val originalUrl: String,
    val description: String? = null,
    val isVendor: Boolean = false,
    val regions: List<String>? = null,
) {
    /** The user-facing link, routed through the msh.to redirect service. */
    val url: String
        get() = "https://msh.to/$shortCode"
}
