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
package org.meshtastic.core.service

import org.meshtastic.core.common.state.RadioOperation
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.discovery_scan_in_progress
import org.meshtastic.core.resources.firmware_update_in_progress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NotificationTitleResourceTest {

    @Test
    fun noOperationLeavesTheConnectionStateTitleAlone() {
        assertNull(emptySet<RadioOperation>().notificationTitleResource())
    }

    @Test
    fun firmwareUpdateIsReported() {
        assertEquals(
            Res.string.firmware_update_in_progress,
            setOf(RadioOperation.FirmwareUpdate).notificationTitleResource(),
        )
    }

    @Test
    fun firmwareMaintenanceIsReportedAsAFirmwareUpdate() {
        assertEquals(
            Res.string.firmware_update_in_progress,
            setOf(RadioOperation.FirmwareMaintenance).notificationTitleResource(),
        )
    }

    @Test
    fun discoveryScanIsReported() {
        assertEquals(
            Res.string.discovery_scan_in_progress,
            setOf(RadioOperation.DiscoveryScan).notificationTitleResource(),
        )
    }

    @Test
    fun firmwareWorkOutranksAScan() {
        // The one the user must not interrupt wins the title.
        assertEquals(
            Res.string.firmware_update_in_progress,
            setOf(RadioOperation.DiscoveryScan, RadioOperation.FirmwareUpdate).notificationTitleResource(),
        )
    }
}
