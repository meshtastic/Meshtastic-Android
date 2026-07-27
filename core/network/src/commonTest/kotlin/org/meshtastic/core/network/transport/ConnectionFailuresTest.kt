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
package org.meshtastic.core.network.transport

import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConnectionFailuresTest {

    @Test
    fun `an IO failure is an expected connection failure`() {
        assertTrue(IOException("Connection reset by peer").isExpectedConnectionFailure())
    }

    @Test
    fun `an IO subclass is an expected connection failure`() {
        class TruncatedRead(message: String) : IOException(message)

        assertTrue(TruncatedRead("Not enough data available").isExpectedConnectionFailure())
    }

    @Test
    fun `a programming error is not an expected connection failure`() {
        assertFalse(IllegalStateException("Unexpected call to beginMessage()").isExpectedConnectionFailure())
        assertFalse(IllegalArgumentException("bad argument").isExpectedConnectionFailure())
        assertFalse(NullPointerException().isExpectedConnectionFailure())
    }
}
