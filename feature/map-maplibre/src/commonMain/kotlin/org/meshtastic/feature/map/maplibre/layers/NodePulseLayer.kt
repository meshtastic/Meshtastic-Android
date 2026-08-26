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
package org.meshtastic.feature.map.maplibre.layers

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import org.maplibre.compose.expressions.dsl.and
import org.maplibre.compose.expressions.dsl.asNumber
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.gt
import org.maplibre.compose.expressions.dsl.not
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.sources.Source
import org.meshtastic.core.common.util.nowSeconds
import org.meshtastic.core.model.Node
import org.meshtastic.feature.map.maplibre.geojson.NodeFeatureKeys
import org.meshtastic.feature.map.maplibre.style.MapColors

/**
 * A one-second halo under any node heard in the last few seconds.
 *
 * The Google flavor's `PulsingNodeChip` flashes white over the chip itself, which it can do because each marker there
 * is a composable. MapLibre draws markers as layer features, so the same idea becomes a circle that grows and fades
 * underneath — the shape a map usually uses to say "this one just spoke", and legible at a glance on either a light or
 * a dark basemap.
 *
 * Unclustered nodes only, matching Google: a halo under a cluster bubble would say nothing about which of its members
 * was heard.
 *
 * No ticking clock. Which nodes count as just-heard is decided whenever [nodes] changes, and a packet arriving is
 * exactly what changes it. Once the animation finishes the layer draws nothing, so a membership list going stale
 * between packets is invisible.
 */
@Composable
internal fun NodePulseLayer(nodes: List<Node>, source: Source) {
    val heardJustNow = remember(nodes) { nodes.heardJustNow(nowSeconds) }
    // Frozen with the membership so the filter cannot drift out from under a pulse already running.
    val cutoff = remember(heardJustNow) { (nowSeconds - RECENTLY_HEARD_SECONDS).toInt() }

    val progress = remember { Animatable(PULSE_FINISHED) }
    LaunchedEffect(heardJustNow) {
        if (heardJustNow.isNotEmpty()) {
            progress.snapTo(0f)
            progress.animateTo(PULSE_FINISHED, animationSpec = tween(PULSE_MILLIS, easing = LinearEasing))
        }
    }

    // The layer stays mounted and idles at zero opacity rather than existing only while a pulse runs. Layer addition
    // is queued onto the map thread, so a layer that lives for a single second can be removed again before it is ever
    // added — which is what the first attempt did, and why nothing drew at all.
    val fraction by progress.asState()
    val idle = heardJustNow.isEmpty() || fraction >= PULSE_FINISHED

    CircleLayer(
        id = "node-pulse",
        source = source,
        filter = !feature.has("point_count") and (feature[NodeFeatureKeys.LAST_HEARD].asNumber() gt const(cutoff)),
        color = const(MapColors.Highlight),
        opacity = const(if (idle) 0f else (PULSE_FINISHED - fraction) * PULSE_PEAK_OPACITY),
        radius = const(lerp(PULSE_START_DP.dp, PULSE_END_DP.dp, fraction)),
    )
}

/**
 * The nodes a pulse is for: those heard within [RECENTLY_HEARD_SECONDS] of [now].
 *
 * Takes [now] rather than reading the clock so it can be tested. A node whose `lastHeard` is ahead of the clock counts
 * as just-heard: device clocks drift, and a node reporting a time slightly in the future has still just been heard
 * from.
 */
internal fun List<Node>.heardJustNow(now: Long): List<Node> = filter { now - it.lastHeard <= RECENTLY_HEARD_SECONDS }

/** Pulse only for nodes heard within this many seconds, matching the Google flavor's own window. */
private const val RECENTLY_HEARD_SECONDS = 5L
private const val PULSE_MILLIS = 1000
private const val PULSE_FINISHED = 1f

/** Starts level with the node chip and grows past it, so the chip is never obscured. */
private const val PULSE_START_DP = 14
private const val PULSE_END_DP = 34
private const val PULSE_PEAK_OPACITY = 0.6f
