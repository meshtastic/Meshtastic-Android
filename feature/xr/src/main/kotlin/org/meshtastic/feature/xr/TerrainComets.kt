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
@file:Suppress("MagicNumber")

package org.meshtastic.feature.xr

import androidx.xr.runtime.math.Pose
import androidx.xr.runtime.math.Quaternion
import androidx.xr.runtime.math.Vector3
import androidx.xr.scenecore.Entity
import org.meshtastic.feature.map.terrain.spatial.Vec3
import kotlin.math.sqrt

/** Where a comet's head is and which way it is flying. */
internal class CometPose(val position: Vec3, val direction: Vec3)

/** What a comet flies along: arcs end to end, each from one marker's top to the next, in one color. */
internal class CometPath(val legs: List<Pair<Vec3, Vec3>>, val color: Rgb) {
    /** Seconds on each leg: a steady speed, held so a short hop can be followed and a long one does not drag. */
    val durations: List<Double> = legs.map { (from, to) ->
        (distance(from, to) / SPEED).toDouble().coerceIn(SHORTEST_LEG, LONGEST_LEG)
    }

    val duration: Double = durations.sum()

    /** The head [elapsed] seconds after it set off, null once it has landed. */
    fun at(elapsed: Double): CometPose? {
        var start = 0.0
        for ((index, leg) in legs.withIndex()) {
            val length = durations[index]
            if (elapsed < start + length) {
                val t = ((elapsed - start) / length).toFloat().coerceIn(0f, 1f)
                val here = arcPoint(leg.first, leg.second, t)
                // Looking a little way along the arc, back from the far end so the last step still has a way to look.
                val ahead = arcPoint(leg.first, leg.second, t + LOOK)
                val behind = arcPoint(leg.first, leg.second, t - LOOK)
                return CometPose(here, Vec3(ahead.x - behind.x, ahead.y - behind.y, ahead.z - behind.z))
            }
            start += length
        }
        return null
    }

    private companion object {
        /** Model units a second: the table is most of a meter across. */
        const val SPEED = 0.6f
        const val SHORTEST_LEG = 0.4
        const val LONGEST_LEG = 1.6
        const val LOOK = 0.02f
    }
}

/**
 * A comet with its head at the origin and its tail back along -z: a bright head, then beads that shrink away from it.
 * Turned to face the way it flies, so the tail trails it.
 */
internal fun cometMeshData(color: Rgb): TerrainMeshData {
    val acc = MeshAccumulator()
    acc.sphere(0f, 0f, 0f, COMET_HEAD, color.mix(Rgb(1f, 1f, 1f), HEAD_LIGHTEN))
    for (bead in 1..COMET_BEADS) {
        val shrink = 1 - BEAD_SHRINK * bead / COMET_BEADS
        // Lighter than the line it flies along, or the tail would be lost against it; less so toward its end.
        val light = color.mix(Rgb(1f, 1f, 1f), TAIL_LIGHTEN * (COMET_BEADS - bead) / COMET_BEADS)
        acc.sphere(0f, 0f, -bead * BEAD_GAP, COMET_BEAD * shrink, light, rings = 4, segments = 6)
    }
    return acc.build()
}

/** One comet in the scene: hidden until it is sent, then moved along its path a frame at a time. */
internal class Comet(val path: CometPath, private val entity: Entity) {
    private var elapsed = IDLE

    val isFlying: Boolean
        get() = elapsed >= 0

    /** Sets it off from the start of its path. One already in the air carries on. */
    fun send() {
        if (!isFlying) elapsed = 0.0
    }

    fun step(dt: Double) {
        if (!isFlying) return
        val pose = path.at(elapsed)
        elapsed += dt
        if (pose == null) {
            elapsed = IDLE
            entity.setEnabled(false)
            return
        }
        val position = Vector3(pose.position.x, pose.position.y, pose.position.z)
        val heading = Vector3(pose.direction.x, pose.direction.y, pose.direction.z)
        entity.setPose(Pose(position, Quaternion.fromRotation(Vector3(0f, 0f, 1f), heading)))
        entity.setEnabled(true)
    }

    private companion object {
        const val IDLE = -1.0
    }
}

/**
 * The comets on the table. One flies in from a node along its link each time the radio hears it. A trace route's pair
 * take turns for as long as it is drawn: out along the way there, back along the way back, then a rest.
 */
internal class TerrainComets(private val links: Map<Long, Comet>, private val route: List<Comet>) {
    private var resting = 0.0
    private var next = 0
    private var demoClock = 0.0
    private var demoNext = 0
    private var retired = false

    /**
     * Called before the comets' entities are taken out of the scene. The frame loop can still hold these comets for a
     * frame after that, and moving an entity that is gone is an error.
     */
    fun retire() {
        retired = true
    }

    /** The radio heard this node. */
    fun heard(node: Long) {
        if (!retired) links[node]?.send()
    }

    /** [demo] sends a comet down each link in turn, for a map of made-up nodes that are never heard. */
    fun step(dt: Double, demo: Boolean) {
        if (retired) return
        links.values.forEach { it.step(dt) }
        route.forEach { it.step(dt) }
        if (route.isNotEmpty() && route.none { it.isFlying }) {
            resting += dt
            if (next > 0 || resting >= ROUTE_REST) {
                route[next].send()
                next = (next + 1) % route.size
                resting = 0.0
            }
        }
        if (demo && links.isNotEmpty()) {
            demoClock += dt
            if (demoClock >= DEMO_GAP) {
                demoClock = 0.0
                links.values.elementAt(demoNext++ % links.size).send()
            }
        }
    }

    private companion object {
        const val ROUTE_REST = 1.5
        const val DEMO_GAP = 1.2
    }
}

private fun distance(a: Vec3, b: Vec3): Float {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val dz = b.z - a.z
    return sqrt(dx * dx + dy * dy + dz * dz)
}

private const val COMET_HEAD = 0.0065f
private const val COMET_BEAD = 0.0045f
private const val COMET_BEADS = 6
private const val BEAD_GAP = 0.0075f
private const val BEAD_SHRINK = 0.55f
private const val HEAD_LIGHTEN = 0.5f
private const val TAIL_LIGHTEN = 0.35f
