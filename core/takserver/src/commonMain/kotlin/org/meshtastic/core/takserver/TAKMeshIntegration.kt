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
@file:Suppress("ReturnCount", "TooGenericExceptionCaught")

package org.meshtastic.core.takserver

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

import kotlinx.coroutines.launch
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.repository.CommandSender
import org.meshtastic.core.repository.MeshConfigHandler

import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.takserver.TAKPacketV2Conversion.toCoTMessage
import org.meshtastic.core.takserver.TAKPacketV2Conversion.toTAKPacketV2
import org.meshtastic.proto.MemberRole
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.TAKPacket
import org.meshtastic.proto.Team
import kotlin.concurrent.Volatile
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/**
 * Bidirectional bridge between the local TAK server and the Meshtastic mesh network.
 *
 * V2 protocol only: All traffic uses port 78 (ATAK_PLUGIN_V2).
 * Legacy V1 port 72 is still received for backward compatibility but will be removed.
 */
class TAKMeshIntegration(
    private val takServerManager: TAKServerManager,
    private val commandSender: CommandSender,

    private val serviceRepository: ServiceRepository,
    private val meshConfigHandler: MeshConfigHandler,
) {
    @Volatile private var isRunning = false
    private val jobs = mutableListOf<Job>()
    private var currentTeam: Team = Team.Unspecifed_Color
    private var currentRole: MemberRole = MemberRole.Unspecifed

    fun start(scope: CoroutineScope) {
        if (isRunning) return
        isRunning = true

        takServerManager.start(scope)

        val newJobs = listOf(
            // Forward incoming CoT from TAK clients to mesh
            scope.launch {
                takServerManager.inboundMessages.collect { (cotMessage, clientInfo) ->
                    // Enrich GeoChat messages with the originating TAK client's
                    // callsign when the message itself lacks one. This only applies
                    // to messages FROM the connected TAK client — mesh-originated
                    // messages flow through handleMeshPacket() instead.
                    val enriched = if (cotMessage.type == "b-t-f" &&
                        cotMessage.contact?.callsign.isNullOrEmpty() &&
                        clientInfo?.callsign != null
                    ) {
                        cotMessage.copy(
                            contact = (cotMessage.contact ?: CoTContact(callsign = ""))
                                .copy(callsign = clientInfo.callsign)
                        )
                    } else {
                        cotMessage
                    }
                    sendCoTToMesh(enriched)
                }
            },

            // Forward incoming ATAK packets from mesh to TAK clients
            scope.launch {
                serviceRepository.meshPacketFlow
                    .filter {
                        it.decoded?.portnum == PortNum.ATAK_PLUGIN_V2 ||
                            it.decoded?.portnum == PortNum.ATAK_PLUGIN
                    }
                    .collect { packet -> handleMeshPacket(packet) }
            },

            // Track TAK config changes
            scope.launch {
                meshConfigHandler.moduleConfig
                    .map { it.tak }
                    .distinctUntilChanged()
                    .collect { takConfig ->
                        currentTeam = takConfig?.team ?: Team.Unspecifed_Color
                        currentRole = takConfig?.role ?: MemberRole.Unspecifed
                    }
            },
        )

        jobs.addAll(newJobs)
        Logger.i { "TAK Mesh Integration started (v2 protocol)" }
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false
        val toCancel = jobs.toList()
        jobs.clear()
        toCancel.forEach(Job::cancel)
        takServerManager.stop()
        Logger.i { "TAK Mesh Integration stopped" }
    }

    // ── Send: TAK client → mesh ─────────────────────────────────────────────

    private suspend fun sendCoTToMesh(cotMessage: CoTMessage) {
        // Prefer the sourceEventXml for shape/marker/route types — the SDK's
        // CotXmlParser extracts compact typed payloads (DrawnShape, Marker,
        // Route, etc.) that compress far better than raw_detail encoding.
        // For PLI and GeoChat, use the enriched CoTMessage (which may have
        // had callsign/contact injected by the upstream enrichment step).
        val rawXml = cotMessage.sourceEventXml ?: cotMessage.toXml()
        // Extend stale for static objects (routes, shapes, markers) that may
        // arrive over LoRa mesh past their original TTL. iTAK uses 2-min stale
        // for routes; ATAK uses 24h. 5 min ensures it survives mesh delivery.
        val freshXml = ensureMinimumStaleForMesh(rawXml)
        // Strip non-essential elements before compression to save wire bytes
        val xml = stripNonEssentialElements(freshXml)

        Logger.d { "RAW CoT OUT (mesh, ${cotMessage.type}): $rawXml" }

        // Route through the SDK parser/compressor which handles all typed
        // payloads (DrawnShape, Marker, Route, Aircraft, etc.) with compact
        // proto fields instead of raw_detail XML. Falls back to the app's
        // own conversion only if the SDK path fails.
        //
        // compressWithRemarksFallback preserves <remarks> text when the
        // compressed packet fits under the LoRa MTU, and strips remarks
        // automatically if needed to fit. Returns null if even without
        // remarks the packet exceeds the limit.
        val wirePayload: ByteArray = try {
            val sdkParser = org.meshtastic.tak.CotXmlParser()
            val sdkData = sdkParser.parse(xml)
            val compressor = org.meshtastic.tak.TakCompressor()
            compressor.compressWithRemarksFallback(sdkData, MAX_TAK_WIRE_PAYLOAD_BYTES) ?: run {
                Logger.w {
                    buildString {
                        append("Dropping oversized TAK packet: type=${cotMessage.type} max=$MAX_TAK_WIRE_PAYLOAD_BYTES")
                        cotMessage.sourceEventXml?.let { src ->
                            append('\n')
                            append("Source CoT event: ")
                            append(if (src.length <= TAK_LOG_XML_MAX_CHARS) src else src.take(TAK_LOG_XML_MAX_CHARS) + "…")
                        }
                    }
                }
                return
            }
        } catch (e: Throwable) {
            Logger.d(e) { "SDK parser/compressor failed for ${cotMessage.type}, trying app conversion" }
            val takPacketV2 = cotMessage.toTAKPacketV2()
            if (takPacketV2 == null) {
                Logger.w { "Cannot convert CoT type ${cotMessage.type} to TAKPacketV2, dropping" }
                return
            }
            try {
                TakV2Compressor.compress(takPacketV2)
            } catch (e2: Throwable) {
                Logger.w(e2) { "V2 compression failed for ${cotMessage.type}, using uncompressed wire format" }
                encodeUncompressed(takPacketV2)
            }
        }

        try {
            val dataPacket = DataPacket(
                to = DataPacket.ID_BROADCAST,
                bytes = wirePayload.toByteString(),
                dataType = PortNum.ATAK_PLUGIN_V2.value,
            )
            commandSender.sendData(dataPacket)
            Logger.d { "Sent V2 to mesh: ${cotMessage.type} (${wirePayload.size} bytes)" }
        } catch (e: Throwable) {
            // Something other than size — radio not connected, queue full, etc.
            Logger.e(e) { "Failed to send TAKPacketV2 to mesh (${cotMessage.type}, ${wirePayload.size} bytes): ${e.message}" }
        }
    }

    /**
     * Wrap a [org.meshtastic.proto.TAKPacketV2] into the uncompressed v2 wire format:
     * `[0xFF flag byte][raw protobuf]`. Used as a fallback when the zstd native lib
     * isn't loaded.
     */
    private fun encodeUncompressed(takPacketV2: org.meshtastic.proto.TAKPacketV2): ByteArray {
        val protoBytes = org.meshtastic.proto.TAKPacketV2.ADAPTER.encode(takPacketV2)
        val out = ByteArray(1 + protoBytes.size)
        out[0] = TakV2Compressor.DICT_ID_UNCOMPRESSED.toByte()
        protoBytes.copyInto(out, 1)
        return out
    }

    // ── Receive: mesh → TAK client ──────────────────────────────────────────

    private suspend fun handleMeshPacket(packet: MeshPacket) {
        val payload = packet.decoded?.payload ?: return

        when (packet.decoded?.portnum) {
            PortNum.ATAK_PLUGIN_V2 -> handleV2Packet(payload.toByteArray())
            PortNum.ATAK_PLUGIN -> handleV1Packet(payload)
            else -> return
        }
    }

    private suspend fun handleV2Packet(wirePayload: ByteArray) {
        try {
            // Decompress to CoT XML via the SDK's CotXmlBuilder, which handles
            // ALL typed payloads (DrawnShape, Marker, Route, etc.) and preserves
            // shape detail elements (vertices, colors, stroke weight) that the
            // app's own CoTXmlParser would strip. Forward the SDK-generated XML
            // directly to TAK clients without re-parsing.
            val rawXml = TakV2Compressor.decompressToXml(wirePayload)
            // Strip the XML declaration and collapse whitespace — ATAK's TCP
            // streaming parser expects bare <event>...</event> on a single
            // line, not a formatted XML document with <?xml ...?> prologue.
            val xml = rawXml
                .replace("""<?xml version="1.0" encoding="UTF-8"?>""", "")
                .replace(Regex("""\s*\n\s*"""), "")
                .trim()
            Logger.d { "RAW CoT IN (mesh): $xml" }
            // Routes: ATAK ignores b-m-r CoT events over TCP streaming.
            // Convert to a KML data package and write to ATAK's auto-import dir.
            if (xml.contains("""type="b-m-r"""")) {
                try {
                    val pkg = RouteDataPackageGenerator.generateDataPackage(xml)
                    if (pkg != null) {
                        val (fileName, zipBytes) = pkg
                        AtakFileWriter.writeToImportDir(fileName, zipBytes)
                    } else {
                        Logger.w { "Route data package generation failed — not enough waypoints?" }
                    }
                } catch (e2: Throwable) {
                    Logger.w(e2) { "Route data package write failed: ${e2.message}" }
                }
            }
            takServerManager.broadcastRawXml(xml)
            Logger.d { "V2 → TAK clients (raw XML)" }
        } catch (e: Throwable) {
            Logger.w(e) { "Failed to handle V2 packet: ${e.message}" }
        }
    }

    /** Backward compat for legacy V1 devices. Will be removed. */
    private suspend fun handleV1Packet(payload: okio.ByteString) {
        try {
            val takPacket = TAKPacket.ADAPTER.decode(payload)
            val cotMessage = convertV1ToCoT(takPacket) ?: return
            takServerManager.broadcast(cotMessage)
            Logger.d { "V1 → TAK clients: ${cotMessage.type}" }
        } catch (e: Throwable) {
            Logger.w(e) { "Failed to handle V1 packet: ${e.message}" }
        }
    }

    private fun convertV1ToCoT(takPacket: TAKPacket): CoTMessage? {
        val callsign = takPacket.contact?.callsign ?: "UNKNOWN"
        val senderUid = takPacket.contact?.device_callsign ?: "unknown"
        val teamName = takPacket.group?.team?.toTakTeamName() ?: DEFAULT_TAK_TEAM_NAME
        val roleName = takPacket.group?.role?.toTakRoleName() ?: DEFAULT_TAK_ROLE_NAME
        val battery = takPacket.status?.battery ?: DEFAULT_TAK_BATTERY

        val pli = takPacket.pli
        if (pli != null) {
            return CoTMessage.pli(
                uid = senderUid,
                callsign = callsign,
                latitude = pli.latitude_i.toDouble() / TAK_COORDINATE_SCALE,
                longitude = pli.longitude_i.toDouble() / TAK_COORDINATE_SCALE,
                altitude = pli.altitude.toDouble(),
                speed = pli.speed.toDouble(),
                course = pli.course.toDouble(),
                team = teamName,
                role = roleName,
                battery = battery,
                staleMinutes = DEFAULT_TAK_STALE_MINUTES,
            )
        }

        val chat = takPacket.chat
        if (chat != null) {
            val timeNow = Clock.System.now()
            return CoTMessage(
                uid = "GeoChat.$senderUid.All Chat Rooms",
                type = "b-t-f",
                how = "h-g-i-g-o",
                time = timeNow,
                start = timeNow,
                stale = timeNow + DEFAULT_TAK_STALE_MINUTES.minutes,
                latitude = 0.0,
                longitude = 0.0,
                contact = CoTContact(callsign = callsign, endpoint = DEFAULT_TAK_ENDPOINT),
                group = CoTGroup(name = teamName, role = roleName),
                status = CoTStatus(battery = battery),
                chat = CoTChat(
                    chatroom = chat.to ?: "All Chat Rooms",
                    senderCallsign = callsign,
                    message = chat.message,
                ),
            )
        }

        return null
    }

    companion object {
        /**
         * Minimum stale TTL (5 min) for static CoT types sent over mesh.
         * iTAK uses 2-min stale for routes/shapes; over LoRa mesh with
         * multi-hop relay, these arrive past stale and ATAK discards them.
         * PLI and GeoChat are left untouched — their stale is meaningful.
         */
        private val MIN_MESH_STALE_TTL = 15.minutes
        private val STATIC_COT_PREFIXES = listOf("b-m-r", "u-d-", "b-m-p-")
        private val EVENT_TYPE_RE = Regex("""<event\s[^>]*\btype="([^"]*)"""")
        private val STALE_ATTR_RE = Regex("""\bstale="([^"]*)"""")

        fun ensureMinimumStaleForMesh(xml: String): String {
            val type = EVENT_TYPE_RE.find(xml)?.groupValues?.getOrNull(1) ?: return xml
            if (STATIC_COT_PREFIXES.none { type.startsWith(it) }) return xml
            val staleMatch = STALE_ATTR_RE.find(xml) ?: return xml
            val staleStr = staleMatch.groupValues[1]
            val staleInstant = try {
                kotlin.time.Instant.parse(staleStr)
            } catch (_: IllegalArgumentException) {
                // Handle edge-case formats like missing "Z"
                try {
                    val cleaned = staleStr.replace(Regex("""\.\d+"""), "").replace("Z", "+00:00")
                    kotlin.time.Instant.parse(cleaned)
                } catch (_: IllegalArgumentException) { return xml }
            }

            val now = Clock.System.now()
            val remaining = staleInstant - now
            if (remaining >= MIN_MESH_STALE_TTL) return xml

            val newStale = now + MIN_MESH_STALE_TTL
            val newStaleStr = newStale.toString().replace(Regex("""\.\d+"""), "") // strip fractional seconds
            Logger.i { "Extended stale for $type: $staleStr → $newStaleStr (was ${remaining.inWholeSeconds}s remaining, now ${MIN_MESH_STALE_TTL.inWholeSeconds}s)" }
            return xml.replaceRange(staleMatch.range, """stale="$newStaleStr"""")
        }

        /**
         * Strip non-essential XML elements before mesh compression to save wire bytes.
         * These elements add 100-200 bytes but aren't needed for rendering shapes,
         * routes, chats, markers, PLI, or any other payload on the receiving end.
         */
        private val STRIP_PATTERNS = listOf(
            """<takv[^>]*/>""",                              // TAK version (self-closing)
            """<takv[^>]*>.*?</takv>""",                     // TAK version (paired)
            """<voice[^>]*/>""",                              // voice chat state
            """<voice[^>]*>.*?</voice>""",
            """<marti[^>]*/>""",                              // empty marti
            """<marti[^>]*>.*?</marti>""",
            """<__geofence[^>]*/>""",                         // geofence config
            """<__geofence[^>]*>.*?</__geofence>""",
            """<tog[^>]*/>""",                                // toggle state
            """<archive[^>]*/>""",                            // archive marker
            """<__shapeExtras[^>]*/>""",                      // shape extras
            """<__shapeExtras[^>]*>.*?</__shapeExtras>""",
            """<creator[^>]*/>""",                            // creator info
            """<creator[^>]*>.*?</creator>""",
            """<remarks[^>]*/>""",                            // empty remarks (self-closing)
            """<remarks[^>]*></remarks>""",                   // empty remarks (paired)
            """<strokeStyle[^>]*/>""",                        // stroke style (SDK uses color fields)
            """<precisionlocation[^>]*/>""",                  // precision location metadata
            """<precisionlocation[^>]*>.*?</precisionlocation>""",
            """<precisionLocation[^>]*/>""",                  // iTAK camelCase variant
            """<precisionLocation[^>]*>.*?</precisionLocation>""",
        ).map { Regex(it, RegexOption.DOT_MATCHES_ALL) }

        // Strip any attribute with value "???" — unknown/placeholder metadata
        private val UNKNOWN_ATTR_PATTERN = Regex("""\s+\w+\s*=\s*"[?]{3}"""")

        // Strip specific named attributes that the SDK doesn't use (display-only)
        private val STRIP_ATTR_PATTERNS = listOf(
            """\s+routetype\s*=\s*"[^"]*"""",      // route display type (SDK doesn't use)
            """\s+order\s*=\s*"[^"]*"""",           // checkpoint order label (SDK doesn't use)
            """\s+color\s*=\s*"[^"]*"""",           // link_attr color (SDK uses strokeColor instead)
            """\s+access\s*=\s*"[^"]*"""",          // access control (not relevant for mesh)
            """\s+callsign\s*=\s*""""",             // empty callsign attributes (e.g. checkpoints)
            """\s+phone\s*=\s*""""",                // empty phone attributes
        ).map { Regex(it) }

        // Route waypoint UID stripping — UIDs are full 36-char UUIDs that cost
        // ~40 bytes each in the proto wire format. The receiving TAK client derives
        // its own UIDs, so these are pure overhead. Only targets <link> elements
        // with a point= attribute (route waypoints / shape vertices).
        private val ROUTE_LINK_ELEM_RE = Regex("""<link\s[^>]*\bpoint="[^"]*"[^>]*/>""")
        private val LINK_UID_ATTR_RE = Regex("""\s+uid="[^"]*"""")

        fun stripNonEssentialElements(xml: String): String {
            var result = xml
            for (pattern in STRIP_PATTERNS) {
                result = pattern.replace(result, "")
            }
            // Strip ??? attributes from remaining elements
            result = UNKNOWN_ATTR_PATTERN.replace(result, "")
            // Strip specific display-only attributes
            for (pattern in STRIP_ATTR_PATTERNS) {
                result = pattern.replace(result, "")
            }
            // Strip uid from route waypoint <link> elements (receiver derives UIDs)
            result = ROUTE_LINK_ELEM_RE.replace(result) { LINK_UID_ATTR_RE.replace(it.value, "") }
            return result
        }
    }
}
