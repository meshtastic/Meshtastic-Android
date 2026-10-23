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
package org.meshtastic.app.node

import com.google.ar.core.TrackingFailureReason
import io.github.sceneview.ar.ARMotionSensorHealth
import io.github.sceneview.ar.ARSessionFailure

/** What the AR view says over the camera when the session cannot start, in place of the coaching card. */
enum class ArNotice {
    NONE,
    CAMERA_UNAVAILABLE,
    SESSION_FAILED,
    RESTART_PHONE,
}

/** How long camera frames may arrive without tracking, and without a reason, before the motion sensors are checked. */
const val AR_NEVER_TRACKED_MS = 10_000L

/** How many times one session listens to the motion sensors before giving up on a verdict. */
const val AR_MOTION_PROBES_MAX = 3

/**
 * The silent stall: a live camera image, no tracking ever, and nothing from ARCore to explain it. A session that
 * tracked and lost it is not this, and neither is one ARCore gives a reason for.
 */
fun arStartupStalled(framesArriving: Boolean, everTracked: Boolean, reason: TrackingFailureReason?): Boolean =
    framesArriving && !everTracked && (reason == null || reason == TrackingFailureReason.NONE)

/** Whether the sensors are still worth listening to: a verdict is final, a measurement that said nothing is retried. */
fun arMotionProbeDue(stalled: Boolean, probes: Int, last: ARMotionSensorHealth?): Boolean =
    stalled && probes < AR_MOTION_PROBES_MAX && (probes == 0 || last == ARMotionSensorHealth.UNKNOWN)

/**
 * A session failure outranks a stall. The install family is left to ARSceneView, whose own overlay offers the ARCore
 * install; a restart is asked for only on a measured silent gyroscope.
 */
fun arNotice(failure: ARSessionFailure?, stalled: Boolean, sensors: ARMotionSensorHealth?): ArNotice = when {
    failure is ARSessionFailure.CameraNotAvailable -> ArNotice.CAMERA_UNAVAILABLE
    failure != null && !failure.isInstallFlow() -> ArNotice.SESSION_FAILED
    stalled && sensors == ARMotionSensorHealth.SILENT -> ArNotice.RESTART_PHONE
    else -> ArNotice.NONE
}

private fun ARSessionFailure.isInstallFlow(): Boolean = this is ARSessionFailure.ArCoreNotInstalled ||
    this is ARSessionFailure.UserDeclinedInstall ||
    this is ARSessionFailure.ApkTooOld ||
    this is ARSessionFailure.SdkTooOld ||
    this is ARSessionFailure.DeviceNotCompatible
