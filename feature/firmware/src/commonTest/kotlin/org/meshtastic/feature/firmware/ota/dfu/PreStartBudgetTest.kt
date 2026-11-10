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
package org.meshtastic.feature.firmware.ota.dfu

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.testTimeSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class PreStartBudgetTest {

    @Test
    fun `the budget stays under the bootloader 6 minute idle boot`() {
        assertTrue(PRE_START_BUDGET < 6.minutes)
    }

    @Test
    fun `a connect that finishes inside the budget runs to completion`() = runTest {
        val budget = PreStartBudget(5.minutes, testTimeSource)
        var connected = false
        budget.bounded {
            delay(4.minutes)
            connected = true
        }
        assertTrue(connected)
    }

    @Test
    fun `a connect still running when the budget ends fails as StartTimedOut`() = runTest {
        val budget = PreStartBudget(5.minutes, testTimeSource)
        delay(4.minutes)
        assertFailsWith<DfuException.StartTimedOut> { budget.bounded { delay(2.minutes) } }
        assertEquals(5.minutes.inWholeMilliseconds, currentTime, "cut off at the budget, not after the connect")
    }

    @Test
    fun `a retry after the budget is spent fails before connecting`() = runTest {
        val budget = PreStartBudget(5.minutes, testTimeSource)
        delay(5.minutes + 1.seconds)
        var attempted = false
        assertFailsWith<DfuException.StartTimedOut> { budget.bounded { attempted = true } }
        assertFailsWith<DfuException.StartTimedOut> { budget.check() }
        assertTrue(!attempted)
    }

    @Test
    fun `after START is accepted the budget no longer applies`() = runTest {
        val budget = PreStartBudget(5.minutes, testTimeSource)
        budget.markStarted()
        delay(10.minutes)
        budget.check()
        var reconnected = false
        budget.bounded {
            delay(2.minutes)
            reconnected = true
        }
        assertTrue(reconnected)
    }
}
