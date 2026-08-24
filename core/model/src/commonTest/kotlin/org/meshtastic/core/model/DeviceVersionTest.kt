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
package org.meshtastic.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class DeviceVersionTest {

    @Test
    fun canParse() {
        assertEquals(10000, DeviceVersion("1.0.0").asInt)
        assertEquals(10101, DeviceVersion("1.1.1").asInt)
        assertEquals(12357, DeviceVersion("1.23.57").asInt)
        assertEquals(12357, DeviceVersion("1.23.57.abde123").asInt)
    }

    @Test
    fun twoPartVersionAppends_zero() {
        assertEquals(20700, DeviceVersion("2.7").asInt)
    }

    @Test
    fun invalidVersionReturns_zero() {
        assertEquals(0, DeviceVersion("invalid").asInt)
    }

    @Test
    fun comparisonIsCorrect() {
        kotlin.test.assertTrue(DeviceVersion("2.7.12") >= DeviceVersion("2.7.11"))
        kotlin.test.assertTrue(DeviceVersion("3.0.0") > DeviceVersion("2.8.1"))
        assertEquals(DeviceVersion("2.7.12"), DeviceVersion("2.7.12"))
        kotlin.test.assertFalse(DeviceVersion("2.6.9") >= DeviceVersion("2.7.0"))
    }

    /**
     * Regression for #3726: an unparseable / transient firmware string (which parses to 0) must be reported as UNKNOWN,
     * never TOO_OLD. Treating 0 as "ancient" is what triggered the false blocking "firmware too old" popup that
     * force-disconnected devices running perfectly valid firmware.
     */
    @Test
    fun checkStatus_unparseableIsUnknownNotTooOld() {
        assertEquals(FirmwareCheckStatus.UNKNOWN, DeviceVersion("").checkStatus)
        assertEquals(FirmwareCheckStatus.UNKNOWN, DeviceVersion("garbage").checkStatus)
        assertEquals(FirmwareCheckStatus.UNKNOWN, DeviceVersion("2.").checkStatus)
        assertEquals(FirmwareCheckStatus.UNKNOWN, DeviceVersion("0.0.0").checkStatus)
    }

    @Test
    fun checkStatus_classifiesKnownVersions() {
        assertEquals(FirmwareCheckStatus.TOO_OLD, DeviceVersion("2.2.0").checkStatus)
        assertEquals(FirmwareCheckStatus.SHOULD_UPDATE, DeviceVersion("2.5.0").checkStatus)
        assertEquals(FirmwareCheckStatus.OK, DeviceVersion("2.7.0").checkStatus)
        assertEquals(FirmwareCheckStatus.OK, DeviceVersion(DeviceVersion.MIN_FW_VERSION).checkStatus)
        assertEquals(FirmwareCheckStatus.SHOULD_UPDATE, DeviceVersion(DeviceVersion.ABS_MIN_FW_VERSION).checkStatus)
    }
}
