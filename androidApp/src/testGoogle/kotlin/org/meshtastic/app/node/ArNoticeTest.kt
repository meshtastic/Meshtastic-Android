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
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException
import io.github.sceneview.ar.ARMotionSensorHealth
import io.github.sceneview.ar.ARSessionFailure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArNoticeTest {
    private val cameraBusy = ARSessionFailure.CameraNotAvailable(CameraNotAvailableException())
    private val declinedInstall = ARSessionFailure.UserDeclinedInstall(UnavailableUserDeclinedInstallationException())
    private val other = ARSessionFailure.Other(IllegalStateException("boom"))

    @Test
    fun `a busy camera names the camera and outranks a silent gyroscope`() {
        assertEquals(ArNotice.CAMERA_UNAVAILABLE, arNotice(cameraBusy, stalled = true, ARMotionSensorHealth.SILENT))
    }

    @Test
    fun `an install flow failure is left to the AR view's own overlay`() {
        assertEquals(ArNotice.NONE, arNotice(declinedInstall, stalled = false, sensors = null))
    }

    @Test
    fun `any other failure says AR could not start`() {
        assertEquals(ArNotice.SESSION_FAILED, arNotice(other, stalled = false, sensors = null))
    }

    @Test
    fun `a restart is asked for only on a stalled session with a measured silent gyroscope`() {
        assertEquals(ArNotice.RESTART_PHONE, arNotice(null, stalled = true, ARMotionSensorHealth.SILENT))
        assertEquals(ArNotice.NONE, arNotice(null, stalled = true, ARMotionSensorHealth.UNKNOWN))
        assertEquals(ArNotice.NONE, arNotice(null, stalled = true, ARMotionSensorHealth.DELIVERING))
        assertEquals(ArNotice.NONE, arNotice(null, stalled = true, sensors = null))
        assertEquals(ArNotice.NONE, arNotice(null, stalled = false, ARMotionSensorHealth.SILENT))
    }

    @Test
    fun `a stall needs frames, no tracking ever, and no reason from ARCore`() {
        assertTrue(arStartupStalled(framesArriving = true, everTracked = false, reason = null))
        assertTrue(arStartupStalled(framesArriving = true, everTracked = false, TrackingFailureReason.NONE))
        assertFalse(
            arStartupStalled(framesArriving = true, everTracked = false, TrackingFailureReason.INSUFFICIENT_LIGHT),
        )
        assertFalse(arStartupStalled(framesArriving = true, everTracked = true, reason = null))
        assertFalse(arStartupStalled(framesArriving = false, everTracked = false, reason = null))
    }

    @Test
    fun `the sensors are probed again only after a measurement that said nothing`() {
        assertTrue(arMotionProbeDue(stalled = true, probes = 0, last = null))
        assertTrue(arMotionProbeDue(stalled = true, probes = 1, ARMotionSensorHealth.UNKNOWN))
        assertFalse(arMotionProbeDue(stalled = true, probes = 1, ARMotionSensorHealth.SILENT))
        assertFalse(arMotionProbeDue(stalled = true, probes = 1, ARMotionSensorHealth.DELIVERING))
        assertFalse(arMotionProbeDue(stalled = true, probes = AR_MOTION_PROBES_MAX, ARMotionSensorHealth.UNKNOWN))
        assertFalse(arMotionProbeDue(stalled = false, probes = 0, last = null))
    }
}
