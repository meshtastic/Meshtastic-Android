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
package org.meshtastic.feature.map.hud

import java.util.concurrent.Executor

/**
 * An orientation fusion outside the platform sensors, such as Play services' Fused Orientation Provider. Each flavor
 * binds one; [NONE] is the flavor without it, and the camera pose falls back to the rotation vector.
 */
interface FusedOrientationSource {

    /**
     * Starts delivering samples on [executor], slower when [reduced], and returns the handle that stops them, or null
     * when the source is unavailable. [onFailure] runs on [executor] if the source fails after it was started.
     */
    fun start(reduced: Boolean, executor: Executor, listener: Listener, onFailure: () -> Unit): AutoCloseable?

    fun interface Listener {
        /**
         * [attitude] is the device-to-east-north-up quaternion as (x, y, z, w), the rotation vector's layout.
         * [headingErrorDeg] is the 95% half-cone of the heading error, 180 or more when unknown. [trueNorth] says the
         * attitude is referenced to true north.
         */
        fun onSample(attitude: FloatArray, headingErrorDeg: Float, trueNorth: Boolean, timestampNs: Long)
    }

    companion object {
        val NONE =
            object : FusedOrientationSource {
                override fun start(
                    reduced: Boolean,
                    executor: Executor,
                    listener: Listener,
                    onFailure: () -> Unit,
                ): AutoCloseable? = null
            }
    }
}
