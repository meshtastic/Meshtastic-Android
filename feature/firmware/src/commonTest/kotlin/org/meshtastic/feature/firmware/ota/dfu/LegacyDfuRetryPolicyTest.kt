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
@file:Suppress("MagicNumber")

package org.meshtastic.feature.firmware.ota.dfu

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Tests the Legacy DFU retry policy extracted into [runDfuRetryLoop]. The retry loop is driven entirely by lambdas — a
 * session-result supplier and a stale-reset cleanup hook — so the policy can be exercised without bringing up the full
 * BLE stack. Covers the drop/stale/success sequences that the mid-stream-disconnect recovery design depends on.
 */
class LegacyDfuRetryPolicyTest {

    /**
     * Sequence A: NORMAL upload drops mid-stream → stale cleanup → RECOVERY upload succeeds.
     *
     * Verifies:
     * - only 1 upload attempt is consumed before success (stale cleanup does NOT consume an upload attempt)
     * - RECOVERY profile is selected for the post-drop attempts
     * - exactly one stale-reset cleanup cycle runs
     * - the surfaced result is Success
     */
    @Test
    fun `drop then stale then success consumes one upload attempt and switches to RECOVERY`() = runTest {
        val sessions = mutableListOf<LegacyDfuStreamProfile>()
        var staleResets = 0

        val outcome =
            runDfuRetryLoop(
                protocol = DfuProtocolKind.LEGACY,
                // Confirmed-Legacy style budget: full budget before and after engagement.
                budget = DfuAttemptBudget(LEGACY_SESSION_ATTEMPTS, LEGACY_SESSION_ATTEMPTS),
                maxStaleResets = MAX_LEGACY_STALE_RESETS,
                runUploadSession = { profile ->
                    sessions.add(profile)
                    when (sessions.size) {
                        1 ->
                            DfuUploadResult.Failure(
                                LegacyDfuException.MidStreamDisconnect(
                                    20,
                                    200,
                                    "Disconnected",
                                    lastConfirmedBytes = -1,
                                ),
                                false,
                            )

                        2 -> DfuUploadResult.Failure(LegacyDfuException.StaleSessionReset(), true)

                        else -> DfuUploadResult.Success
                    }
                },
                resetStaleBootloader = { staleResets++ },
                interAttemptDelay = {},
            )

        assertEquals(DfuUploadResult.Success, outcome)
        assertEquals(
            listOf(LegacyDfuStreamProfile.NORMAL, LegacyDfuStreamProfile.RECOVERY, LegacyDfuStreamProfile.RECOVERY),
            sessions,
        )
        assertEquals(1, staleResets)
    }

    /**
     * Sequence B: drop → stale → RECOVERY drop → stale → RECOVERY success.
     *
     * Verifies the run fits within 3 upload attempts + 2 stale resets (the budget), each stale-reset cycle runs exactly
     * once per drop, and the final RECOVERY attempt succeeds.
     */
    @Test
    fun `drop stale drop stale success fits within 3 uploads and 2 stale resets`() = runTest {
        val sessions = mutableListOf<LegacyDfuStreamProfile>()
        var staleResets = 0

        val outcome =
            runDfuRetryLoop(
                protocol = DfuProtocolKind.LEGACY,
                budget = DfuAttemptBudget(LEGACY_SESSION_ATTEMPTS, LEGACY_SESSION_ATTEMPTS),
                maxStaleResets = MAX_LEGACY_STALE_RESETS,
                runUploadSession = { profile ->
                    sessions.add(profile)
                    // Alternating: attempts 1, 3 drop mid-stream; attempts 2, 4 are stale; attempt 5 succeeds.
                    when (sessions.size) {
                        1,
                        3,
                        ->
                            DfuUploadResult.Failure(
                                LegacyDfuException.MidStreamDisconnect(
                                    20,
                                    200,
                                    "Disconnected",
                                    lastConfirmedBytes = -1,
                                ),
                                false,
                            )

                        2,
                        4,
                        -> DfuUploadResult.Failure(LegacyDfuException.StaleSessionReset(), true)

                        else -> DfuUploadResult.Success
                    }
                },
                resetStaleBootloader = { staleResets++ },
                interAttemptDelay = {},
            )

        assertEquals(DfuUploadResult.Success, outcome)
        // 5 sessions total, all but the first are RECOVERY.
        assertEquals(5, sessions.size)
        assertEquals(LegacyDfuStreamProfile.NORMAL, sessions.first())
        sessions.drop(1).forEach { assertEquals(LegacyDfuStreamProfile.RECOVERY, it) }
        assertEquals(MAX_LEGACY_STALE_RESETS, staleResets)
    }

    /**
     * Repeated stale responses must stop at MAX_LEGACY_STALE_RESETS even though none of them consume an upload attempt
     * — otherwise a wedged bootloader would loop forever.
     */
    @Test
    fun `repeated stale responses stop at MAX_LEGACY_STALE_RESETS`() = runTest {
        var sessions = 0
        var staleResets = 0

        val outcome =
            runDfuRetryLoop(
                protocol = DfuProtocolKind.LEGACY,
                budget = DfuAttemptBudget(LEGACY_SESSION_ATTEMPTS, LEGACY_SESSION_ATTEMPTS),
                maxStaleResets = MAX_LEGACY_STALE_RESETS,
                runUploadSession = {
                    sessions++
                    DfuUploadResult.Failure(LegacyDfuException.StaleSessionReset(), true)
                },
                resetStaleBootloader = { staleResets++ },
                interAttemptDelay = {},
            )

        assertIs<DfuUploadResult.Failure>(outcome)
        assertIs<LegacyDfuException.StaleSessionReset>(outcome.error)
        // The loop tolerates MAX_LEGACY_STALE_RESETS stale responses that trigger cleanup; the next stale response
        // terminates the loop.
        assertEquals(MAX_LEGACY_STALE_RESETS + 1, sessions)
        assertEquals(MAX_LEGACY_STALE_RESETS, staleResets)
    }

    /**
     * Ordinary failures (not MidStreamDisconnect, not StaleSessionReset) consume the upload-attempt budget. After
     * `attempts` consecutive ordinary failures, the loop exits with the last failure.
     */
    @Test
    fun `ordinary failures consume the three upload attempts`() = runTest {
        var sessions = 0
        var staleResets = 0

        val outcome =
            runDfuRetryLoop(
                protocol = DfuProtocolKind.LEGACY,
                budget = DfuAttemptBudget(LEGACY_SESSION_ATTEMPTS, LEGACY_SESSION_ATTEMPTS),
                maxStaleResets = MAX_LEGACY_STALE_RESETS,
                runUploadSession = {
                    sessions++
                    DfuUploadResult.Failure(DfuException.TransferFailed("ordinary failure"), false)
                },
                resetStaleBootloader = { staleResets++ },
                interAttemptDelay = {},
            )

        assertIs<DfuUploadResult.Failure>(outcome)
        assertIs<DfuException.TransferFailed>(outcome.error)
        assertEquals(LEGACY_SESSION_ATTEMPTS, sessions)
        assertEquals(0, staleResets, "ordinary failures must NOT trigger stale cleanup")
    }

    /**
     * A StaleSessionReset must NOT consume an upload attempt — the cleanup cycle never tried to upload. After the stale
     * cleanup, the same upload-attempt budget remains, and a subsequent successful attempt succeeds.
     */
    @Test
    fun `stale cleanup does not consume an upload attempt`() = runTest {
        var sessions = 0
        var staleResets = 0

        val outcome =
            runDfuRetryLoop(
                protocol = DfuProtocolKind.LEGACY,
                budget = DfuAttemptBudget(LEGACY_SESSION_ATTEMPTS, LEGACY_SESSION_ATTEMPTS),
                maxStaleResets = MAX_LEGACY_STALE_RESETS,
                runUploadSession = {
                    sessions++
                    if (sessions == 1) {
                        DfuUploadResult.Failure(LegacyDfuException.StaleSessionReset(), true)
                    } else {
                        DfuUploadResult.Success
                    }
                },
                resetStaleBootloader = { staleResets++ },
                interAttemptDelay = {},
            )

        assertEquals(DfuUploadResult.Success, outcome)
        assertEquals(2, sessions, "stale cleanup must not consume an upload attempt — 2 sessions for 1 success")
        assertEquals(1, staleResets)
    }

    /**
     * The profile switches from NORMAL to RECOVERY only after a MidStreamDisconnect. A StaleSessionReset by itself must
     * NOT switch the profile — the link did not actually drop mid-upload.
     */
    @Test
    fun `NORMAL switches to RECOVERY only after MidStreamDisconnect`() = runTest {
        val sessions = mutableListOf<LegacyDfuStreamProfile>()

        // Initial stale session, then mid-stream drop, then success — only the post-drop attempt(s) should be RECOVERY.
        runDfuRetryLoop(
            protocol = DfuProtocolKind.LEGACY,
            budget = DfuAttemptBudget(LEGACY_SESSION_ATTEMPTS, LEGACY_SESSION_ATTEMPTS),
            maxStaleResets = MAX_LEGACY_STALE_RESETS,
            runUploadSession = { profile ->
                sessions.add(profile)
                when (sessions.size) {
                    1 -> DfuUploadResult.Failure(LegacyDfuException.StaleSessionReset(), true)

                    2 ->
                        DfuUploadResult.Failure(
                            LegacyDfuException.MidStreamDisconnect(20, 200, "Disconnected", lastConfirmedBytes = -1),
                            false,
                        )

                    else -> DfuUploadResult.Success
                }
            },
            resetStaleBootloader = {},
            interAttemptDelay = {},
        )

        // Session 1 (stale) stayed NORMAL; session 2 (drop) was still NORMAL; session 3 (post-drop) switched to
        // RECOVERY.
        assertEquals(
            listOf(LegacyDfuStreamProfile.NORMAL, LegacyDfuStreamProfile.NORMAL, LegacyDfuStreamProfile.RECOVERY),
            sessions,
        )
    }

    /**
     * An initial StaleSessionReset (no prior drop) keeps NORMAL — the most common case of a device stranded in the
     * bootloader from a previous interrupted flash.
     */
    @Test
    fun `initial StaleSessionReset keeps NORMAL profile`() = runTest {
        val sessions = mutableListOf<LegacyDfuStreamProfile>()

        runDfuRetryLoop(
            protocol = DfuProtocolKind.LEGACY,
            budget = DfuAttemptBudget(LEGACY_SESSION_ATTEMPTS, LEGACY_SESSION_ATTEMPTS),
            maxStaleResets = MAX_LEGACY_STALE_RESETS,
            runUploadSession = { profile ->
                sessions.add(profile)
                when (sessions.size) {
                    1 -> DfuUploadResult.Failure(LegacyDfuException.StaleSessionReset(), true)
                    else -> DfuUploadResult.Success
                }
            },
            resetStaleBootloader = {},
            interAttemptDelay = {},
        )

        sessions.forEach { assertEquals(LegacyDfuStreamProfile.NORMAL, it) }
    }

    /**
     * The original mid-stream failure stays visible even when the terminal failure is a later cleanup/transfer error.
     * The retry loop attaches the first MidStreamDisconnect as a suppressed exception on the surfaced error.
     */
    @Test
    fun `original MidStreamDisconnect stays visible when terminal failure differs`() = runTest {
        val outcome =
            runDfuRetryLoop(
                protocol = DfuProtocolKind.LEGACY,
                budget = DfuAttemptBudget(LEGACY_SESSION_ATTEMPTS, LEGACY_SESSION_ATTEMPTS),
                maxStaleResets = MAX_LEGACY_STALE_RESETS,
                runUploadSession = {
                    when (it) {
                        LegacyDfuStreamProfile.NORMAL ->
                            DfuUploadResult.Failure(
                                LegacyDfuException.MidStreamDisconnect(
                                    50,
                                    200,
                                    "Disconnected",
                                    lastConfirmedBytes = -1,
                                ),
                                false,
                            )

                        // All RECOVERY attempts fail with ordinary errors until the budget is exhausted.
                        else -> DfuUploadResult.Failure(DfuException.TransferFailed("recovery transfer failed"), false)
                    }
                },
                resetStaleBootloader = {},
                interAttemptDelay = {},
            )

        assertIs<DfuUploadResult.Failure>(outcome)
        val terminal = outcome.error
        assertIs<DfuException.TransferFailed>(terminal)
        assertEquals(1, terminal.suppressedExceptions.size)
        val suppressed = terminal.suppressedExceptions.single()
        assertIs<LegacyDfuException.MidStreamDisconnect>(suppressed)
        assertEquals(50, suppressed.bytesSent)
        assertEquals(200, suppressed.totalBytes)
    }

    /**
     * Secure DFU never throws the Legacy-only exceptions, so it just runs the `attempts` loop — no stale cleanup, no
     * profile switching.
     */
    @Test
    fun `Secure protocol runs the attempts loop without stale handling`() = runTest {
        val sessions = mutableListOf<LegacyDfuStreamProfile>()
        var staleResets = 0

        val outcome =
            runDfuRetryLoop(
                protocol = DfuProtocolKind.SECURE,
                // Secure budget is always 1/1 — pre==engaged, so engagement promotion is a no-op.
                budget = DfuAttemptBudget(LIMITED_SESSION_ATTEMPTS, LIMITED_SESSION_ATTEMPTS),
                maxStaleResets = MAX_LEGACY_STALE_RESETS,
                runUploadSession = { profile ->
                    sessions.add(profile)
                    DfuUploadResult.Failure(DfuException.TransferFailed("secure transfer failed"), false)
                },
                resetStaleBootloader = { staleResets++ },
                interAttemptDelay = {},
            )

        assertIs<DfuUploadResult.Failure>(outcome)
        assertIs<DfuException.TransferFailed>(outcome.error)
        assertEquals(LIMITED_SESSION_ATTEMPTS, sessions.size)
        // Secure never switches profile even if a MidStreamDisconnect-shaped failure surfaced (it cannot —
        // MidStreamDisconnect is a LegacyDfuException subtype).
        sessions.forEach { assertEquals(LegacyDfuStreamProfile.NORMAL, it) }
        assertEquals(0, staleResets)
    }

    /** Secure DFU succeeds as soon as the first session succeeds — no fallback, no profile switching. */
    @Test
    fun `Secure protocol returns Success on first successful session`() = runTest {
        val outcome =
            runDfuRetryLoop(
                protocol = DfuProtocolKind.SECURE,
                budget = DfuAttemptBudget(LIMITED_SESSION_ATTEMPTS, LIMITED_SESSION_ATTEMPTS),
                maxStaleResets = MAX_LEGACY_STALE_RESETS,
                runUploadSession = { DfuUploadResult.Success },
                resetStaleBootloader = {},
                interAttemptDelay = {},
            )

        assertEquals(DfuUploadResult.Success, outcome)
    }

    // -----------------------------------------------------------------------
    // Two-stage budget: pre-engagement probe → engaged promotion
    // -----------------------------------------------------------------------

    /**
     * Unknown Legacy pre-engagement connect failure: budget stays at the speculative 1-attempt cap so Secure fallback
     * stays timely. The probe never engaged, so no promotion occurs.
     */
    @Test
    fun `Unknown Legacy pre-engagement connect failure stops after 1 attempt for timely Secure fallback`() = runTest {
        var sessions = 0
        val outcome =
            runDfuRetryLoop(
                protocol = DfuProtocolKind.LEGACY,
                budget = DfuAttemptBudget(LIMITED_SESSION_ATTEMPTS, LEGACY_SESSION_ATTEMPTS),
                maxStaleResets = MAX_LEGACY_STALE_RESETS,
                runUploadSession = {
                    sessions++
                    DfuUploadResult.Failure(DfuException.ConnectionFailed("connect failed"), false)
                },
                resetStaleBootloader = {},
                interAttemptDelay = {},
            )

        assertIs<DfuUploadResult.Failure>(outcome)
        assertEquals(1, sessions, "pre-engagement budget must be 1 so Secure can fall back in time")
    }

    /**
     * Unknown Legacy engages then MidStreamDisconnect: budget promotes from 1 → 3 inside the loop, and subsequent
     * attempts use RECOVERY.
     */
    @Test
    fun `Unknown Legacy engagement during MidStreamDisconnect promotes budget to 3 and uses RECOVERY`() = runTest {
        val sessions = mutableListOf<LegacyDfuStreamProfile>()
        val outcome =
            runDfuRetryLoop(
                protocol = DfuProtocolKind.LEGACY,
                budget = DfuAttemptBudget(LIMITED_SESSION_ATTEMPTS, LEGACY_SESSION_ATTEMPTS),
                maxStaleResets = MAX_LEGACY_STALE_RESETS,
                runUploadSession = { profile ->
                    sessions.add(profile)
                    if (sessions.size == 1) {
                        // First attempt: engages, then drops mid-stream.
                        DfuUploadResult.Failure(
                            LegacyDfuException.MidStreamDisconnect(50, 200, "Disconnected", lastConfirmedBytes = -1),
                            true,
                        )
                    } else {
                        // Subsequent RECOVERY attempts fail ordinarily to exhaust the promoted budget.
                        DfuUploadResult.Failure(DfuException.TransferFailed("recovery failed"), true)
                    }
                },
                resetStaleBootloader = {},
                interAttemptDelay = {},
            )

        assertIs<DfuUploadResult.Failure>(outcome)
        assertEquals(3, sessions.size, "promoted engaged budget must allow 3 attempts")
        assertEquals(LegacyDfuStreamProfile.NORMAL, sessions.first())
        sessions.drop(1).forEach { assertEquals(LegacyDfuStreamProfile.RECOVERY, it) }
    }

    /**
     * Unknown Legacy engages, drop → stale → success: the stale cleanup after engagement does NOT consume the promoted
     * budget. Three upload sessions total (drop, stale, success) but only one upload-attempt budget slot consumed
     * before success.
     */
    @Test
    fun `Unknown Legacy stale cleanup after engagement does not consume promoted budget`() = runTest {
        val sessions = mutableListOf<LegacyDfuStreamProfile>()
        var staleResets = 0
        val outcome =
            runDfuRetryLoop(
                protocol = DfuProtocolKind.LEGACY,
                budget = DfuAttemptBudget(LIMITED_SESSION_ATTEMPTS, LEGACY_SESSION_ATTEMPTS),
                maxStaleResets = MAX_LEGACY_STALE_RESETS,
                runUploadSession = { profile ->
                    sessions.add(profile)
                    when (sessions.size) {
                        1 ->
                            DfuUploadResult.Failure(
                                LegacyDfuException.MidStreamDisconnect(
                                    50,
                                    200,
                                    "Disconnected",
                                    lastConfirmedBytes = -1,
                                ),
                                true,
                            )

                        2 -> DfuUploadResult.Failure(LegacyDfuException.StaleSessionReset(), true)

                        else -> DfuUploadResult.Success
                    }
                },
                resetStaleBootloader = { staleResets++ },
                interAttemptDelay = {},
            )

        assertEquals(DfuUploadResult.Success, outcome)
        assertEquals(3, sessions.size, "drop+stale+success = 3 sessions; stale cleanup does not consume budget")
        assertEquals(1, staleResets)
    }

    /**
     * A Legacy alternate that engages (e.g. SecureObserved primary fails after engagement → Legacy fallback) receives
     * the full Legacy engaged budget of 3.
     */
    @Test
    fun `Legacy alternate that engages receives full engaged Legacy budget`() = runTest {
        val sessions = mutableListOf<LegacyDfuStreamProfile>()
        val outcome =
            runDfuRetryLoop(
                protocol = DfuProtocolKind.LEGACY,
                // Legacy alternate: pre=1 (probe), engaged=3 (full Legacy budget).
                budget = DfuAttemptBudget(LIMITED_SESSION_ATTEMPTS, LEGACY_SESSION_ATTEMPTS),
                maxStaleResets = MAX_LEGACY_STALE_RESETS,
                runUploadSession = { profile ->
                    sessions.add(profile)
                    // Engages immediately, then fails until the budget is exhausted.
                    DfuUploadResult.Failure(DfuException.TransferFailed("legacy alt failure"), true)
                },
                resetStaleBootloader = {},
                interAttemptDelay = {},
            )

        assertIs<DfuUploadResult.Failure>(outcome)
        assertEquals(3, sessions.size, "Legacy alternate that engages must receive full engaged budget")
    }

    /**
     * Secure never receives a Legacy-style engaged promotion: its budget carries pre==engaged==1, so even when a Secure
     * session reports protocolEngaged=true the active attempt cap stays at 1.
     */
    @Test
    fun `Secure budget stays at 1 even when Secure session reports protocolEngaged true`() = runTest {
        var sessions = 0
        val outcome =
            runDfuRetryLoop(
                protocol = DfuProtocolKind.SECURE,
                budget = DfuAttemptBudget(LIMITED_SESSION_ATTEMPTS, LIMITED_SESSION_ATTEMPTS),
                maxStaleResets = MAX_LEGACY_STALE_RESETS,
                runUploadSession = {
                    sessions++
                    // Even with protocolEngaged=true, the Secure budget cannot grow (pre==engaged==1).
                    DfuUploadResult.Failure(DfuException.TransferFailed("secure engages then fails"), true)
                },
                resetStaleBootloader = {},
                interAttemptDelay = {},
            )

        assertIs<DfuUploadResult.Failure>(outcome)
        assertEquals(1, sessions, "Secure budget must stay at 1 even when protocolEngaged=true (no Legacy promotion)")
    }

    // -----------------------------------------------------------------------
    // Cleanup policy (cleanupDfuSessionTransport)
    // -----------------------------------------------------------------------

    /** Fake transport for cleanup-policy tests — records abort/close calls and can inject failures. */
    private class FakeCleanupTransport(val abortAction: () -> Unit = {}, val closeAction: () -> Unit = {}) :
        DfuUploadTransport {
        var abortCalled = false
        var closeCalled = false

        override val isLowSpeedTransfer: Boolean = false

        override suspend fun connectToDfuMode(): Result<Unit> = Result.success(Unit)

        override suspend fun transferInitPacket(initPacket: ByteArray): Result<Unit> = Result.success(Unit)

        override suspend fun transferFirmware(firmware: ByteArray, onProgress: suspend (Float) -> Unit): Result<Unit> =
            Result.success(Unit)

        override suspend fun abort() {
            abortCalled = true
            abortAction()
        }

        override suspend fun close() {
            closeCalled = true
            closeAction()
        }
    }

    @Test
    fun `cleanup closes transport after abort throws Exception`() = runTest {
        // Even if abort throws an operational Exception, close must still be called. The exception propagates after
        // close because the helper's finally guarantees close.
        val transport = FakeCleanupTransport(abortAction = { throw RuntimeException("abort failed") })
        assertFailsWith<RuntimeException> {
            cleanupDfuSessionTransport(transport, completed = false, sessionFailure = null)
        }
        assertTrue(transport.abortCalled)
        assertTrue(transport.closeCalled)
    }

    @Test
    fun `cleanup closes transport after abort throws Error`() = runTest {
        // Error subtypes propagate through the cleanup helper's finally — close still runs.
        val transport = FakeCleanupTransport(abortAction = { throw AssertionError("abort error") })
        assertFailsWith<AssertionError> {
            cleanupDfuSessionTransport(transport, completed = false, sessionFailure = null)
        }
        assertTrue(transport.abortCalled)
        assertTrue(transport.closeCalled)
    }

    @Test
    fun `cleanup skips abort after StaleSessionReset but always closes`() = runTest {
        val transport = FakeCleanupTransport()
        cleanupDfuSessionTransport(
            transport,
            completed = false,
            sessionFailure = LegacyDfuException.StaleSessionReset(),
        )
        assertFalse(transport.abortCalled, "abort must be skipped after StaleSessionReset")
        assertTrue(transport.closeCalled, "close must always run")
    }

    @Test
    fun `cleanup skips abort after MidStreamDisconnect but always closes`() = runTest {
        val transport = FakeCleanupTransport()
        cleanupDfuSessionTransport(
            transport,
            completed = false,
            sessionFailure = LegacyDfuException.MidStreamDisconnect(20, 200, "Disconnected", lastConfirmedBytes = -1),
        )
        assertFalse(transport.abortCalled, "abort must be skipped after MidStreamDisconnect")
        assertTrue(transport.closeCalled, "close must always run")
    }

    @Test
    fun `cleanup attempts abort after ambiguous ConnectionFailed and always closes`() = runTest {
        val transport = FakeCleanupTransport()
        cleanupDfuSessionTransport(
            transport,
            completed = false,
            sessionFailure = DfuException.ConnectionFailed("connect failed"),
        )
        assertTrue(transport.abortCalled, "abort must be attempted after ConnectionFailed (link may still be usable)")
        assertTrue(transport.closeCalled, "close must always run")
    }

    @Test
    fun `cleanup skips abort after completed session but always closes`() = runTest {
        val transport = FakeCleanupTransport()
        cleanupDfuSessionTransport(transport, completed = true, sessionFailure = null)
        assertFalse(transport.abortCalled, "abort must be skipped after a completed session")
        assertTrue(transport.closeCalled, "close must always run")
    }
}
