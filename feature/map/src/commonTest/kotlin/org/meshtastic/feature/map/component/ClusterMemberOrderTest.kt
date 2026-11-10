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
package org.meshtastic.feature.map.component

import org.meshtastic.core.model.Node
import org.meshtastic.proto.User
import kotlin.math.sign
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClusterMemberOrderTest {

    private fun entry(num: Int, title: String, favorite: Boolean = false, longName: String? = null) =
        ClusterMemberEntry(
            nodeNum = num,
            title = title,
            subtitle = "",
            node =
            longName?.let {
                Node(
                    num = num,
                    isFavorite = favorite,
                    user = User.Builder().also { wb -> wb.long_name = it }.build(),
                )
            } ?: Node(num = num, isFavorite = favorite),
        )

    @Test
    fun `numbers in names sort as numbers`() {
        val ordered = listOf(entry(1, "S10"), entry(2, "S6"), entry(3, "s7"), entry(4, "S15")).inDialogOrder()

        assertEquals(listOf("S6", "s7", "S10", "S15"), ordered.map { it.title })
    }

    @Test
    fun `a local display name decides the order over the broadcast long name`() {
        val renamed =
            ClusterMemberEntry(
                nodeNum = 1,
                title = "",
                subtitle = "",
                node =
                Node(
                    num = 1,
                    displayName = "Alpha",
                    user = User.Builder().also { wb -> wb.long_name = "Zulu" }.build(),
                ),
            )
        val plain = entry(2, "", longName = "Mike")

        assertEquals(listOf(1, 2), listOf(plain, renamed).inDialogOrder().map { it.nodeNum })
    }

    @Test
    fun `favorites come first`() {
        val ordered = listOf(entry(1, "Alpha"), entry(2, "Zulu", favorite = true)).inDialogOrder()

        assertEquals(listOf(2, 1), ordered.map { it.nodeNum })
    }

    @Test
    fun `the long name decides over a title that carries a heard-ago suffix`() {
        val ordered =
            listOf(
                entry(1, "B 9 hr. ago", longName = "Stack Five 10"),
                entry(2, "A 1 hr. ago", longName = "Stack Five 9"),
            )
                .inDialogOrder()

        assertEquals(listOf(2, 1), ordered.map { it.nodeNum })
    }

    @Test
    fun `leading zeros and long digit runs still compare by value`() {
        assertTrue(naturalCompare("N007", "N10") < 0)
        assertTrue(naturalCompare("N99999999999999999999", "N100000000000000000000") < 0)
        assertEquals(0, naturalCompare("abc", "ABC"))
    }

    @Test
    fun `digits from other scripts sort as text and keep the order consistent`() {
        val names = listOf("N٣", "N12", "N2", "N١٢")

        names.forEach { a ->
            names.forEach { b -> assertEquals(-naturalCompare(b, a).sign, naturalCompare(a, b).sign) }
        }
        assertTrue(naturalCompare("N2", "N12") < 0)
        // A run that starts with a non-ASCII digit is text, so it sorts after a number rather than as the number 3.
        assertTrue(naturalCompare("٣", "10") > 0)
    }
}
