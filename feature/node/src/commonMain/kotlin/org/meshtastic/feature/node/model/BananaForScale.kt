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
package org.meshtastic.feature.node.model

import kotlin.math.sqrt

/**
 * A real-size banana, 185 mm tip to stem, stood beside a device's model for scale. The model is bundled with the app,
 * built from a CC0 drawing by `scripts/models/banana_for_scale.py`: Y up, in meters, standing on y = 0 with its labeled
 * front facing +Z, like the flasher's models.
 */
object BananaForScale {
    /** The bundled model, as a compose resource path. */
    const val MODEL = "files/models/banana_for_scale.glb"

    /** The space between the banana and the device. */
    private const val GAP_METERS = 0.03f

    /** Where the banana goes in [device]'s coordinates: on its left, on its lowest point, centered front to back. */
    fun beside(device: GlbBounds, banana: GlbBounds): FloatArray = floatArrayOf(
        device.min[0] - GAP_METERS - banana.max[0],
        device.min[1] - banana.min[1],
        device.center[2] - banana.center[2],
    )

    /**
     * [device] with [banana] standing beside it as one GLB, framed by both and carrying both notices. The device's own
     * coordinates are unchanged, so its display glass stays where it was read. Null when the files cannot be merged.
     */
    fun stage(device: ByteArray, deviceBounds: GlbBounds, deviceCopyright: String?, banana: ByteArray): StagedModel? {
        val bananaBounds = GlbBounds.parse(banana) ?: return null
        val translation = beside(deviceBounds, bananaBounds)
        val copyright =
            listOfNotNull(deviceCopyright, GlbBounds.copyright(banana)).joinToString("\n\n").takeIf { it.isNotEmpty() }
        return GlbMerge.append(device, banana, translation, copyright)?.let {
            StagedModel(it, union(deviceBounds, bananaBounds, translation), copyright)
        }
    }

    /**
     * [bounds] grown about their center for the orbit camera to frame by. Side by side the two are wider than tall, and
     * framed by their longest side on a portrait screen the device runs off its edge.
     */
    fun framing(bounds: GlbBounds): GlbBounds {
        val center = bounds.center
        return GlbBounds(
            min = FloatArray(AXES) { center[it] + (bounds.min[it] - center[it]) * FRAMING_SCALE },
            max = FloatArray(AXES) { center[it] + (bounds.max[it] - center[it]) * FRAMING_SCALE },
        )
    }

    private const val FRAMING_SCALE = 1.4f

    /** [device] and the banana moved by [translation], together. */
    fun union(device: GlbBounds, banana: GlbBounds, translation: FloatArray): GlbBounds = GlbBounds(
        min = FloatArray(AXES) { minOf(device.min[it], banana.min[it] + translation[it]) },
        max = FloatArray(AXES) { maxOf(device.max[it], banana.max[it] + translation[it]) },
    )

    /**
     * Where the banana stands on a surface beside a device whose footprint is centered on the origin in [pose], clear
     * of it however far the device is turned about the surface's up.
     */
    fun besideOnSurface(device: GlbBounds, pose: RestPose, banana: GlbBounds): FloatArray {
        val footprint = pose.rotatedBounds(device)
        val halfWidth = (footprint.max[0] - footprint.min[0]) / 2f
        val halfDepth = (footprint.max[2] - footprint.min[2]) / 2f
        val reach = sqrt(halfWidth * halfWidth + halfDepth * halfDepth)
        return floatArrayOf(-reach - GAP_METERS - banana.max[0], -banana.min[1], -banana.center[2])
    }

    private const val AXES = 3
}

/** A model file ready to show, with the bounds to frame it by and the notice to show with it. */
class StagedModel(val glb: ByteArray, val bounds: GlbBounds, val copyright: String?)
