/*
 * Copyright (c) 2024 Meshtastic LLC
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

package com.geeksville.mesh

import org.junit.Assert
import org.junit.Test

class PositionTest {
    @Test
    fun degGood() {
        Assert.assertEquals(Position.degI(89.0), 890000000)
        Assert.assertEquals(Position.degI(-89.0), -890000000)

        Assert.assertEquals(Position.degD(Position.degI(89.0)), 89.0, 0.01)
        Assert.assertEquals(Position.degD(Position.degI(-89.0)), -89.0, 0.01)
    }

    @Test
    fun givenPositionCreatedWithoutTime_thenTimeIsSet() {
        val position = Position(37.1, 121.1, 35)
        Assert.assertTrue(position.time != 0)
    }

}
