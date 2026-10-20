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

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.PowerManager
import android.view.Display
import android.view.Surface
import androidx.annotation.RequiresApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.koin.core.annotation.Single
import java.util.concurrent.Executor
import kotlin.math.max
import kotlin.math.sqrt

private const val QUATERNION_SIZE = 4

private const val HEADING_ACCURACY_INDEX = 4
private const val NINETY_FIVE_PERCENT_SIGMAS = 1.96
private const val MAX_HEADING_ERROR_DEG = 180f

/**
 * Running hot, at most one pose per this long, the SENSOR_DELAY_UI period. The fused orientation provider ignores a
 * slower requested period (measured on a Pixel 6a: 55 Hz when asked for 15), so the pose is thinned here.
 */
private const val REDUCED_INTERVAL_NS = 66_667_000L

/**
 * Prefers [fused] and falls back to the platform rotation vector, or the geomagnetic one on devices without a
 * gyroscope. Running hot, either source is slowed.
 */
@Single
class AndroidCameraPoseProvider(context: Context, private val fused: FusedOrientationSource) : CameraPoseProvider {
    private val context = context.applicationContext

    override fun poseUpdates(): Flow<CameraPoseState> = callbackFlow {
        // Every callback runs on this one thread, away from the main thread that draws the overlay, so the session
        // needs no locking.
        val thread = HandlerThread("mesh-view-pose").apply { start() }
        val handler = Handler(thread.looper)
        val session = PoseSession(context, fused, handler) { trySend(it) }
        handler.post(session::start)
        awaitClose {
            handler.post {
                session.stop()
                thread.quitSafely()
            }
        }
    }
}

private class PoseSession(
    context: Context,
    private val fused: FusedOrientationSource,
    private val handler: Handler,
    private val send: (CameraPoseState) -> Unit,
) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val rotationSensor =
        sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
    private val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private val executor = Executor { handler.post(it) }

    private val sample = FloatArray(QUATERNION_SIZE)
    private val smoothed = FloatArray(QUATERNION_SIZE)
    private val matrix = FloatArray(ROTATION_MATRIX_SIZE)
    private var filter = OneEuroQuaternionFilter()
    private var last = CameraPoseState()
    private var thermal = HudThermal.NORMAL
    private var fusedHandle: AutoCloseable? = null
    private var sensorRegistered = false
    private var stopThermal: (() -> Unit)? = null
    private var stopped = false
    private var lastPublishedNs = 0L

    // Some fusions (measured on a Pixel 9 Pro) report a fixed pi/6 here rather than an estimate, so the value is
    // trusted only once it has moved.
    private var firstAccuracy: Float? = null
    private var accuracyIsLive = false

    private val reduced
        get() = thermal != HudThermal.NORMAL

    private val sensorListener =
        object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                event.toQuaternion(sample)
                publish(sample, event.timestamp, event.accuracy, event.headingAccuracyDeg(), trueNorth = false)
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }

    private val fusedListener = FusedOrientationSource.Listener { attitude, headingErrorDeg, trueNorth, timestampNs ->
        if (!stopped && fusedHandle != null) {
            val error = headingErrorDeg.toOneSigmaDeg()
            // An invalid heading error means an uncalibrated compass, so it asks for calibration like status 0.
            val calibration = if (error == null) SensorManager.SENSOR_STATUS_UNRELIABLE else CALIBRATION_UNKNOWN
            publish(attitude, timestampNs, calibration, error, trueNorth)
        }
    }

    fun start() {
        val power = powerManager
        if (power != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            stopThermal = power.watchThermal(executor, ::onThermal)
        }
        startOrientation()
    }

    fun stop() {
        stopped = true
        fusedHandle?.close()
        fusedHandle = null
        if (sensorRegistered) sensorManager?.unregisterListener(sensorListener)
        sensorRegistered = false
        stopThermal?.invoke()
    }

    private fun startOrientation() {
        var handle: AutoCloseable? = null
        handle = fused.start(reduced, executor, fusedListener) { onFusedFailure(handle) }
        fusedHandle = handle
        if (handle == null) startSensor()
    }

    private fun onFusedFailure(handle: AutoCloseable?) {
        if (stopped || handle == null || handle !== fusedHandle) return
        fusedHandle = null
        filter = OneEuroQuaternionFilter()
        startSensor()
    }

    private fun startSensor() {
        val sensor = rotationSensor
        val delay = if (reduced) SensorManager.SENSOR_DELAY_UI else SensorManager.SENSOR_DELAY_GAME
        sensorRegistered =
            sensor != null && sensorManager?.registerListener(sensorListener, sensor, delay, handler) == true
        if (!sensorRegistered) emit(CameraPoseState(hasSensor = false))
    }

    private fun onThermal(next: HudThermal) {
        if (stopped || next == thermal) return
        val wasReduced = reduced
        thermal = next
        if (reduced != wasReduced) restartOrientation()
        emit(last)
    }

    private fun restartOrientation() {
        val handle = fusedHandle
        if (handle != null) {
            fusedHandle = null
            handle.close()
            startOrientation()
        } else if (sensorRegistered) {
            sensorManager?.unregisterListener(sensorListener)
            startSensor()
        }
    }

    private fun publish(
        attitude: FloatArray,
        timestampNs: Long,
        calibration: Int,
        headingAccuracyDeg: Double?,
        trueNorth: Boolean,
    ) {
        if (reduced && timestampNs - lastPublishedNs < REDUCED_INTERVAL_NS) return
        lastPublishedNs = timestampNs
        filter.filter(attitude, timestampNs, smoothed)
        val rotation = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)?.rotation ?: Surface.ROTATION_0
        emit(
            CameraPoseState(
                pose = attitudeToCameraPose(smoothed, rotation, matrix),
                calibration = calibration,
                headingAccuracyDeg = headingAccuracyDeg,
                trueNorth = trueNorth,
            ),
        )
    }

    private fun emit(state: CameraPoseState) {
        last = state.copy(thermal = thermal)
        send(last)
    }

    private fun SensorEvent.headingAccuracyDeg(): Double? = headingAccuracyRad()?.let { raw ->
        if (firstAccuracy == null) firstAccuracy = raw
        if (raw != firstAccuracy) accuracyIsLive = true
        raw.takeIf { accuracyIsLive }?.let { Math.toDegrees(it.toDouble()) / NINETY_FIVE_PERCENT_SIGMAS }
    }
}

/** Rotation-vector values as (x, y, z, w); older sensors omit w, which is recovered from the unit norm. */
@Suppress("MagicNumber")
private fun SensorEvent.toQuaternion(out: FloatArray) {
    val x = values[0]
    val y = values[1]
    val z = values[2]
    out[0] = x
    out[1] = y
    out[2] = z
    out[3] = if (values.size > 3) values[3] else sqrt(max(0f, 1f - x * x - y * y - z * z))
}

/** The rotation vector's heading error estimate, which it reports in radians and as -1 when it has none. */
private fun SensorEvent.headingAccuracyRad(): Float? = values.getOrNull(HEADING_ACCURACY_INDEX)?.takeIf { it >= 0f }

/** A 95% heading error bound in degrees, as the one-sigma figure every other accuracy on screen uses. */
private fun Float.toOneSigmaDeg(): Double? = takeIf {
    it >= 0f && it < MAX_HEADING_ERROR_DEG
}
    ?.let { it / NINETY_FIVE_PERCENT_SIGMAS }

private fun Int.toHudThermal(): HudThermal = when {
    this >= PowerManager.THERMAL_STATUS_CRITICAL -> HudThermal.CRITICAL
    this >= PowerManager.THERMAL_STATUS_SEVERE -> HudThermal.HOT
    else -> HudThermal.NORMAL
}

/** Reports the current thermal status, then every change, and returns the call that stops it. */
@RequiresApi(Build.VERSION_CODES.Q)
private fun PowerManager.watchThermal(executor: Executor, onChange: (HudThermal) -> Unit): () -> Unit {
    onChange(currentThermalStatus.toHudThermal())
    val listener = PowerManager.OnThermalStatusChangedListener { onChange(it.toHudThermal()) }
    addThermalStatusListener(executor, listener)
    return { removeThermalStatusListener(listener) }
}
