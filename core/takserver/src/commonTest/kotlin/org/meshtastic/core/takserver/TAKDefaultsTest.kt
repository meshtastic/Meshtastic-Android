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
package org.meshtastic.core.takserver

import org.meshtastic.proto.MemberRole
import org.meshtastic.proto.Team
import org.meshtastic.proto.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TAKDefaultsTest {

    // ── toTakTeamName ──────────────────────────────────────────────────────────

    @Test
    fun `toTakTeamName returns default for null`() {
        assertEquals(DEFAULT_TAK_TEAM_NAME, null.toTakTeamName())
    }

    @Test
    fun `toTakTeamName returns default for Unspecifed_Color`() {
        assertEquals(DEFAULT_TAK_TEAM_NAME, Team.Unspecifed_Color.toTakTeamName())
    }

    @Test
    fun `toTakTeamName converts Blue`() {
        assertEquals("Blue", Team.Blue.toTakTeamName())
    }

    @Test
    fun `toTakTeamName converts Red`() {
        assertEquals("Red", Team.Red.toTakTeamName())
    }

    @Test
    fun `toTakTeamName replaces underscores with spaces`() {
        // Dark_Blue -> "Dark Blue"
        assertEquals("Dark Blue", Team.Dark_Blue.toTakTeamName())
    }

    // ── toTakRoleName ─────────────────────────────────────────────────────────

    @Test
    fun `toTakRoleName returns default for null`() {
        assertEquals(DEFAULT_TAK_ROLE_NAME, null.toTakRoleName())
    }

    @Test
    fun `toTakRoleName returns default for Unspecifed`() {
        assertEquals(DEFAULT_TAK_ROLE_NAME, MemberRole.Unspecifed.toTakRoleName())
    }

    @Test
    fun `toTakRoleName returns default for TeamMember`() {
        assertEquals(DEFAULT_TAK_ROLE_NAME, MemberRole.TeamMember.toTakRoleName())
    }

    @Test
    fun `toTakRoleName converts TeamLead`() {
        assertEquals("Team Lead", MemberRole.TeamLead.toTakRoleName())
    }

    @Test
    fun `toTakRoleName converts ForwardObserver`() {
        assertEquals("Forward Observer", MemberRole.ForwardObserver.toTakRoleName())
    }

    @Test
    fun `toTakRoleName falls back to enum name for other roles`() {
        // HQ is not specially mapped, so the fallback is its enum name
        assertEquals(MemberRole.HQ.name, MemberRole.HQ.toTakRoleName())
    }

    // ── toTakCallsign ─────────────────────────────────────────────────────────

    @Test
    fun `toTakCallsign prefers short_name`() {
        val user = User(id = "!1234", long_name = "Long Name", short_name = "SN")
        assertEquals("SN", user.toTakCallsign())
    }

    @Test
    fun `toTakCallsign falls back to long_name when short_name is blank`() {
        val user = User(id = "!1234", long_name = "Long Name", short_name = "")
        assertEquals("Long Name", user.toTakCallsign())
    }

    @Test
    fun `toTakCallsign falls back to id when both names are blank`() {
        val user = User(id = "!1234", long_name = "", short_name = "")
        assertEquals("!1234", user.toTakCallsign())
    }

    // ── keepalive / idle timeout constants ─────────────────────────────────────

    @Test
    fun `keepalive stale window is wider than keepalive interval`() {
        val staleMs = TAK_KEEPALIVE_INTERVAL_MS * TAK_KEEPALIVE_STALE_MULTIPLIER
        assertTrue(
            staleMs > TAK_KEEPALIVE_INTERVAL_MS,
            "Stale window ($staleMs ms) must exceed keepalive interval ($TAK_KEEPALIVE_INTERVAL_MS ms)",
        )
    }

    @Test
    fun `idle timeout exceeds keepalive stale window`() {
        val idleTimeoutMs = TAK_KEEPALIVE_INTERVAL_MS * TAK_READ_IDLE_TIMEOUT_MULTIPLIER
        val staleMs = TAK_KEEPALIVE_INTERVAL_MS * TAK_KEEPALIVE_STALE_MULTIPLIER
        assertTrue(idleTimeoutMs > staleMs, "Idle timeout ($idleTimeoutMs ms) must exceed stale window ($staleMs ms)")
    }
}
