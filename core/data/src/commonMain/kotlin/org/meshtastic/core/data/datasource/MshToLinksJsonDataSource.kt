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
package org.meshtastic.core.data.datasource

import org.meshtastic.core.model.MshToMarketplace
import org.meshtastic.core.model.MshToRoute

/** Reads the bundled msh.to link data: `urls.json` (short codes) and `marketplaces.json` (region metadata). */
interface MshToLinksJsonDataSource {
    /** Routes from the bundled `urls.json`, or empty if missing/malformed. */
    fun loadRoutes(): List<MshToRoute>

    /** Marketplace metadata from the bundled `marketplaces.json`, keyed by marketplace identifier. */
    fun loadMarketplaces(): Map<String, MshToMarketplace>
}
