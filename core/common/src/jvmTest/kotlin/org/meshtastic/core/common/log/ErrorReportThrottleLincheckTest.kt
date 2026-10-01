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
package org.meshtastic.core.common.log

import org.jetbrains.lincheck.datastructures.IntGen
import org.jetbrains.lincheck.datastructures.ModelCheckingOptions
import org.jetbrains.lincheck.datastructures.Operation
import org.jetbrains.lincheck.datastructures.Param
import kotlin.test.Test

/** Concurrent reports never exceed the per-signature limit or lose a suppressed count, including across eviction. */
class ErrorReportThrottleLincheckTest {
    // A frozen clock keeps every bucket in one window; maxKeys = 2 with three keys forces eviction.
    private val throttle = ErrorReportThrottle(limit = 2, windowMs = 1_000L, maxKeys = 2, nowMs = { 0L })

    @Operation
    fun acquire(@Param(gen = IntGen::class, conf = "0:2") key: Int): Int? = throttle.acquire("signature-$key")

    @Test
    fun concurrentReportsStayLinearizableAcrossEviction() = ModelCheckingOptions()
        .threads(2)
        .actorsPerThread(3)
        .iterations(20)
        .invocationsPerIteration(1_000)
        .check(this::class)
}
