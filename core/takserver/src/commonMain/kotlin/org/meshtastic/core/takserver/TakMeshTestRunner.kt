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
@file:Suppress("TooGenericExceptionCaught", "ReturnCount")

package org.meshtastic.core.takserver

import co.touchlab.kermit.Logger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex

/** Which outbound TAK protocol a [TakTestResult] was dispatched through. See [TAKMeshIntegration]. */
enum class TakProtocol {
    /** Firmware >= 2.8.0: zstd-compressed TAKPacketV2 on port 78, full typed CoT support. */
    V2,

    /** Firmware <= 2.7.x: bare-protobuf legacy TAKPacket on port 72, PLI + GeoChat only. */
    V1,
}

/** Result of sending a single test fixture through the TAK mesh pipeline. */
data class TakTestResult(
    val fixtureName: String,
    val xmlBytes: Int,
    val compressedBytes: Int,
    val passed: Boolean,
    val error: String? = null,
    val protocol: TakProtocol = TakProtocol.V2,
    // Mirrors TakSendOutcome.Dropped.schemaLimited: true only when this fixture was dropped because the active
    // protocol's CoT type coverage doesn't include it (e.g. v1's TAKPacket only representing PLI/GeoChat) — a
    // known, permanent limitation of that protocol, so this is expected/intentional behavior, not a self-test
    // regression. False for an oversize drop (a real MTU problem, even if it happened on v1) or any failure —
    // never set purely because the result's protocol is V1.
    val expectedDrop: Boolean = false,
)

/**
 * Debug-only test runner that sends the SDK's CoT XML test fixtures through the REAL TAK mesh pipeline: strip → parse →
 * dispatch, via [TAKMeshIntegration.sendCoTToMeshForTest]. Each fixture is run through BOTH the v2 (firmware >= 2.8.0)
 * and v1 (legacy) dispatch paths so the self-test's pass/fail rate reflects what a real connected radio would actually
 * do on either firmware generation, rather than always exercising the v2 pipeline regardless of the connected radio's
 * firmware — see [TAKMeshIntegration.useTakV2].
 *
 * Paces sends by waiting [sendDelayMs] between each successfully-sent fixture to avoid flooding the radio's TX queue.
 * Fixtures that are dropped (not sent) incur no delay.
 */
class TakMeshTestRunner(private val takMeshIntegration: TAKMeshIntegration) {
    private val _results = MutableStateFlow<List<TakTestResult>>(emptyList())
    val results: StateFlow<List<TakTestResult>> = _results.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _currentFixture = MutableStateFlow<String?>(null)
    val currentFixture: StateFlow<String?> = _currentFixture.asStateFlow()

    // Prevents concurrent invocations from racing the _isRunning check-then-set.
    private val runMutex = Mutex()

    companion object {
        /** Delay between sends to let the radio transmit and receive ACK. */
        private const val SEND_DELAY_MS = 5_000L

        /** All bundled fixture filenames. */
        val FIXTURE_NAMES =
            listOf(
                // TAK-Talk sanity test — first in the list so the operator's
                // initial "Send Test CoTs" tap immediately exercises the
                // most-stress-tested path on the receiver: m-t-t with
                // <voice/> push-to-talk marker + <marti> directed routing.
                // If the receiver's TAKTALK plugin plays a TTS clip for this
                // single send, the v0.3.2 round-trip pipeline (sender callsign
                // implicit, marti carried, no spurious <contact>) is wired
                // end-to-end on both ends of the mesh.
                "taktalk_sanity.xml",
                "aircraft_adsb.xml",
                "aircraft_hostile.xml",
                "alert_tic.xml",
                "casevac.xml",
                "casevac_medline.xml",
                "chat_receipt_delivered.xml",
                "chat_receipt_read.xml",
                "delete_event.xml",
                "drawing_circle.xml",
                "drawing_circle_large.xml",
                "drawing_ellipse.xml",
                "drawing_freeform.xml",
                "drawing_polygon.xml",
                "drawing_rectangle.xml",
                "drawing_rectangle_itak.xml",
                "drawing_telestration.xml",
                "emergency_911.xml",
                "emergency_cancel.xml",
                "geochat_broadcast.xml",
                "geochat_dm.xml",
                "geochat_simple.xml",
                "marker_2525.xml",
                "marker_goto.xml",
                "marker_goto_itak.xml",
                "marker_icon_set.xml",
                "marker_spot.xml",
                "marker_tank.xml",
                "pli_basic.xml",
                "pli_full.xml",
                "pli_itak.xml",
                "pli_stationary.xml",
                "pli_takaware.xml",
                "pli_webtak.xml",
                "ranging_bullseye.xml",
                "ranging_circle.xml",
                "ranging_line.xml",
                "route_3wp.xml",
                "route_itak_3wp.xml",
                "task_engage.xml",
                "waypoint.xml",
            )
    }

    /**
     * Run all test fixtures sequentially through both the v2 and v1 dispatch paths. Updates [results] and
     * [currentFixture] as each fixture is processed. [results] accumulates v2 runs first, then v1 runs.
     */
    suspend fun runAll() {
        // Use tryLock to prevent concurrent test runs: if another coroutine is already
        // inside runAll(), tryLock returns false and we bail immediately. This is safer
        // than the check-then-set pattern which has a TOCTOU race in multi-threaded
        // coroutine dispatch.
        if (!runMutex.tryLock()) return
        try {
            _isRunning.value = true
            _results.value = emptyList()

            val allResults = mutableListOf<TakTestResult>()

            for (protocol in listOf(TakProtocol.V2, TakProtocol.V1)) {
                for (name in FIXTURE_NAMES) {
                    _currentFixture.value = "$name (${protocol.name.lowercase()})"
                    val result = runSingleFixture(name, protocol)
                    allResults.add(result)
                    _results.value = allResults.toList()

                    if (result.passed) {
                        // Wait for radio airtime + ACK before next send. Dropped fixtures never
                        // reached the radio, so there's nothing to pace.
                        delay(SEND_DELAY_MS)
                    }
                }
            }

            _currentFixture.value = null

            val v2 = allResults.filter { it.protocol == TakProtocol.V2 }
            val v1 = allResults.filter { it.protocol == TakProtocol.V1 }
            val v2Passed = v2.count { it.passed }
            val v1Passed = v1.count { it.passed }
            val v1ExpectedDrops = v1.count { it.expectedDrop }
            Logger.i {
                "TAK Mesh Test complete: v2=$v2Passed/${v2.size} passed; " +
                    "v1=$v1Passed/${v1.size} passed ($v1ExpectedDrops expected drops — " +
                    "v1's legacy schema only supports PLI/GeoChat)"
            }
        } finally {
            _isRunning.value = false
            runMutex.unlock()
        }
    }

    private suspend fun runSingleFixture(name: String, protocol: TakProtocol): TakTestResult {
        val forceV2 = protocol == TakProtocol.V2

        // Load fixture XML from bundled resources via platform-specific loader
        val xml =
            try {
                loadTakFixtureXml(name)
            } catch (e: Exception) {
                Logger.w(e) { "Failed to load fixture $name" }
                return TakTestResult(name, 0, 0, false, "Load failed: ${e.message}", protocol)
            }

        // Parse into the same CoTMessage shape a real inbound TAK-client message would produce
        // (sourceEventXml preserved) — see CoTXmlParser.buildCoTMessage.
        val cotMessage =
            CoTXmlParser(xml).parse().getOrElse { e ->
                Logger.w(e) { "TAK Test: $name failed to parse as CoT XML: ${e.message}" }
                return TakTestResult(name, xml.length, 0, false, "Parse failed: ${e.message}", protocol)
            }

        // Dispatch through the REAL production pipeline (TAKMeshIntegration.sendCoTToMeshV1/V2),
        // with the protocol forced rather than read from the connected radio's firmware.
        return try {
            when (val outcome = takMeshIntegration.sendCoTToMeshForTest(cotMessage, forceV2)) {
                is TakSendOutcome.Sent -> {
                    Logger.i { "TAK Test: $name → ${outcome.wireBytes}B (xml=${xml.length}B) via $protocol" }
                    TakTestResult(name, xml.length, outcome.wireBytes, true, protocol = protocol)
                }

                is TakSendOutcome.Dropped -> {
                    Logger.w { "TAK Test: $name dropped on $protocol: ${outcome.reason}" }
                    TakTestResult(
                        fixtureName = name,
                        xmlBytes = xml.length,
                        compressedBytes = 0,
                        passed = false,
                        error = outcome.reason,
                        protocol = protocol,
                        // Only a schema-coverage drop is an expected/intentional limitation (v1's legacy
                        // schema legitimately supports only PLI/GeoChat). An oversize drop is a real MTU
                        // problem regardless of which protocol hit it, and must never be mislabeled as
                        // expected just because it happened on v1.
                        expectedDrop = outcome.schemaLimited,
                    )
                }

                is TakSendOutcome.Failed -> {
                    Logger.w { "TAK Test: $name send failed on $protocol: ${outcome.reason}" }
                    TakTestResult(name, xml.length, 0, false, outcome.reason, protocol)
                }
            }
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.w(e) { "TAK Test: $name send failed: ${e.message}" }
            TakTestResult(name, xml.length, 0, false, "Send failed: ${e.message}", protocol)
        }
    }
}
