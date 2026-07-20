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
package org.meshtastic.feature.map.offline

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class BurningManPackPolicyTest {

    private val policy = BurningManPackPolicy()

    @Test
    fun `outside area after event removes`() {
        assertEquals(
            PackAction.Remove,
            policy.reconcile(
                instant("2026-09-08T08:00:00Z"),
                PackLocation(37.3346, -122.0090, instant("2026-09-08T07:00:00Z")),
            ),
        )
    }

    @Test
    fun `no location before grace deadline retains`() {
        assertEquals(PackAction.Retain, policy.reconcile(instant("2026-09-10T08:00:00Z"), null))
    }

    @Test
    fun `no location at cleanup deadline removes`() {
        assertEquals(PackAction.Remove, policy.reconcile(instant("2026-09-12T07:00:00Z"), null))
    }

    @Test
    fun `inside area installs before cleanup when no manifest is present`() {
        assertEquals(
            PackAction.Install,
            policy.reconcile(
                instant("2026-09-07T20:00:00Z"),
                PackLocation(40.7864, -119.2065, instant("2026-09-07T19:00:00Z")),
            ),
        )
    }

    @Test
    fun `inside area installs during the grace period when no manifest is present`() {
        assertEquals(
            PackAction.Install,
            policy.reconcile(
                instant("2026-09-10T08:00:00Z"),
                PackLocation(40.7864, -119.2065, instant("2026-09-10T07:00:00Z")),
            ),
        )
    }

    @Test
    fun `manifest prevents a second installation`() {
        val installedPolicy =
            BurningManPackPolicy(
                manifest =
                BurningManPackManifest(
                    packId = "burning-man-2026",
                    sourceBuild = "2026-08-29",
                    installedAt = instant("2026-09-01T07:00:00Z"),
                    userSuppressed = false,
                ),
            )

        assertEquals(
            PackAction.Retain,
            installedPolicy.reconcile(
                instant("2026-09-07T20:00:00Z"),
                PackLocation(40.7864, -119.2065, instant("2026-09-07T19:00:00Z")),
            ),
        )
    }

    @Test
    fun `bounds include their edge and exclude points outside`() {
        val timestamp = instant("2026-09-07T19:00:00Z")

        assertTrue(policy.contains(PackLocation(40.722536, -119.287957, timestamp)))
        assertFalse(policy.contains(PackLocation(40.843421, -119.287957, timestamp)))
    }

    private fun instant(value: String): Instant = Instant.parse(value)
}
