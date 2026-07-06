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
package org.meshtastic.core.data.manager

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.nowSeconds
import org.meshtastic.core.model.geofence.activeWaypointPackets
import org.meshtastic.core.model.geofence.geofencesToMonitor
import org.meshtastic.core.model.geofence.toGeofence
import org.meshtastic.core.model.util.GeoConstants.DEG_D
import org.meshtastic.core.repository.MeshNotificationManager
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.NotificationPrefs
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.geofence
import org.meshtastic.core.resources.geofence_entered_body
import org.meshtastic.core.resources.geofence_entered_title
import org.meshtastic.core.resources.geofence_left_body
import org.meshtastic.core.resources.geofence_left_title
import org.meshtastic.core.resources.getStringSuspend
import org.meshtastic.core.resources.unknown_username
import org.meshtastic.proto.Position
import org.meshtastic.proto.Waypoint
import kotlin.concurrent.Volatile

/**
 * Raises a LOCAL notification when another mesh node's reported position crosses a waypoint's geofence. Evaluation runs
 * against OTHER nodes' positions arriving over the mesh — never the device's own location — so this is manual
 * point-in-region math, NOT the OS Geofencing API.
 *
 * Hooked from [MeshDataHandlerImpl.handlePosition]; holds the active geofence-bearing waypoints in memory (refreshed
 * from [PacketRepository.getWaypoints], normalised via [activeWaypointPackets] so stale/expired transmissions can't
 * fire). By default only waypoints created by THIS device are tracked — geofences are mesh-broadcast, so without that
 * filter every receiver in range would alert on the creator's crossings — but the user can opt in to specific foreign
 * geofences via [NotificationPrefs.geofenceAlertOptIns]. Crossing state lives in [GeofenceCrossingStore] (in-memory,
 * baseline-on-first-sighting).
 *
 * Received positions are funnelled through a single ordered worker so that two positions for the same node can never be
 * evaluated out of order (which would corrupt the inside/outside baseline and fire a spurious or missed alert).
 */
@Single
class GeofenceMonitor(
    private val packetRepository: Lazy<PacketRepository>,
    private val nodeManager: NodeManager,
    private val serviceNotifications: MeshNotificationManager,
    private val crossingStore: GeofenceCrossingStore,
    private val notificationPrefs: NotificationPrefs,
    @Named("ServiceScope") private val scope: CoroutineScope,
) {

    private data class PositionSample(val nodeNum: Int, val lat: Double, val lon: Double)

    @Volatile private var activeGeofences: List<Waypoint> = emptyList()

    // Unbounded so we never drop a position (which could swallow a real crossing); positions arrive infrequently.
    private val samples = Channel<PositionSample>(Channel.UNLIMITED)

    init {
        // Single serial consumer → evaluations happen in arrival order, and we launch ONE coroutine, not one per
        // received position.
        scope.launch {
            for (sample in samples) {
                try {
                    evaluate(sample.nodeNum, sample.lat, sample.lon)
                } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                    // Isolate per-sample failures: an unexpected throw must not kill the sole consumer and silently
                    // stop geofence tracking for the rest of the session.
                    Logger.e(e) { "Geofence evaluation failed for node ${sample.nodeNum}; skipping sample" }
                }
            }
        }
        // A bad emission must not tear down the snapshot for the rest of the session. Combined with myNodeNum (so the
        // creator-only filter recomputes once our node number is known — waypoints can arrive before we're connected)
        // and the opt-in set (so subscribing to a foreign geofence takes effect immediately).
        scope.launch {
            combine(
                packetRepository.value.getWaypoints(),
                nodeManager.myNodeNum,
                notificationPrefs.geofenceAlertOptIns,
            ) { packets, myNodeNum, optIns ->
                packets.activeWaypointPackets(nowSeconds).values.geofencesToMonitor(myNodeNum, optIns)
            }
                .catch { Logger.e(it) { "Geofence waypoint stream failed; geofence tracking paused" } }
                .collect { active ->
                    activeGeofences = active
                    crossingStore.retainOnly(active.map { it.id }.toSet())
                }
        }
    }

    /** Evaluate a received node position against every active geofence. [nodeNum] is the position's sender. */
    fun onPositionReceived(nodeNum: Int, myNodeNum: Int, position: Position) {
        val latI = position.latitude_i ?: 0
        val lonI = position.longitude_i ?: 0
        val lat = latI * DEG_D
        val lon = lonI * DEG_D
        // Skip our own position, a node with no fix (don't false-cross at 0,0), out-of-range/garbage coordinates, or
        // when nothing is geofenced.
        val skip =
            nodeNum == myNodeNum ||
                (latI == 0 && lonI == 0) ||
                lat !in MIN_LATITUDE..MAX_LATITUDE ||
                lon !in MIN_LONGITUDE..MAX_LONGITUDE ||
                activeGeofences.isEmpty()
        if (skip) return
        samples.trySend(PositionSample(nodeNum, lat, lon))
    }

    private suspend fun evaluate(nodeNum: Int, lat: Double, lon: Double) {
        val now = nowSeconds
        for (waypoint in activeGeofences) {
            // Re-check expiry per evaluation: the snapshot is only recomputed on a new waypoint emission, so a
            // waypoint that expired since the last emission must not keep firing.
            if (waypoint.expire == 0 || waypoint.expire.toLong() > now) {
                val geofence = waypoint.toGeofence() ?: continue
                when (crossingStore.update(waypoint.id, nodeNum, geofence.contains(lat, lon))) {
                    GeofenceTransition.ENTERED -> if (waypoint.notify_on_enter) notifyCrossing(waypoint, nodeNum, true)

                    GeofenceTransition.EXITED -> if (waypoint.notify_on_exit) notifyCrossing(waypoint, nodeNum, false)

                    GeofenceTransition.BASELINE,
                    GeofenceTransition.UNCHANGED,
                    -> Unit
                }
            }
        }
    }

    private suspend fun notifyCrossing(waypoint: Waypoint, nodeNum: Int, entered: Boolean) {
        // Favorites gate is receiver-local and resolved only on a real transition.
        if (waypoint.notify_favorites_only && nodeManager.nodeDBbyNodeNum[nodeNum]?.isFavorite != true) return

        try {
            val nodeName =
                nodeManager.nodeDBbyNodeNum[nodeNum]?.user?.long_name?.takeIf { it.isNotBlank() }
                    ?: getStringSuspend(Res.string.unknown_username)
            val waypointName = waypoint.name.takeIf { it.isNotBlank() } ?: getStringSuspend(Res.string.geofence)
            val title =
                getStringSuspend(
                    if (entered) Res.string.geofence_entered_title else Res.string.geofence_left_title,
                    waypointName,
                )
            val body =
                getStringSuspend(
                    if (entered) Res.string.geofence_entered_body else Res.string.geofence_left_body,
                    nodeName,
                    waypointName,
                )
            // Reuse the waypoint notification channel + map?waypointId= deep link. A distinct contactKey per
            // (waypoint, node) gives each crossing its own notification id (matches the Apple reference).
            serviceNotifications.updateWaypointNotification(
                contactKey = "geofence:${waypoint.id}:$nodeNum",
                name = title,
                message = body,
                waypointId = waypoint.id,
            )
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // A string-resource or notification failure must not take down the evaluation worker.
            Logger.e(e) { "Failed to raise geofence crossing notification for waypoint ${waypoint.id}" }
        }
    }

    private companion object {
        const val MIN_LATITUDE = -90.0
        const val MAX_LATITUDE = 90.0
        const val MIN_LONGITUDE = -180.0
        const val MAX_LONGITUDE = 180.0
    }
}
