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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FirmwareUpdateNoticePolicyTest {

    @Test
    fun `creates an informational OTA update notice for an older connected ESP32 node`() {
        val notice =
            FirmwareUpdateNoticePolicy.createNotice(
                nodeIdentity = "!12345678",
                currentVersion = "v2.7.15",
                stableVersion = "v2.7.16",
                hardware = DeviceHardware(architecture = "esp32-s3", platformioTarget = "t-echo"),
                transport = FirmwareUpdateTransport.Bluetooth,
                releaseTargets = setOf("t-echo"),
            )

        requireNotNull(notice)
        assertEquals(FirmwareUpdateDestination.AndroidUpdate, notice.destination)
        assertEquals(FirmwareUpdateNoticePresentation.Update, notice.presentation)
        assertEquals("2.7.15", notice.currentVersion)
        assertEquals("2.7.16", notice.stableVersion)
    }

    @Test
    fun `does not create a notice for missing malformed current equal or newer versions`() {
        val hardware = DeviceHardware(architecture = "esp32", platformioTarget = "t-beam")

        listOf(null, "", "unknown", "2.7.16", "2.8.0").forEach { currentVersion ->
            assertNull(
                FirmwareUpdateNoticePolicy.createNotice(
                    nodeIdentity = "!12345678",
                    currentVersion = currentVersion,
                    stableVersion = "v2.7.16",
                    hardware = hardware,
                    transport = FirmwareUpdateTransport.Bluetooth,
                    releaseTargets = setOf("t-beam"),
                ),
            )
        }
    }

    @Test
    fun `selects Meshtastic Flasher when Android cannot update the hardware over the active connection`() {
        val notice =
            FirmwareUpdateNoticePolicy.createNotice(
                nodeIdentity = "!12345678",
                currentVersion = "2.7.15",
                stableVersion = "2.7.16",
                hardware = DeviceHardware(architecture = "nrf52840", platformioTarget = "rak4631"),
                transport = FirmwareUpdateTransport.Tcp,
                releaseTargets = setOf("rak4631"),
            )

        assertEquals(FirmwareUpdateDestination.MeshtasticFlasher, notice?.destination)
    }

    @Test
    fun `dedupe key scopes notifications to node hardware target and stable version`() {
        val first = FirmwareUpdateNoticePolicy.notificationKey("!12345678", "t-beam", "v2.7.16")
        val sameStableVersion = FirmwareUpdateNoticePolicy.notificationKey("!12345678", "t-beam", "2.7.16")
        val newerStableVersion = FirmwareUpdateNoticePolicy.notificationKey("!12345678", "t-beam", "2.7.17")
        val differentTarget = FirmwareUpdateNoticePolicy.notificationKey("!12345678", "t-echo", "2.7.16")

        assertEquals(first, sameStableVersion)
        assertFalse(first == newerStableVersion)
        assertFalse(first == differentTarget)
    }

    @Test
    fun `does not create a notice when the stable manifest omits the hardware target`() {
        val notice =
            FirmwareUpdateNoticePolicy.createNotice(
                nodeIdentity = "!12345678",
                currentVersion = "2.7.15",
                stableVersion = "2.7.16",
                hardware = DeviceHardware(architecture = "esp32", platformioTarget = "t-beam"),
                transport = FirmwareUpdateTransport.Bluetooth,
                releaseTargets = setOf("t-echo"),
            )

        assertNull(notice)
    }

    @Test
    fun `dedupe only suppresses a previously scheduled notification`() {
        val key = FirmwareUpdateNoticePolicy.notificationKey("!12345678", "t-beam", "2.7.16")

        assertTrue(FirmwareUpdateNoticePolicy.shouldSchedule(key, alreadyScheduled = emptySet()))
        assertFalse(FirmwareUpdateNoticePolicy.shouldSchedule(key, alreadyScheduled = setOf(key)))
    }
}
