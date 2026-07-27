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

/**
 * Marks a [Throwable] as an *expected condition*: a state the app is designed to encounter and recover from, caused by
 * the environment rather than by a defect in this code.
 *
 * Bluetooth switched off, a runtime permission the user has not granted, location services disabled, and a deliberate
 * disconnect are all expected conditions. They deserve a log line — and are worth watching as a *rate* — but they are
 * not bugs anyone can act on, so they must never be recorded as exceptions in Crashlytics or Datadog RUM. Reporting
 * them buries genuine regressions during release triage.
 *
 * Implement this on exception types whose very existence means "the environment said no". Where an exception type is
 * shared between expected and genuine failures, keep the type clean and simply log the expected call site at
 * [Severity.Warn] instead — see [shouldReportAsException] for the rule both reporting sinks apply.
 */
interface ExpectedCondition {
    /**
     * Stable, low-cardinality label naming which condition occurred, e.g. `ble-bluetooth-disabled`. This is what makes
     * the condition countable as a rate in the log backend instead of an error, so keep it free of addresses, node ids,
     * timings and any other per-user detail.
     */
    val expectedConditionLabel: String
}

/** Cause chains are walked with a depth cap so a malformed or self-referential chain cannot spin. */
private const val MAX_CAUSE_DEPTH = 10

/**
 * Returns the [ExpectedCondition.expectedConditionLabel] of the first [ExpectedCondition] in this throwable's cause
 * chain, or `null` when nothing in the chain is an expected condition.
 *
 * The chain is walked because transport and coroutine machinery routinely wraps the original cause.
 */
fun Throwable.expectedConditionLabel(): String? {
    var current: Throwable? = this
    var depth = 0
    while (current != null && depth < MAX_CAUSE_DEPTH) {
        (current as? ExpectedCondition)?.let {
            return it.expectedConditionLabel
        }
        current = current.cause
        depth++
    }
    return null
}

/** Returns `true` when this throwable, or anything in its cause chain, is an [ExpectedCondition]. */
fun Throwable.isExpectedCondition(): Boolean = expectedConditionLabel() != null

/**
 * The single source of truth for "should this log line become a reported exception?".
 *
 * Both reporting sinks consult this so they cannot drift apart:
 * - **Crashlytics** calls `recordException` only when this returns `true`.
 * - **Datadog** has no equivalent hook — its SDK turns *any* log at `ERROR` or above into a RUM error, throwable or
 *   not. The writer therefore downgrades the emitted level to `WARN` when this returns `false`, which is the only way
 *   to keep an expected condition out of RUM error tracking while still emitting the log line.
 *
 * A log below [Severity.Error] is never reported by either sink, so warn-level logging remains the simplest way to
 * record an expected condition without reporting it.
 */
fun shouldReportAsException(severity: Severity, throwable: Throwable?): Boolean = when {
    severity < Severity.Error -> false

    // Structured-concurrency bookkeeping, never a defect. Crashlytics already filtered these; Datadog did not.
    throwable is CancellationException -> false

    throwable?.isExpectedCondition() == true -> false

    else -> true
}
