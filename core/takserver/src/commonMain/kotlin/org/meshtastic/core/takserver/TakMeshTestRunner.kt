/*
 * Copyright (c) 2026 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.meshtastic.core.takserver

import co.touchlab.kermit.Logger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.repository.CommandSender
import org.meshtastic.proto.PortNum

/**
 * Result of sending a single test fixture through the TAK mesh pipeline.
 */
data class TakTestResult(
    val fixtureName: String,
    val xmlBytes: Int,
    val compressedBytes: Int,
    val passed: Boolean,
    val error: String? = null,
)

/**
 * Debug-only test runner that sends the SDK's CoT XML test fixtures through the
 * real TAK mesh pipeline: strip → parse → compress → send to mesh radio.
 *
 * Paces sends by waiting [sendDelayMs] between each fixture to avoid flooding
 * the radio's TX queue.
 */
class TakMeshTestRunner(
    private val commandSender: CommandSender,
) {
    private val _results = MutableStateFlow<List<TakTestResult>>(emptyList())
    val results: StateFlow<List<TakTestResult>> = _results.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _currentFixture = MutableStateFlow<String?>(null)
    val currentFixture: StateFlow<String?> = _currentFixture.asStateFlow()

    companion object {
        /** Delay between sends to let the radio transmit and receive ACK. */
        private const val SEND_DELAY_MS = 5_000L
        private const val MAX_TAK_WIRE_PAYLOAD_BYTES = 225

        /** All bundled fixture filenames. */
        val FIXTURE_NAMES = listOf(
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
     * Run all test fixtures sequentially, sending each through the mesh pipeline.
     * Updates [results] and [currentFixture] as each fixture is processed.
     */
    suspend fun runAll() {
        if (_isRunning.value) return
        _isRunning.value = true
        _results.value = emptyList()

        val allResults = mutableListOf<TakTestResult>()

        for (name in FIXTURE_NAMES) {
            _currentFixture.value = name
            val result = runSingleFixture(name)
            allResults.add(result)
            _results.value = allResults.toList()

            if (result.passed) {
                // Wait for radio airtime + ACK before next send
                delay(SEND_DELAY_MS)
            }
        }

        _currentFixture.value = null
        _isRunning.value = false

        val passed = allResults.count { it.passed }
        val failed = allResults.size - passed
        Logger.i { "TAK Mesh Test complete: $passed/${allResults.size} passed, $failed failed" }
    }

    private suspend fun runSingleFixture(name: String): TakTestResult {
        // Load fixture XML from bundled resources
        val xml = try {
            loadFixtureXml(name)
        } catch (e: Throwable) {
            Logger.w(e) { "Failed to load fixture $name" }
            return TakTestResult(name, 0, 0, false, "Load failed: ${e.message}")
        }

        // Apply the same pipeline as TAKMeshIntegration.sendCoTToMesh()
        val freshXml = TAKMeshIntegration.ensureMinimumStaleForMesh(xml)
        val strippedXml = TAKMeshIntegration.stripNonEssentialElements(freshXml)

        // Parse and compress via SDK
        val wirePayload: ByteArray
        try {
            val sdkParser = org.meshtastic.tak.CotXmlParser()
            val sdkData = sdkParser.parse(strippedXml)
            val compressor = org.meshtastic.tak.TakCompressor()
            val compressed = compressor.compressWithRemarksFallback(sdkData, MAX_TAK_WIRE_PAYLOAD_BYTES)
            if (compressed == null) {
                Logger.w { "TAK Test: $name oversized even without remarks (xml=${xml.length}B)" }
                return TakTestResult(name, xml.length, 0, false, "Oversized (>${MAX_TAK_WIRE_PAYLOAD_BYTES}B)")
            }
            wirePayload = compressed
        } catch (e: Throwable) {
            Logger.w(e) { "TAK Test: $name compression failed: ${e.message}" }
            return TakTestResult(name, xml.length, 0, false, "Compress failed: ${e.message}")
        }

        // Send to mesh
        try {
            val dataPacket = DataPacket(
                to = DataPacket.ID_BROADCAST,
                bytes = wirePayload.toByteString(),
                dataType = PortNum.ATAK_PLUGIN_V2.value,
            )
            commandSender.sendData(dataPacket)
            Logger.i { "TAK Test: $name → ${wirePayload.size}B (xml=${xml.length}B)" }
            return TakTestResult(name, xml.length, wirePayload.size, true)
        } catch (e: Throwable) {
            Logger.w(e) { "TAK Test: $name send failed: ${e.message}" }
            return TakTestResult(name, xml.length, wirePayload.size, false, "Send failed: ${e.message}")
        }
    }

    private fun loadFixtureXml(name: String): String {
        val stream = this::class.java.classLoader?.getResourceAsStream("tak_test_fixtures/$name")
            ?: throw IllegalStateException("Fixture not found: tak_test_fixtures/$name")
        return stream.bufferedReader().readText()
    }
}
