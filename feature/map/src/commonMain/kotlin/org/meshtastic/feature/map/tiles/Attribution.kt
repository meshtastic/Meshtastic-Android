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
package org.meshtastic.feature.map.tiles

private val HTML_TAG = Regex("<[^>]*>")
private val REPEATED_WHITESPACE = Regex("\\s+")

/** Only the entities our own attribution strings actually use; this is not a general HTML decoder. */
private val HTML_ENTITIES =
    listOf("&copy;" to "©", "&nbsp;" to " ", "&lt;" to "<", "&gt;" to ">", "&quot;" to "\"", "&amp;" to "&")

/**
 * The attribution string as plain text.
 *
 * [RasterTileSpec.attributionHtml] is HTML because that is the form MapLibre's attribution ornament renders. A renderer
 * with no such ornament still has to show the credit — OpenStreetMap's and Esri's tile policies both require it — so
 * rather than drop it or print `&copy; <a href=…>`, the markup comes off here.
 *
 * `&amp;` is decoded last so an escaped entity does not turn into a live one.
 */
fun String.attributionPlainText(): String {
    val withoutTags = HTML_TAG.replace(this, "")
    val decoded = HTML_ENTITIES.fold(withoutTags) { text, (entity, char) -> text.replace(entity, char) }
    return REPEATED_WHITESPACE.replace(decoded, " ").trim()
}

/** The credits owed for a basemap and whatever overlays are drawn over it, joined for a single line of text. */
fun mapAttributionText(basemap: RasterTileSpec?, overlays: List<RasterTileSpec>): String =
    (listOfNotNull(basemap?.attributionHtml) + overlays.mapNotNull { it.attributionHtml })
        .map { it.attributionPlainText() }
        .filter { it.isNotBlank() }
        .distinct()
        .joinToString(" · ")
