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
package org.meshtastic.feature.connections

import org.meshtastic.core.model.Node
import org.meshtastic.proto.User
import kotlin.test.Test
import kotlin.test.assertEquals

class LiveUpdateCandidatesTest {
    private fun node(num: Int, lastHeard: Int, favorite: Boolean = false, ignored: Boolean = false, name: String = "") =
        Node(
            num = num,
            lastHeard = lastHeard,
            isFavorite = favorite,
            isIgnored = ignored,
            user =
            User.Builder()
                .also { wb ->
                    wb.id = "!" + num.toString(16).padStart(8, '0')
                    wb.long_name = name
                    wb.short_name = name.take(4)
                }
                .build(),
        )

    @Test
    fun `favorites come first and then the most recently heard`() {
        val stale = node(1, lastHeard = 100)
        val recent = node(2, lastHeard = 300)
        val staleFavorite = node(3, lastHeard = 50, favorite = true)
        val recentFavorite = node(4, lastHeard = 200, favorite = true)

        val ordered = liveUpdateCandidates(listOf(stale, recent, staleFavorite, recentFavorite), ourNodeNum = null)

        assertEquals(listOf(4, 3, 2, 1), ordered.map { it.num })
    }

    @Test
    fun `our own node and ignored nodes are not offered`() {
        val ours = node(1, lastHeard = 500)
        val ignored = node(2, lastHeard = 400, ignored = true)
        val other = node(3, lastHeard = 300)

        assertEquals(listOf(3), liveUpdateCandidates(listOf(ours, ignored, other), ourNodeNum = 1).map { it.num })
    }

    @Test
    fun `search matches long name short name and id ignoring case`() {
        val base = node(0x1a, lastHeard = 1, name = "Base Camp")
        val ridge = node(0x2b, lastHeard = 1, name = "Ridge")
        val candidates = listOf(base, ridge)

        assertEquals(listOf(base), filterLiveUpdateCandidates(candidates, "camp"))
        assertEquals(listOf(ridge), filterLiveUpdateCandidates(candidates, "RIDG"))
        assertEquals(listOf(ridge), filterLiveUpdateCandidates(candidates, "0000002b"))
        assertEquals(candidates, filterLiveUpdateCandidates(candidates, "  "))
    }
}
