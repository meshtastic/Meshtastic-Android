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
package org.meshtastic.feature.map.terrain

/**
 * Mapterhorn's two-tier PMTiles layout, ported from the sibling iOS app's `mapterhorn-data-contract.md`: a single
 * global archive always available at low zoom, plus per-region high-res archives keyed by their own z6 tile coordinate,
 * present only where source data coverage allows.
 *
 * No API key, no documented rate limit (Cloudflare R2-backed, per docs.mapterhorn.com/data-access) — same no-auth,
 * range-requestable shape as the base layer's Protomaps source.
 */
object MapterhornEndpoints {

    const val GLOBAL_PMTILES_URL = "https://download.mapterhorn.com/planet.pmtiles"

    /**
     * Plain XYZ tiles, the endpoint Mapterhorn's own migration guide documents.
     *
     * Probed 2026-09-16 at Seattle: z0–16 all 200 (~56–95 KB each, ~130 ms, Cloudflare-cached for a week,
     * `access-control-allow-origin: *`), z17+ 404. So it serves the regional detail the per-z6-tile archives were meant
     * to, and does it as independent cacheable requests rather than range reads into one seekable channel, which is
     * what made bulk terrain sampling serial.
     */
    fun tileUrl(zoom: Int, x: Int, y: Int): String = "https://tiles.mapterhorn.com/$zoom/$x/$y.webp"

    /** Deepest zoom [tileUrl] serves. Deeper requests 404. */
    const val TILES_MAX_ZOOM = 16

    /** Global coverage tops out at this zoom; deeper detail (if any) only exists in a regional archive. */
    const val GLOBAL_MAX_ZOOM = 12

    /** Regional archives start where global coverage ends. */
    const val REGIONAL_MIN_ZOOM = 13

    /** Regional archives never go deeper than this, even where source data would allow it. */
    const val REGIONAL_MAX_ZOOM = 18

    /** Each regional archive covers exactly one tile at this zoom. */
    const val REGIONAL_ARCHIVE_ZOOM = 6

    /**
     * The regional archive for one z6 [tile]. Mapterhorn publishes one only where it has high-resolution data, so a 404
     * here means global-only terrain for that tile, not an error.
     */
    fun regionalArchiveUrl(tile: TileIndex): String {
        require(tile.zoom == REGIONAL_ARCHIVE_ZOOM) { "Regional archives are keyed by z$REGIONAL_ARCHIVE_ZOOM tiles" }
        return "https://download.mapterhorn.com/$REGIONAL_ARCHIVE_ZOOM-${tile.x}-${tile.y}.pmtiles"
    }

    /**
     * What to show on screen whenever terrain (hillshade or contours) is actively rendering.
     *
     * Deliberately generic rather than an enumerated per-source credit: Mapterhorn's data is a mosaic of 100+ regional
     * datasets under a mix of licenses (CC-BY-4.0, CC0, national open-data licenses, US public domain — see
     * mapterhorn.com/attribution/, fetched and confirmed during this feature's research, not assumed), with no single
     * blanket license covering all of it. Computing which specific sources a given downloaded region actually draws
     * from would be the fully-correct answer, but is real additional engineering; this generic credit plus a link to
     * Mapterhorn's own attribution page is the honest, low-risk baseline both flavors should ship with now, upgradeable
     * to a per-region source list later without changing the UI shape that reads it.
     */
    const val ATTRIBUTION = "Terrain data © Mapterhorn"
    const val ATTRIBUTION_URL = "https://mapterhorn.com/attribution/"
}
