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
package org.meshtastic.core.ble

import co.touchlab.kermit.Severity
import com.juul.kable.UnmetRequirementReason
import org.meshtastic.core.common.log.isExpectedCondition
import org.meshtastic.core.common.log.shouldReportAsException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A BLE scan refused by the environment must never reach a crash reporter.
 *
 * Bluetooth off, location services off and a missing scan permission were four of the loudest Crashlytics issues during
 * 2.8.0 triage, all of them non-actionable.
 */
class BleScanStartExceptionTest {

    @Test
    fun `every scan-start failure is an expected condition`() {
        BleScanStartFailureReason.entries.forEach { reason ->
            val exception = BleScanStartException(reason, cause = IllegalStateException("cause"))
            assertTrue(exception.isExpectedCondition(), "$reason must be an expected condition")
            assertEquals(reason.label, exception.expectedConditionLabel)
        }
    }

    @Test
    fun `scan-start failures are never reported even when logged at error`() {
        BleScanStartFailureReason.entries.forEach { reason ->
            val exception = BleScanStartException(reason, cause = IllegalStateException("cause"))
            assertFalse(
                shouldReportAsException(Severity.Error, exception),
                "$reason must not be recorded as a non-fatal",
            )
        }
    }

    @Test
    fun `labels are unique and low cardinality`() {
        val labels = BleScanStartFailureReason.entries.map { it.label }
        assertEquals(labels.size, labels.toSet().size, "labels must be unique to be usable as rate keys")
        labels.forEach { label ->
            assertTrue(label.isNotBlank(), "label must not be blank")
            assertEquals(label.lowercase(), label, "label '$label' must be lowercase for stable grouping")
        }
    }

    // Kable's UnmetRequirementException has an internal constructor, so the reason mapping is verified directly.
    @Test
    fun `kable unmet-requirement reasons map onto scan-start reasons`() {
        assertEquals(
            BleScanStartFailureReason.BluetoothDisabled,
            UnmetRequirementReason.BluetoothDisabled.toBleScanStartFailureReason(),
        )
        assertEquals(
            BleScanStartFailureReason.LocationServicesDisabled,
            UnmetRequirementReason.LocationServicesDisabled.toBleScanStartFailureReason(),
        )
    }

    @Test
    fun `every kable unmet-requirement reason is mapped`() {
        // Guards against a Kable upgrade adding a reason that silently falls through to error-level logging.
        UnmetRequirementReason.entries.forEach { it.toBleScanStartFailureReason() }
    }
}
