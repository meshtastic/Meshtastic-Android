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
@file:Suppress("MagicNumber", "ReturnCount", "TooManyFunctions")

package org.meshtastic.feature.map.terrain.spatial

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Turns a sideways hand movement into a spin. A look-and-pinch drag moves the hand only a few centimeters, so following
 * its angle around the middle barely turned the map; this turns the map by the distance moved, in the direction that
 * carries the grabbed point along with the hand.
 */
object SpatialWheelSpin {
    /** Radians per meter of hand travel, per meter of disk radius: twice the rim's own arc. */
    const val GAIN = 2f

    /**
     * Yaw change for a drag that started at [start] and is now at [current] (table x/z, meters, +z toward the viewer).
     * Grabbing the near half and moving right turns the map the other way from grabbing the far half, the way a wheel
     * turns under the hand.
     */
    fun yawDelta(start: Vec2, current: Vec2, diskRadius: Float): Float {
        val dx = current.x - start.x
        if (!dx.isFinite()) return 0f
        val side = if (start.y >= 0) 1f else -1f
        return side * dx * GAIN / max(diskRadius, 0.1f)
    }
}

/** A drag that follows the hand around the middle of the table, like turning a wheel. */
class SpatialWheelDrag(val startYaw: Float, x: Float, z: Float) {
    private var lastAngle: Float? = angle(x, z)

    /** Total turn since the drag began, which can run past a full turn. */
    var turned: Float = 0f
        private set

    val yaw: Float
        get() = startYaw + turned

    /** Moves the drag to a new point and returns the yaw to show. */
    fun update(x: Float, z: Float): Float {
        val angle = angle(x, z) ?: return yaw
        lastAngle?.let { turned += wrapped(angle - it) }
        lastAngle = angle
        return yaw
    }

    companion object {
        /** Points closer to the center than this, in meters, give no usable angle and are skipped. */
        const val DEAD_ZONE = 0.03f

        /**
         * The angle of a point around the center that a yaw about +y adds to: turning the table by `yaw` moves a point
         * from angle `a` to `a + yaw`. Null inside the dead zone.
         */
        fun angle(x: Float, z: Float): Float? {
            if (!x.isFinite() || !z.isFinite() || x * x + z * z < DEAD_ZONE * DEAD_ZONE) return null
            return atan2(-z, x)
        }

        /** An angle brought into -π..π. */
        fun wrapped(angle: Float): Float {
            val twoPi = (2 * PI).toFloat()
            var a = angle % twoPi
            if (a > PI) a -= twoPi
            if (a <= -PI) a += twoPi
            return a
        }
    }
}

/** Sizes of the compass bezel and the handles around the disk, in meters. */
object SpatialWheelLayout {
    /** Gap between the disk's edge and the bezel. */
    const val GAP = 0.001f

    /** Radial width of the bezel. */
    const val BEZEL_WIDTH = 0.022f

    /** How far the bezel's top stands above the highest point of the map's edge. */
    const val BEZEL_LIFT = 0.005f

    /** Size of the chamfer on the bezel's top edges. */
    const val CHAMFER = 0.004f

    /** The bezel never stands taller than this above the table's zero height. */
    const val MAXIMUM_TOP = 0.03f
    const val HANDLE_COUNT = 8
    const val SPOKE_LENGTH = 0.03f
    const val SPOKE_RADIUS = 0.0035f
    const val KNOB_RADIUS = 0.009f

    /** Distance from the center to the middle of a knob. */
    fun knobDistance(diskRadius: Float): Float = diskRadius + GAP + BEZEL_WIDTH + SPOKE_LENGTH

    /** How far the whole wheel reaches past the disk's edge. */
    const val REACH = GAP + BEZEL_WIDTH + SPOKE_LENGTH + KNOB_RADIUS

    /** Handle angles in the yaw sense, set between the compass points so none sits under a letter. */
    val handleAngles: List<Float> = List(HANDLE_COUNT) { ((it + 0.5) * 2 * PI / HANDLE_COUNT).toFloat() }

    /** x/z of a point at a distance from the center at an angle in the yaw sense. */
    fun point(distance: Float, angle: Float): Vec2 = Vec2(distance * cos(angle), -distance * sin(angle))

    data class CompassPoint(val letter: String, val angle: Float)

    /** The compass points and where they sit on the bezel. North is -z on the map, east +x. */
    val compassPoints: List<CompassPoint> =
        listOf(
            CompassPoint("N", (PI / 2).toFloat()),
            CompassPoint("E", 0f),
            CompassPoint("S", (-PI / 2).toFloat()),
            CompassPoint("W", PI.toFloat()),
        )
}

/**
 * A raised ring with chamfered top edges: inner wall, inner chamfer, flat top, outer chamfer, outer wall. Each face has
 * its own vertices so the edges stay crisp.
 */
class SpatialBezelMesh(
    innerRadius: Float,
    width: Float,
    bottom: Float,
    top: Float,
    chamfer: Float,
    segments: Int = 128,
) {
    val positions: FloatArray
    val normals: FloatArray
    val indices: IntArray

    private class Face(val ax: Float, val ay: Float, val bx: Float, val by: Float, val nx: Float, val ny: Float)

    init {
        val c = min(chamfer, min(width / 3, max(top - bottom, 0f) / 2))
        val inner = innerRadius
        val outer = innerRadius + width
        val wallTop = top - c
        val diagonal = (1 / sqrt(2.0)).toFloat()
        val faces =
            listOf(
                Face(inner, wallTop, inner, bottom, -1f, 0f),
                Face(inner + c, top, inner, wallTop, -diagonal, diagonal),
                Face(outer - c, top, inner + c, top, 0f, 1f),
                Face(outer, wallTop, outer - c, top, diagonal, diagonal),
                Face(outer, bottom, outer, wallTop, 1f, 0f),
            )
        val n = max(segments, 3)
        val pos = FloatArray(faces.size * (n + 1) * 2 * 3)
        val nrm = FloatArray(pos.size)
        val idx = IntArray(faces.size * n * 6)
        var v = 0
        var t = 0
        for (face in faces) {
            val base = v
            for (s in 0..n) {
                val angle = s.toFloat() / n * 2 * PI.toFloat()
                val dx = cos(angle)
                val dz = -sin(angle)
                for ((px, py) in listOf(face.ax to face.ay, face.bx to face.by)) {
                    pos[v * 3] = dx * px
                    pos[v * 3 + 1] = py
                    pos[v * 3 + 2] = dz * px
                    nrm[v * 3] = dx * face.nx
                    nrm[v * 3 + 1] = face.ny
                    nrm[v * 3 + 2] = dz * face.nx
                    v++
                }
            }
            // Front faces wind counter-clockwise seen from the side the normal points to.
            for (s in 0 until n) {
                val a0 = base + s * 2
                val b0 = a0 + 1
                val a1 = a0 + 2
                val b1 = a0 + 3
                intArrayOf(a0, a1, b0, a1, b1, b0).forEach { idx[t++] = it }
            }
        }
        positions = pos
        normals = nrm
        indices = idx
    }
}

/**
 * The wheel's turn as it is shown, stepped once per frame. A drag sets where the wheel is heading; each frame eases the
 * shown turn toward it, and a release with some speed keeps the wheel spinning until friction stops it. Times are in
 * seconds, angles in radians.
 */
class SpatialWheelMotion {
    private class Sample(val time: Double, val yaw: Float)

    /** The turn on screen. */
    var shown: Float = 0f
        private set

    /** Where the shown turn is heading. */
    var target: Float = 0f
        private set

    /** Speed of a released spin, zero when not coasting. */
    var velocity: Float = 0f
        private set

    var isResetting: Boolean = false
        private set

    private val samples = ArrayList<Sample>()

    val isCoasting: Boolean
        get() = velocity != 0f

    /** True while there is still something to move. */
    val isMoving: Boolean
        get() = isCoasting || shown != target

    /** Halts the wheel where it is and returns the turn a drag should start from. */
    fun grab(): Float {
        velocity = 0f
        isResetting = false
        target = shown
        samples.clear()
        return target
    }

    /** The hand has moved the wheel to [yaw] at [time]. */
    fun drag(yaw: Float, time: Double) {
        target = yaw
        samples.add(Sample(time, yaw))
        if (samples.first().time < time - VELOCITY_WINDOW * 2) samples.removeAt(0)
    }

    /** The hand let go at [time]: a quick enough spin carries on. */
    fun release(time: Double) {
        val speed = velocityOf(samples.map { it.time to it.yaw }, time)
        samples.clear()
        velocity = if (abs(speed) < MINIMUM_FLICK_SPEED) 0f else speed.coerceIn(-MAXIMUM_SPEED, MAXIMUM_SPEED)
    }

    /**
     * Stops a coast where it is. A swing back to north carries on, since the taps of the double tap that started it
     * also arrive here.
     */
    fun stop() {
        if (isResetting) return
        velocity = 0f
        target = shown
    }

    /** Swings back to north the short way round. */
    fun reset() {
        velocity = 0f
        samples.clear()
        val turns = round(shown / (2 * PI.toFloat()))
        shown -= turns * 2 * PI.toFloat()
        target = 0f
        isResetting = true
    }

    /** Advances one frame and returns true when the shown turn changed. */
    fun step(dt: Double): Boolean {
        if (!isMoving || dt <= 0) return false
        if (isCoasting) {
            target += velocity * dt.toFloat()
            velocity = decayed(velocity, dt)
        }
        val before = shown
        shown = eased(shown, target, dt, if (isResetting) RESET_TIME_CONSTANT else FOLLOW_TIME_CONSTANT)
        if (shown == target && !isCoasting) isResetting = false
        return shown != before
    }

    companion object {
        /** How quickly the shown turn catches up with the hand. */
        const val FOLLOW_TIME_CONSTANT = 0.07

        /** The slower ease a double tap uses to swing back to north. */
        const val RESET_TIME_CONSTANT = 0.1

        /** How quickly a released spin slows; it is all but stopped within a second. */
        const val FRICTION_TIME_CONSTANT = 0.22

        /** The span of hand movement the release speed is measured over. */
        const val VELOCITY_WINDOW = 0.1

        /** Releases slower than this don't coast, so letting go of a held wheel leaves it still. */
        const val MINIMUM_FLICK_SPEED = 0.6f

        /** A coast slows to this and stops. */
        const val STOP_SPEED = 0.15f
        const val MAXIMUM_SPEED = 8f

        /**
         * Moves [current] toward [target] by the share of the gap one step of [dt] closes, the same at any frame rate.
         */
        fun eased(current: Float, target: Float, dt: Double, timeConstant: Double): Float {
            if (dt <= 0 || timeConstant <= 0) return target
            val share = (1 - exp(-dt / timeConstant)).toFloat()
            val next = current + (target - current) * share
            return if (abs(target - next) < 1e-4f) target else next
        }

        /**
         * The speed over the samples (time, yaw) in the last [window] before [now], zero without enough of them. The
         * span runs to [now], so a hand that stopped before letting go reads as slower.
         */
        fun velocityOf(samples: List<Pair<Double, Float>>, now: Double, window: Double = VELOCITY_WINDOW): Float {
            val recent = samples.filter { it.first >= now - window }
            val first = recent.firstOrNull() ?: return 0f
            val last = recent.last()
            if (last.first - first.first < 0.01) return 0f
            val span = max(last.first, now) - first.first
            return ((last.second - first.second) / span).toFloat()
        }

        /** A coasting speed after friction has acted for [dt], zero once it is slow enough to stop. */
        fun decayed(velocity: Float, dt: Double): Float {
            val next = velocity * exp(-max(dt, 0.0) / FRICTION_TIME_CONSTANT).toFloat()
            return if (abs(next) < STOP_SPEED) 0f else next
        }
    }
}
