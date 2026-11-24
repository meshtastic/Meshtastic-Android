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
package org.meshtastic.core.domain.usecase.settings

import org.meshtastic.proto.Routing
import org.meshtastic.sdk.AdminResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class RadioResponseResultTest {
    // Regression: the collapse dropped or mis-typed the routingError carried to the settings screen.
    @Test
    fun `a refused admin read reports its routing error`() {
        val result = AdminResult.RateLimited.toRadioResponseResult { RadioResponseResult.Success }
        assertIs<RadioResponseResult.Error>(result)
        assertEquals(Routing.Error.RATE_LIMIT_EXCEEDED, result.routingError)
    }

    // Regression: a catch-all Error for Timeout would stop the caller's deadline/connection messaging.
    @Test
    fun `a timeout and an unreachable node report nothing so the caller's own handling runs`() {
        assertNull(AdminResult.Timeout.toRadioResponseResult { RadioResponseResult.Success })
        assertNull(AdminResult.NodeUnreachable.toRadioResponseResult { RadioResponseResult.Success })
        assertEquals(
            RadioResponseResult.CannedMessages("x7"),
            AdminResult.Success(7).toRadioResponseResult { RadioResponseResult.CannedMessages("x$it") },
        )
    }
}
