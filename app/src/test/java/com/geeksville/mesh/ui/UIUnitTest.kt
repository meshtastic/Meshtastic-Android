/*
 * Copyright (c) 2025 Meshtastic LLC
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

package com.geeksville.mesh.ui

import com.geeksville.mesh.model.getInitials
import org.junit.Assert.assertEquals
import org.junit.Test

class UIUnitTest {
    @Test
    fun initialsGood() {
        assertEquals("KH", getInitials("Kevin Hester"))
        assertEquals("KHLC", getInitials("  Kevin Hester Lesser Cat  "))
        assertEquals("", getInitials("  "))
        assertEquals("gksv", getInitials("geeksville"))
        assertEquals("geek", getInitials("geek"))
        assertEquals("gks1", getInitials("geeks1"))
    }

    @Test
    fun ignoreEmojisWhenCreatingInitials() {
        assertEquals("TG", getInitials("The \uD83D\uDC10 Goat"))
        assertEquals("TT", getInitials("The \uD83E\uDD14Thinker"))
        assertEquals("TCH", getInitials("\uD83D\uDC4F\uD83C\uDFFFThe Clapping Hands"))
        assertEquals("山羊", getInitials("山羊\uD83D\uDC10"))
    }
}
