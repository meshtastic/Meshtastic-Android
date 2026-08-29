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
package org.meshtastic.feature.map.layers

/**
 * Per-layer opacity, keyed the way each kind of layer is already identified elsewhere.
 *
 * Two key spaces share one map because they cannot collide: a built-in overlay is keyed by its catalogue id
 * (`hillshade`), an imported layer by its URI. The URI — not the id — is deliberate: a file-backed [MapLayerItem] gets
 * a fresh random id on every load, so an id key would lose the user's setting at the next start. `hiddenLayerUrls` keys
 * visibility the same way for the same reason.
 *
 * Stored as a `stringSet` preference of `key|:|value` entries, matching how `networkMapLayers` encodes its records —
 * DataStore has no map type.
 */
const val LAYER_OPACITY_OPAQUE: Float = 1f

private const val OPACITY_DELIMITER = "|:|"

/** This layer's opacity, or fully opaque when the user has never moved its slider. */
fun Map<String, Float>.opacityOf(key: String): Float = this[key] ?: LAYER_OPACITY_OPAQUE

/**
 * Encodes for storage, dropping every fully-opaque layer.
 *
 * Opaque is the default, so persisting it would add an entry for each layer the user ever touched and never remove one
 * — the set would only ever grow, including for layers long since deleted.
 */
fun encodeLayerOpacity(opacities: Map<String, Float>): Set<String> = opacities
    .filterValues { it < LAYER_OPACITY_OPAQUE }
    .mapTo(mutableSetOf()) { (key, opacity) ->
        "$key$OPACITY_DELIMITER${opacity.coerceIn(0f, LAYER_OPACITY_OPAQUE)}"
    }

/**
 * Decodes stored entries, dropping any that no longer parse.
 *
 * Split from the *last* delimiter: a key is an imported layer's URI, built from a user-chosen file name, so it may
 * contain the delimiter itself. The value never can.
 */
fun decodeLayerOpacity(entries: Set<String>): Map<String, Float> = entries
    .mapNotNull { entry ->
        if (!entry.contains(OPACITY_DELIMITER)) return@mapNotNull null
        val key = entry.substringBeforeLast(OPACITY_DELIMITER).takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        val opacity = entry.substringAfterLast(OPACITY_DELIMITER).toFloatOrNull() ?: return@mapNotNull null
        key to opacity.coerceIn(0f, LAYER_OPACITY_OPAQUE)
    }
    .toMap()
