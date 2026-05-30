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
@file:Suppress("ReturnCount", "TooGenericExceptionCaught", "LongMethod")

package org.meshtastic.core.takserver

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.model.Capabilities
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.repository.CommandSender
import org.meshtastic.core.repository.MeshConfigHandler
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.takserver.TAKPacketConversion.toTAKPacket
import org.meshtastic.core.takserver.TAKPacketV2Conversion.toTAKPacketV2
import org.meshtastic.proto.MemberRole
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.TAKPacket
import org.meshtastic.proto.Team
import org.meshtastic.tak.CotMeshSanitizer
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/**
 * Bidirectional bridge between the local TAK server and the Meshtastic mesh network.
 *
 * Outbound traffic (TAK client -> mesh) is version-gated on the connected radio's firmware version, exposed via
 * [Capabilities.supportsTakV2]:
 * - Firmware **>= 2.8.0**: TAKPacketV2 on port 78 (ATAK_PLUGIN_V2) with zstd dictionary compression via TAKPacket-SDK.
 *   Supports all CoT payload types (PLI, GeoChat, DrawnShape, Marker, Route, Aircraft, Casevac, Emergency, Task) with
 *   compact typed encodings that fit under the 237B LoRa MTU.
 * - Firmware **<= 2.7.x**: Legacy [TAKPacket] on port 72 (ATAK_PLUGIN) with bare protobuf encoding. Supports only PLI
 *   and GeoChat — shapes, markers, routes, and other typed CoT events are dropped (with a warning) because the legacy
 *   schema cannot represent them.
 *
 * Inbound traffic (mesh -> TAK client) is always dual-path tolerant — both port 72 and port 78 are dispatched
 * regardless of the local radio's firmware version, so a v2-capable node can still relay legacy v1 packets received
 * from older nodes in mixed-firmware mesh deployments.
 */
@OptIn(ExperimentalAtomicApi::class)
class TAKMeshIntegration(
    private val takServerManager: TAKServerManager,
    private val commandSender: CommandSender,
    private val serviceRepository: ServiceRepository,
    private val meshConfigHandler: MeshConfigHandler,
    private val nodeRepository: NodeRepository,
) {
    private val isRunning = AtomicBoolean(false)

    // Immutable list reference replaced atomically in start()/stop(); never mutated in-place.
    // @Volatile only guarantees visibility of the reference itself — any in-place mutation
    // would bypass the visibility guarantee and must not be added.
    @Volatile private var jobs: List<Job> = emptyList()

    @Volatile private var currentTeam: Team = Team.Unspecifed_Color

    @Volatile private var currentRole: MemberRole = MemberRole.Unspecifed

    // Drops CoT the bridge has already injected within a short window — guards against the
    // same mesh message arriving via multiple LoRa relay paths or a retransmit. Touched only
    // from the single meshPacketFlow collector coroutine (handleMeshPacket), so no locking.
    private val deliveryDedup = CotDeliveryDedup()

    fun start(scope: CoroutineScope) {
        if (!isRunning.compareAndSet(expectedValue = false, newValue = true)) return

        takServerManager.start(scope)

        val newJobs =
            listOf(
                // Forward incoming CoT from TAK clients to mesh
                scope.launch {
                    takServerManager.inboundMessages.collect { (cotMessage, clientInfo) ->
                        // Enrich GeoChat messages with the originating TAK client's
                        // callsign when the message itself lacks one. This only applies
                        // to messages FROM the connected TAK client — mesh-originated
                        // messages flow through handleMeshPacket() instead.
                        val enriched =
                            if (
                                cotMessage.type == "b-t-f" &&
                                cotMessage.contact?.callsign.isNullOrEmpty() &&
                                clientInfo?.callsign != null
                            ) {
                                cotMessage.copy(
                                    contact =
                                    (cotMessage.contact ?: CoTContact(callsign = "")).copy(
                                        callsign = clientInfo.callsign,
                                    ),
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
                            it.decoded?.portnum == PortNum.ATAK_PLUGIN_V2 || it.decoded?.portnum == PortNum.ATAK_PLUGIN
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

        jobs = newJobs
        val fw = nodeRepository.myNodeInfo.value?.firmwareVersion
        val proto = if (Capabilities(fw).supportsTakV2) "v2 (port 78, zstd)" else "v1 (port 72, legacy)"
        Logger.i { "TAK Mesh Integration started — firmware=$fw, outbound=$proto" }
    }

    fun stop() {
        if (!isRunning.compareAndSet(expectedValue = true, newValue = false)) return
        val toCancel = jobs
        jobs = emptyList()
        toCancel.forEach(Job::cancel)
        takServerManager.stop()
        Logger.i { "TAK Mesh Integration stopped" }
    }

    // ── Send: TAK client → mesh ─────────────────────────────────────────────

    /**
     * Determine the outbound TAK protocol version based on the connected radio's firmware version. Evaluated per-send
     * (not cached) so the bridge picks up firmware upgrades during a session without restart. If the firmware version
     * is unavailable (radio not yet handshook), default to V2 — the v2 firmware was released widely enough that
     * defaulting to legacy would be a regression for the common case.
     */
    private fun useTakV2(): Boolean {
        val fw = nodeRepository.myNodeInfo.value?.firmwareVersion ?: return true
        return Capabilities(fw).supportsTakV2
    }

    private suspend fun sendCoTToMesh(cotMessage: CoTMessage) {
        if (useTakV2()) {
            sendCoTToMeshV2(cotMessage)
        } else {
            sendCoTToMeshV1(cotMessage)
        }
    }

    /**
     * v2 send path (firmware >= 2.8.0): SDK parser + zstd dictionary compression, full typed payload support
     * (DrawnShape, Marker, Route, Aircraft, Casevac, Emergency, Task, plus PLI / GeoChat). Wire format: `[flags
     * byte][zstd-compressed TAKPacketV2 protobuf]` on port 78 (ATAK_PLUGIN_V2).
     */
    private suspend fun sendCoTToMeshV2(cotMessage: CoTMessage) {
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

        // Route through the SDK parser/compressor which handles all typed
        // payloads (DrawnShape, Marker, Route, Aircraft, etc.) with compact
        // proto fields instead of raw_detail XML. Falls back to the app's
        // own conversion only if the SDK path fails.
        //
        // compressWithRemarksFallback preserves <remarks> text when the
        // compressed packet fits under the LoRa MTU, and strips remarks
        // automatically if needed to fit. Returns null if even without
        // remarks the packet exceeds the limit.
        val wirePayload: ByteArray =
            try {
                TakSdkCompressor.compressCoT(xml, MAX_TAK_WIRE_PAYLOAD_BYTES)
                    ?: run {
                        Logger.w {
                            buildString {
                                append(
                                    "Dropping oversized TAK packet: " +
                                        "type=${cotMessage.type} max=$MAX_TAK_WIRE_PAYLOAD_BYTES",
                                )
                                cotMessage.sourceEventXml?.let { src ->
                                    append('\n')
                                    append("Source CoT event: ")
                                    append(
                                        if (src.length <= TAK_LOG_XML_MAX_CHARS) {
                                            src
                                        } else {
                                            src.take(TAK_LOG_XML_MAX_CHARS) + "…"
                                        },
                                    )
                                }
                            }
                        }
                        return
                    }
            } catch (e: Exception) {
                Logger.w(e) { "SDK parser/compressor failed for ${cotMessage.type}, trying app conversion" }
                val takPacketV2 = cotMessage.toTAKPacketV2()
                if (takPacketV2 == null) {
                    Logger.w { "Cannot convert CoT type ${cotMessage.type} to TAKPacketV2, dropping" }
                    return
                }
                try {
                    TakV2Compressor.compress(takPacketV2)
                } catch (e2: Exception) {
                    Logger.w(e2) { "V2 compression failed for ${cotMessage.type}, using uncompressed wire format" }
                    encodeUncompressed(takPacketV2)
                }
            }

        try {
            val dataPacket =
                DataPacket(
                    to = DataPacket.ID_BROADCAST,
                    bytes = wirePayload.toByteString(),
                    dataType = PortNum.ATAK_PLUGIN_V2.value,
                )
            commandSender.sendData(dataPacket)
            Logger.d { "Sent V2 to mesh: ${cotMessage.type} (${wirePayload.size} bytes)" }
        } catch (e: Exception) {
            // Something other than size — radio not connected, queue full, etc.
            Logger.e(e) {
                "Failed to send TAKPacketV2 to mesh (${cotMessage.type}, ${wirePayload.size} bytes): ${e.message}"
            }
        }
    }

    /**
     * Legacy v1 send path (firmware <= 2.7.x): bare protobuf-encoded [TAKPacket] on port 72 (ATAK_PLUGIN), no zstd
     * compression. Only PLI and GeoChat payloads are supported by the v1 schema — shapes, markers, routes, casevac,
     * emergency, and task CoT events are dropped with a warning.
     */
    private suspend fun sendCoTToMeshV1(cotMessage: CoTMessage) {
        val takPacket =
            cotMessage.toTAKPacket()
                ?: run {
                    Logger.w {
                        "Dropping CoT for legacy v1 radio: type=${cotMessage.type} not representable " +
                            "in v1 TAKPacket schema (only PLI and GeoChat are supported). " +
                            "Upgrade radio firmware to >= 2.8.0 for full payload support."
                    }
                    return
                }

        val wirePayload = TAKPacket.ADAPTER.encode(takPacket)
        if (wirePayload.size > MAX_TAK_WIRE_PAYLOAD_BYTES) {
            Logger.w {
                "Dropping oversized v1 TAK packet: type=${cotMessage.type} " +
                    "size=${wirePayload.size}B max=$MAX_TAK_WIRE_PAYLOAD_BYTES"
            }
            return
        }

        try {
            val dataPacket =
                DataPacket(
                    to = DataPacket.ID_BROADCAST,
                    bytes = wirePayload.toByteString(),
                    dataType = PortNum.ATAK_PLUGIN.value,
                )
            commandSender.sendData(dataPacket)
            Logger.d { "Sent V1 to mesh: ${cotMessage.type} (${wirePayload.size} bytes)" }
        } catch (e: Exception) {
            Logger.e(e) {
                "Failed to send v1 TAKPacket to mesh (${cotMessage.type}, ${wirePayload.size} bytes): ${e.message}"
            }
        }
    }

    /**
     * Wrap a [org.meshtastic.proto.TAKPacketV2] into the uncompressed v2 wire format: `[0xFF flag byte][raw protobuf]`.
     * Used as a fallback when the zstd native lib isn't loaded.
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
            // Normalize for the TAK TCP stream — drop the <?xml ...?> prologue
            // and collapse inter-tag whitespace so ATAK's streaming parser sees
            // bare <event>...</event> on a single line. Centralized in the SDK.
            val xml = CotMeshSanitizer.normalizeCotXml(rawXml)
            // Drop exact duplicates the mesh delivered more than once (multi-path relay or
            // retransmit) so ATAK doesn't surface doubled chat / TAK-Talk messages. Genuine
            // updates (new PLI position, moved marker, …) differ in content and pass through.
            if (!deliveryDedup.admit(xml)) {
                Logger.d { "Dropped duplicate CoT from mesh (already delivered within dedup window)" }
                return
            }
            // Logger.d { "RAW CoT IN (mesh): $xml" }
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
                } catch (e2: Exception) {
                    Logger.w(e2) { "Route data package write failed: ${e2.message}" }
                }
            }
            takServerManager.broadcastRawXml(xml)
            Logger.d { "V2 → TAK clients (raw XML)" }
        } catch (e: Exception) {
            Logger.w(e) { "Failed to handle V2 packet: ${e.message}" }
        }
    }

    /**
     * v1 receive path (firmware <= 2.7.x): decode bare protobuf [TAKPacket] (no compression) from port 72 (ATAK_PLUGIN)
     * and convert to CoT for forwarding to attached TAK clients. Kept indefinitely so users on stable 2.7.x firmware
     * retain PLI + GeoChat interop; new typed payloads (shapes, markers, routes, etc.) still require a v2-capable radio
     * (firmware >= 2.8.0).
     */
    private suspend fun handleV1Packet(payload: okio.ByteString) {
        try {
            val takPacket = TAKPacket.ADAPTER.decode(payload)
            val cotMessage = convertV1ToCoT(takPacket) ?: return
            takServerManager.broadcast(cotMessage)
            Logger.d { "V1 → TAK clients: ${cotMessage.type}" }
        } catch (e: Exception) {
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
            // Include chatroom in UID so ATAK routes DMs correctly — the UID format
            // "GeoChat.<senderUid>.<chatroom>.<msgId>" is what ATAK uses to determine routing.
            // Hardcoding "All Chat Rooms" here loses DM routing from legacy v1 nodes.
            val chatroom = chat.to ?: "All Chat Rooms"
            val msgId = Random.Default.nextInt().toString(TAK_HEX_RADIX)
            return CoTMessage(
                uid = "GeoChat.$senderUid.$chatroom.$msgId",
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
                chat = CoTChat(chatroom = chatroom, senderCallsign = callsign, message = chat.message),
            )
        }

        return null
    }

    companion object {
        /**
         * Minimum stale TTL (5 min) for static CoT types sent over mesh. iTAK uses 2-min stale for routes/shapes; over
         * LoRa mesh with multi-hop relay, these arrive past stale and ATAK discards them. PLI and GeoChat are left
         * untouched — their stale is meaningful.
         */
        private val MIN_MESH_STALE_TTL = 15.minutes
        private val STATIC_COT_PREFIXES = listOf("b-m-r", "u-d-", "b-m-p-")
        private val EVENT_TYPE_RE = Regex("""<event\s[^>]*\btype="([^"]*)"""")

        // Matches the stale attribute ONLY within the <event> opening tag to avoid
        // accidentally matching a stale="..." on a <link> or other child element.
        private val EVENT_TAG_RE = Regex("""<event\b[^>]*>""")
        private val STALE_ATTR_RE = Regex("""\bstale="([^"]*)"""")

        fun ensureMinimumStaleForMesh(xml: String): String {
            val type = EVENT_TYPE_RE.find(xml)?.groupValues?.getOrNull(1) ?: return xml
            if (STATIC_COT_PREFIXES.none { type.startsWith(it) }) return xml
            // Search for stale only inside the <event> opening tag, not in child elements
            val eventTagMatch = EVENT_TAG_RE.find(xml) ?: return xml
            val eventTag = eventTagMatch.value
            val staleInTag = STALE_ATTR_RE.find(eventTag) ?: return xml
            val staleStr = staleInTag.groupValues[1]
            val staleInstant =
                try {
                    kotlin.time.Instant.parse(staleStr)
                } catch (_: IllegalArgumentException) {
                    // Handle edge-case formats like missing "Z"
                    try {
                        val cleaned = staleStr.replace(Regex("""\.\d+"""), "").replace("Z", "+00:00")
                        kotlin.time.Instant.parse(cleaned)
                    } catch (_: IllegalArgumentException) {
                        return xml
                    }
                }

            val now = Clock.System.now()
            val remaining = staleInstant - now
            if (remaining >= MIN_MESH_STALE_TTL) return xml

            val newStale = now + MIN_MESH_STALE_TTL
            val newStaleStr = newStale.toString().replace(Regex("""\.\d+"""), "") // strip fractional seconds
            Logger.i {
                "Extended stale for $type: $staleStr → $newStaleStr " +
                    "(was ${remaining.inWholeSeconds}s remaining, now ${MIN_MESH_STALE_TTL.inWholeSeconds}s)"
            }
            // Replace the stale value only within the event tag, then splice the patched tag back
            val newEventTag = eventTag.replaceRange(staleInTag.range, """stale="$newStaleStr"""")
            return xml.replaceRange(eventTagMatch.range, newEventTag)
        }

        /**
         * Strip non-essential CoT detail before mesh compression to save wire bytes. Delegates to the SDK's
         * [CotMeshSanitizer] so the strip rules live in ONE golden-tested place shared by every consumer (Android,
         * Apple, …) and can't drift between sides. Drift here once silently stripped TAK-Talk `<voice>` / `<marti>` and
         * broke the feature end-to-end — guarded by the strip-preservation test in TAKMeshIntegrationTest and by
         * CotMeshSanitizerTest in the SDK.
         */
        fun stripNonEssentialElements(xml: String): String = CotMeshSanitizer.stripNonEssentialForMesh(xml)
    }
}
