/*
 * Copyright (c) 2025 Meshtastic LLC
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

package org.meshtastic.feature.node.compass

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.util.bearing
import org.meshtastic.core.model.util.latLongToMeter
import org.meshtastic.core.model.util.toDistanceString
import org.meshtastic.proto.ConfigProtos.Config.DisplayConfig.DisplayUnits
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

private const val ALIGNMENT_TOLERANCE_DEGREES = 5f
private const val FULL_CIRCLE_DEGREES = 360f
private const val BEARING_FORMAT = "%.0f°"
private const val MILLIS_PER_SECOND = 1000
private const val SECONDS_PER_HOUR = 3600
private const val SECONDS_PER_MINUTE = 60
private const val HUNDRED = 100f
private const val MILLIMETERS_PER_METER = 1000f

@HiltViewModel
/** Bridges heading + phone location into a single compass UI stream scoped to a target node. */
class CompassViewModel
@Inject
constructor(
    private val headingProvider: CompassHeadingProvider,
    private val phoneLocationProvider: PhoneLocationProvider,
    private val dispatchers: CoroutineDispatchers,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CompassUiState())
    val uiState: StateFlow<CompassUiState> = _uiState.asStateFlow()

    private var updatesJob: Job? = null
    private var targetPosition: Pair<Double, Double>? = null
    private var targetPositionProto: org.meshtastic.proto.MeshProtos.Position? = null
    private var targetPositionTimeSec: Long? = null

    fun start(node: Node, displayUnits: DisplayUnits) {
        val targetPos = node.validPosition?.let { node.latitude to node.longitude }
        targetPosition = targetPos
        targetPositionProto = node.position
        val targetColor = Color(node.colors.second)
        val targetName = node.user.longName.ifBlank { node.user.shortName.ifBlank { node.num.toString() } }
        targetPositionTimeSec = node.position.time.takeIf { it > 0 }?.toLong()

        _uiState.update {
            it.copy(
                targetName = targetName,
                targetColor = targetColor,
                hasTargetPosition = targetPos != null,
                displayUnits = displayUnits,
                positionTimeSec = targetPositionTimeSec,
            )
        }

        updatesJob?.cancel()

        updatesJob =
            viewModelScope.launch {
                combine(headingProvider.headingUpdates(), phoneLocationProvider.locationUpdates()) {
                        heading,
                        location,
                    ->
                    buildState(heading, location)
                }
                    .flowOn(dispatchers.default)
                    .collect { _uiState.value = it }
            }
    }

    fun stop() {
        updatesJob?.cancel()
        updatesJob = null
    }

    private fun buildState(headingState: HeadingState, locationState: PhoneLocationState): CompassUiState {
        val current = _uiState.value
        val warnings = buildWarnings(headingState, locationState)
        val target = targetPosition

        val positionalAccuracyMeters = target?.let { calculatePositionalAccuracyMeters() }
        val distanceMeters = calculateDistanceMeters(locationState, target)
        val bearingDegrees = calculateBearing(locationState, target)
        val distanceText = distanceMeters?.toDistanceString(current.displayUnits)
        val bearingText = bearingDegrees?.let { BEARING_FORMAT.format(it) }
        val isAligned = isAligned(headingState.heading, bearingDegrees)
        val lastUpdateText = targetPositionTimeSec?.let { formatElapsed(it) }
        val angularErrorDeg = calculateAngularError(positionalAccuracyMeters, distanceMeters)
        val errorRadiusText = positionalAccuracyMeters?.toInt()?.toDistanceString(current.displayUnits)

        return current.copy(
            heading = headingState.heading,
            bearing = bearingDegrees,
            distanceText = distanceText,
            bearingText = bearingText,
            warnings = warnings,
            isAligned = isAligned,
            lastUpdateText = lastUpdateText,
            errorRadiusText = errorRadiusText,
            angularErrorDeg = angularErrorDeg,
        )
    }

    private fun buildWarnings(headingState: HeadingState, locationState: PhoneLocationState): List<CompassWarning> =
        buildList {
            if (!headingState.hasSensor) add(CompassWarning.NO_MAGNETOMETER)
            if (!locationState.permissionGranted) {
                add(CompassWarning.NO_LOCATION_PERMISSION)
            } else if (!locationState.providerEnabled) {
                add(CompassWarning.LOCATION_DISABLED)
            } else if (!locationState.hasFix) {
                add(CompassWarning.NO_LOCATION_FIX)
            }
        }

    private fun calculateBearing(locationState: PhoneLocationState, target: Pair<Double, Double>?): Float? =
        if (canUseLocation(locationState, target)) {
            val location = locationState.location ?: return null
            val activeTarget = target ?: return null
            bearing(location.latitude, location.longitude, activeTarget.first, activeTarget.second).toFloat()
        } else {
            null
        }

    private fun calculateDistanceMeters(
        locationState: PhoneLocationState,
        target: Pair<Double, Double>?,
    ): Int? =
        if (canUseLocation(locationState, target)) {
            val location = locationState.location ?: return null
            val activeTarget = target ?: return null
            latLongToMeter(location.latitude, location.longitude, activeTarget.first, activeTarget.second).toInt()
        } else {
            null
        }

    private fun canUseLocation(locationState: PhoneLocationState, target: Pair<Double, Double>?): Boolean =
        target != null &&
            locationState.permissionGranted &&
            locationState.providerEnabled &&
            locationState.location != null

    private fun isAligned(heading: Float?, bearingDegrees: Float?): Boolean {
        if (heading == null || bearingDegrees == null) return false
        return angularDifference(heading, bearingDegrees) <= ALIGNMENT_TOLERANCE_DEGREES
    }

    private fun angularDifference(heading: Float, target: Float): Float {
        val diff = abs(heading - target) % FULL_CIRCLE_DEGREES
        return min(diff, FULL_CIRCLE_DEGREES - diff)
    }

    private fun formatElapsed(timestampSec: Long): String {
        val nowSec = System.currentTimeMillis() / MILLIS_PER_SECOND
        val diff = maxOf(0, nowSec - timestampSec)
        val hours = diff / SECONDS_PER_HOUR
        val minutes = (diff % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
        val seconds = diff % SECONDS_PER_MINUTE
        // Show a short elapsed string to match iOS behavior and avoid locale/format churn
        return "${hours}h ${minutes}m ${seconds}s ago"
    }

    private fun calculatePositionalAccuracyMeters(): Float? {
        val position = targetPositionProto ?: return null
        val positionTime = targetPositionTimeSec
        if (positionTime == null || positionTime <= 0) return null

        val gpsAccuracyMm = position.gpsAccuracy.toFloat()
        if (gpsAccuracyMm <= 0f) return null

        val dop: Float =
            when {
                position.getPDOP() > 0 -> position.getPDOP() / HUNDRED
                position.getHDOP() > 0 && position.getVDOP() > 0 ->
                    sqrt(
                        (position.getHDOP() / HUNDRED).toDouble().pow(2.0) +
                            (position.getVDOP() / HUNDRED).toDouble().pow(2.0),
                    ).toFloat()
                position.getHDOP() > 0 -> position.getHDOP() / HUNDRED
                else -> return null
            }

        return (gpsAccuracyMm / MILLIMETERS_PER_METER) * dop
    }

    private fun calculateAngularError(positionalAccuracyMeters: Float?, distanceMeters: Int?): Float? {
        val distance = distanceMeters ?: return null
        val accuracy = positionalAccuracyMeters ?: return null
        if (distance <= 0) return FULL_CIRCLE_DEGREES / 2

        val radians = atan2(accuracy.toDouble(), distance.toDouble())
        return Math.toDegrees(radians).toFloat().coerceIn(0f, FULL_CIRCLE_DEGREES / 2)
    }
}
