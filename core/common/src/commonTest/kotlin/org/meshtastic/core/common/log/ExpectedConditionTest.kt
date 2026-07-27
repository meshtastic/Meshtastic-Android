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

import co.touchlab.kermit.Severity
import kotlinx.coroutines.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Locks down the reporting contract that both Crashlytics and Datadog RUM consult.
 *
 * Regressions here are silent and expensive: they either bury real crashes under expected-condition noise, or stop a
 * genuine defect from being reported at all.
 */
class ExpectedConditionTest {

    private class TestExpected(override val expectedConditionLabel: String = "test-expected") :
        IllegalStateException("expected"),
        ExpectedCondition

    // ── shouldReportAsException ──────────────────────────────────────────────────────

    @Test
    fun `severities below Error are never reported`() {
        val severities = listOf(Severity.Verbose, Severity.Debug, Severity.Info, Severity.Warn)
        severities.forEach { severity ->
            assertFalse(
                shouldReportAsException(severity, RuntimeException("boom")),
                "$severity with a throwable must not be reported",
            )
            assertFalse(shouldReportAsException(severity, null), "$severity without a throwable must not be reported")
        }
    }

    @Test
    fun `genuine errors are still reported`() {
        assertTrue(shouldReportAsException(Severity.Error, RuntimeException("boom")))
        assertTrue(shouldReportAsException(Severity.Assert, RuntimeException("boom")))
    }

    @Test
    fun `error without a throwable is still reported`() {
        // Crashlytics synthesises an Exception(message) for these, so the gate must stay open.
        assertTrue(shouldReportAsException(Severity.Error, null))
    }

    @Test
    fun `cancellation is never reported`() {
        assertFalse(shouldReportAsException(Severity.Error, CancellationException("cancelled")))
        assertFalse(shouldReportAsException(Severity.Assert, CancellationException("cancelled")))
    }

    @Test
    fun `expected conditions are never reported even at error severity`() {
        assertFalse(shouldReportAsException(Severity.Error, TestExpected()))
        assertFalse(shouldReportAsException(Severity.Assert, TestExpected()))
    }

    @Test
    fun `expected condition wrapped in a plain exception is not reported`() {
        val wrapped = RuntimeException("transport failed", TestExpected())
        assertFalse(shouldReportAsException(Severity.Error, wrapped))
    }

    @Test
    fun `a genuine failure carrying a cancellation cause is still reported`() {
        // Coroutine machinery routinely attaches a cancellation as the cause of an unrelated real failure.
        // Unwrapping the chain for CancellationException would silently drop these reports.
        val wrapped = RuntimeException("write failed", CancellationException("scope closed"))
        assertTrue(shouldReportAsException(Severity.Error, wrapped))
    }

    // ── Datadog downgrade rule (deliberately differs on cancellation) ────────────────

    @Test
    fun `datadog downgrades expected conditions`() {
        assertTrue(shouldDowngradeForDatadog(Severity.Error, TestExpected()))
        assertTrue(shouldDowngradeForDatadog(Severity.Assert, RuntimeException("outer", TestExpected())))
    }

    @Test
    fun `datadog keeps cancellation at error even though crashlytics drops it`() {
        // Load-bearing asymmetry: a cancellation logged at error means a call site swallowed it instead of
        // rethrowing — broken structured concurrency, and a real bug. This is the signal that surfaced #6468.
        val cancellation = CancellationException("cancelled")
        assertFalse(shouldReportAsException(Severity.Error, cancellation))
        assertFalse(shouldDowngradeForDatadog(Severity.Error, cancellation))
    }

    @Test
    fun `datadog leaves genuine errors and sub-error severities alone`() {
        assertFalse(shouldDowngradeForDatadog(Severity.Error, RuntimeException("boom")))
        assertFalse(shouldDowngradeForDatadog(Severity.Error, null))
        assertFalse(shouldDowngradeForDatadog(Severity.Warn, TestExpected()))
    }

    // ── cause-chain walking ──────────────────────────────────────────────────────────

    @Test
    fun `label is read from the throwable itself`() {
        assertEquals("test-expected", TestExpected().expectedConditionLabel())
        assertTrue(TestExpected().isExpectedCondition())
    }

    @Test
    fun `label is read through the cause chain`() {
        val wrapped = RuntimeException("outer", IllegalStateException("middle", TestExpected("ble-bluetooth-disabled")))
        assertEquals("ble-bluetooth-disabled", wrapped.expectedConditionLabel())
        assertTrue(wrapped.isExpectedCondition())
    }

    @Test
    fun `plain throwable has no label`() {
        val plain = RuntimeException("outer", IllegalStateException("inner"))
        assertNull(plain.expectedConditionLabel())
        assertFalse(plain.isExpectedCondition())
    }

    @Test
    fun `cause chain walking is depth limited`() {
        // A chain longer than the cap must terminate rather than spin, even though the marker is out of reach.
        var deep: Throwable = TestExpected()
        repeat(50) { deep = RuntimeException("layer", deep) }
        assertNull(deep.expectedConditionLabel())
        assertFalse(deep.isExpectedCondition())
    }

    @Test
    fun `self referential cause chain terminates`() {
        val selfReferencing =
            object : RuntimeException("loop") {
                override val cause: Throwable
                    get() = this
            }
        assertNull(selfReferencing.expectedConditionLabel())
    }
}
