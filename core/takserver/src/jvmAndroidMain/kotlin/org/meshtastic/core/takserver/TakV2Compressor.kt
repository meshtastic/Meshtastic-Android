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
package org.meshtastic.core.takserver

import okio.ByteString.Companion.toByteString
import org.meshtastic.proto.TAKPacketV2
import org.meshtastic.tak.TakPacketV2Data
import org.meshtastic.proto.AircraftTrack as WireAircraftTrack
import org.meshtastic.proto.CasevacReport as WireCasevacReport
import org.meshtastic.proto.CotGeoPoint as WireCotGeoPoint
import org.meshtastic.proto.DrawnShape as WireDrawnShape
import org.meshtastic.proto.EmergencyAlert as WireEmergencyAlert
import org.meshtastic.proto.GeoChat as WireGeoChat
import org.meshtastic.proto.Marker as WireMarker
import org.meshtastic.proto.RangeAndBearing as WireRangeAndBearing
import org.meshtastic.proto.Route as WireRoute
import org.meshtastic.proto.TakTalkMessage as WireTakTalkMessage
import org.meshtastic.proto.TakTalkRoomData as WireTakTalkRoomData
import org.meshtastic.proto.TaskRequest as WireTaskRequest
import org.meshtastic.proto.Team as WireTeam
import org.meshtastic.tak.TakCompressor as SdkCompressor

/**
 * JVM/Android implementation of TakV2Compressor. Delegates to TAKPacket-SDK's TakCompressor for zstd dictionary
 * compression.
 *
 * The SDK compressor is constructed lazily and its result is cached in a nullable field so that a native-library
 * failure (e.g. missing Android .so) does NOT poison this object. Without lazy/try-catch, a failure inside a top-level
 * `val` initializer runs at class `<clinit>` time, marks the class ERRONEOUS, and turns every subsequent reference into
 * `NoClassDefFoundError`.
 */
internal actual object TakV2Compressor {

    actual val MAX_DECOMPRESSED_SIZE: Int = 4096
    actual val DICT_ID_NON_AIRCRAFT: Int = 0
    actual val DICT_ID_AIRCRAFT: Int = 1
    actual val DICT_ID_UNCOMPRESSED: Int = 0xFF

    @Volatile private var sdkCompressorOrNull: SdkCompressor? = null

    @Volatile private var sdkCompressorInitFailure: Throwable? = null

    @Synchronized
    private fun getSdkCompressor(): SdkCompressor {
        sdkCompressorOrNull?.let {
            return it
        }
        sdkCompressorInitFailure?.let { cached ->
            throw IllegalStateException("zstd-jni unavailable on this platform", cached)
        }
        return try {
            SdkCompressor().also { sdkCompressorOrNull = it }
        } catch (e: Throwable) {
            sdkCompressorInitFailure = e
            throw IllegalStateException("zstd-jni unavailable on this platform", e)
        }
    }

    actual fun compress(packet: TAKPacketV2): ByteArray {
        val data = wireToSdkData(packet)
        return getSdkCompressor().compress(data)
    }

    actual fun decompress(wirePayload: ByteArray): TAKPacketV2 {
        val data = getSdkCompressor().decompress(wirePayload)
        return sdkDataToWire(data)
    }

    /**
     * Decompress a V2 wire payload and reconstruct CoT XML via the SDK's CotXmlBuilder. This handles ALL payload types
     * (DrawnShape, Marker, Route, etc.) without going through the Wire proto intermediate, avoiding the gap where
     * `toCoTMessage()` only handles PLI/GeoChat.
     */
    actual fun decompressToXml(wirePayload: ByteArray): String {
        val data = getSdkCompressor().decompress(wirePayload)
        return org.meshtastic.tak.CotXmlBuilder().build(data)
    }

    /** Convert Wire-generated TAKPacketV2 → SDK's TakPacketV2Data. */
    private fun wireToSdkData(packet: TAKPacketV2): TakPacketV2Data {
        val cotTypeId = packet.cot_type_id.value
        val cotTypeStr = if (cotTypeId == 0 && packet.cot_type_str.isNotEmpty()) packet.cot_type_str else null

        val payload =
            when {
                packet.chat != null ->
                    TakPacketV2Data.Payload.Chat(
                        message = packet.chat!!.message,
                        to = packet.chat!!.to,
                        toCallsign = packet.chat!!.to_callsign,
                        receiptForUid = packet.chat!!.receipt_for_uid,
                        receiptType = packet.chat!!.receipt_type.value,
                        // TAKTALK sidecars (proto3 optional → wire nullable).
                        // Empty string = empty `<Ea/>` / `<roomId/>` in source XML;
                        // null on the wire = field absent.  The SDK's Chat data class
                        // uses "" for absent, so map null → "".  voice_profile_id has
                        // a present-vs-empty-marker distinction tracked separately
                        // via hasVoiceProfile.
                        lang = packet.chat!!.lang ?: "",
                        roomId = packet.chat!!.room_id ?: "",
                        voiceProfileId = packet.chat!!.voice_profile_id ?: "",
                        hasVoiceProfile = packet.chat!!.voice_profile_id != null,
                    )

                // TAKTALK voice/text message (m-t-t).  Without this branch,
                // m-t-t events fall through to Payload.None and the receiver
                // can't rebuild the CoT event, so TTS playback never fires.
                packet.taktalk != null ->
                    TakPacketV2Data.Payload.TakTalk(
                        text = packet.taktalk!!.text,
                        chatroomId = packet.taktalk!!.chatroom_id,
                        lang = packet.taktalk!!.lang,
                        fromVoice = packet.taktalk!!.from_voice,
                    )

                // TAKTALK room/membership broadcast (y-).
                packet.taktalk_room != null ->
                    @Suppress("DEPRECATION")
                    TakPacketV2Data.Payload.TakTalkRoom(
                        // sender_callsign deprecated in SDK v0.3.2 — sender
                        // identity is now in envelope packet.callsign;
                        // v0.3.1 packets still populate the legacy field so
                        // we forward it for source-compat readers.
                        senderCallsign = packet.taktalk_room!!.sender_callsign,
                        roomId = packet.taktalk_room!!.room_id,
                        roomName = packet.taktalk_room!!.room_name,
                        participants = packet.taktalk_room!!.participants.toList(),
                    )

                packet.aircraft != null ->
                    TakPacketV2Data.Payload.Aircraft(
                        icao = packet.aircraft!!.icao,
                        registration = packet.aircraft!!.registration,
                        flight = packet.aircraft!!.flight,
                        aircraftType = packet.aircraft!!.aircraft_type,
                        squawk = packet.aircraft!!.squawk,
                        category = packet.aircraft!!.category,
                        rssiX10 = packet.aircraft!!.rssi_x10,
                        gps = packet.aircraft!!.gps,
                        cotHostId = packet.aircraft!!.cot_host_id,
                    )

                // Typed geometry variants added by takv2_geometry (tags 34-37).
                // All GeoPoint fields on the wire are delta-encoded from the
                // event anchor; the SDK data class stores absolute lat/lon, so
                // we add packet.latitude_i / longitude_i here.
                packet.shape != null -> {
                    val s = packet.shape!!
                    TakPacketV2Data.Payload.DrawnShape(
                        kind = s.kind.value,
                        style = s.style.value,
                        majorCm = s.major_cm,
                        minorCm = s.minor_cm,
                        angleDeg = s.angle_deg,
                        strokeColor = s.stroke_color.value,
                        strokeArgb = s.stroke_argb,
                        strokeWeightX10 = s.stroke_weight_x10,
                        fillColor = s.fill_color.value,
                        fillArgb = s.fill_argb,
                        labelsOn = s.labels_on,
                        // v0.4.0: vertices are two packed sint32 delta columns
                        // (vertex_lat_deltas / vertex_lon_deltas), zigzag deltas
                        // from the event anchor; SDK data stores absolute lat/lon.
                        vertices =
                        s.vertex_lat_deltas.zip(s.vertex_lon_deltas) { latD, lonD ->
                            TakPacketV2Data.Payload.Vertex(
                                latI = packet.latitude_i + latD,
                                lonI = packet.longitude_i + lonD,
                            )
                        },
                        truncated = s.truncated,
                        bullseyeDistanceDm = s.bullseye_distance_dm,
                        bullseyeBearingRef = s.bullseye_bearing_ref,
                        bullseyeFlags = s.bullseye_flags,
                        bullseyeUidRef = s.bullseye_uid_ref,
                    )
                }

                packet.marker != null -> {
                    val m = packet.marker!!
                    TakPacketV2Data.Payload.Marker(
                        kind = m.kind.value,
                        color = m.color.value,
                        colorArgb = m.color_argb,
                        readiness = m.readiness,
                        parentUid = m.parent_uid,
                        parentType = m.parent_type,
                        parentCallsign = m.parent_callsign,
                        iconset = m.iconset,
                    )
                }

                packet.rab != null -> {
                    val r = packet.rab!!
                    val anchor = r.anchor
                    TakPacketV2Data.Payload.RangeAndBearing(
                        anchorLatI = packet.latitude_i + (anchor?.lat_delta_i ?: 0),
                        anchorLonI = packet.longitude_i + (anchor?.lon_delta_i ?: 0),
                        anchorUid = r.anchor_uid,
                        rangeCm = r.range_cm,
                        bearingCdeg = r.bearing_cdeg,
                        strokeColor = r.stroke_color.value,
                        strokeArgb = r.stroke_argb,
                        strokeWeightX10 = r.stroke_weight_x10,
                    )
                }

                packet.route != null -> {
                    val rt = packet.route!!
                    TakPacketV2Data.Payload.Route(
                        method = rt.method.value,
                        direction = rt.direction.value,
                        prefix = rt.prefix,
                        strokeWeightX10 = rt.stroke_weight_x10,
                        links =
                        rt.links.map { link ->
                            val pt = link.point
                            TakPacketV2Data.Payload.Route.Link(
                                latI = packet.latitude_i + (pt?.lat_delta_i ?: 0),
                                lonI = packet.longitude_i + (pt?.lon_delta_i ?: 0),
                                uid = link.uid,
                                callsign = link.callsign,
                                linkType = link.link_type,
                            )
                        },
                        truncated = rt.truncated,
                    )
                }

                packet.casevac != null -> {
                    val c = packet.casevac!!
                    TakPacketV2Data.Payload.CasevacReport(
                        precedence = c.precedence.value,
                        equipmentFlags = c.equipment_flags,
                        litterPatients = c.litter_patients,
                        ambulatoryPatients = c.ambulatory_patients,
                        security = c.security.value,
                        hlzMarking = c.hlz_marking.value,
                        zoneMarker = c.zone_marker,
                        usMilitary = c.us_military,
                        usCivilian = c.us_civilian,
                        nonUsMilitary = c.non_us_military,
                        nonUsCivilian = c.non_us_civilian,
                        epw = c.epw,
                        child = c.child,
                        terrainFlags = c.terrain_flags,
                        frequency = c.frequency,
                    )
                }

                packet.emergency != null -> {
                    val e = packet.emergency!!
                    TakPacketV2Data.Payload.EmergencyAlert(
                        type = e.type.value,
                        authoringUid = e.authoring_uid,
                        cancelReferenceUid = e.cancel_reference_uid,
                    )
                }

                packet.task != null -> {
                    val t = packet.task!!
                    TakPacketV2Data.Payload.TaskRequest(
                        taskType = t.task_type,
                        targetUid = t.target_uid,
                        assigneeUid = t.assignee_uid,
                        priority = t.priority.value,
                        status = t.status.value,
                        note = t.note,
                    )
                }

                packet.raw_detail != null -> TakPacketV2Data.Payload.RawDetail(packet.raw_detail!!.toByteArray())

                // v0.4.0: PLI is implicit — a packet with no payload_variant set
                // is a position report (the bool pli oneof arm was removed).
                // Mirrors the SDK serializer's toData default.
                else -> TakPacketV2Data.Payload.Pli(true)
            }

        return TakPacketV2Data(
            cotTypeId = cotTypeId,
            cotTypeStr = cotTypeStr,
            how = packet.how.value,
            callsign = packet.callsign,
            team = packet.team.value,
            role = packet.role.value,
            latitudeI = packet.latitude_i,
            longitudeI = packet.longitude_i,
            altitude = packet.altitude,
            speed = packet.speed,
            course = packet.course,
            battery = packet.battery,
            geoSrc = packet.geo_src.value,
            altSrc = packet.alt_src.value,
            uid = packet.uid,
            deviceCallsign = packet.device_callsign,
            staleSeconds = packet.stale_seconds,
            takVersion = packet.tak_version,
            takDevice = packet.tak_device,
            takPlatform = packet.tak_platform,
            takOs = packet.tak_os,
            endpoint = packet.endpoint,
            phone = packet.phone,
            // Directed-routing recipient callsigns (<marti><dest …/>…</marti>).
            // Empty list = broadcast (default); populated for TAKTALK m-t-t,
            // directed b-t-f DMs, and any other CoT shape that ATAK addresses
            // to specific peers. Without this field the receive-side rebuild
            // drops <marti>, breaking TAKTALK voice TTS.
            marti = packet.marti?.dest_callsign?.toList() ?: emptyList(),
            payload = payload,
        )
    }

    /** Convert SDK's TakPacketV2Data → Wire-generated TAKPacketV2. */
    private fun sdkDataToWire(data: TakPacketV2Data): TAKPacketV2 {
        val cotType =
            org.meshtastic.proto.CotType.fromValue(data.cotTypeId) ?: org.meshtastic.proto.CotType.CotType_Other
        val how = org.meshtastic.proto.CotHow.fromValue(data.how) ?: org.meshtastic.proto.CotHow.CotHow_Unspecified
        val team = org.meshtastic.proto.Team.fromValue(data.team) ?: org.meshtastic.proto.Team.Unspecifed_Color
        val role = org.meshtastic.proto.MemberRole.fromValue(data.role) ?: org.meshtastic.proto.MemberRole.Unspecifed
        val geoSrc =
            org.meshtastic.proto.GeoPointSource.fromValue(data.geoSrc)
                ?: org.meshtastic.proto.GeoPointSource.GeoPointSource_Unspecified
        val altSrc =
            org.meshtastic.proto.GeoPointSource.fromValue(data.altSrc)
                ?: org.meshtastic.proto.GeoPointSource.GeoPointSource_Unspecified

        return TAKPacketV2(
            cot_type_id = cotType,
            cot_type_str = data.cotTypeStr ?: "",
            how = how,
            callsign = data.callsign,
            team = team,
            role = role,
            latitude_i = data.latitudeI,
            longitude_i = data.longitudeI,
            altitude = data.altitude,
            speed = data.speed,
            course = data.course,
            battery = data.battery,
            geo_src = geoSrc,
            alt_src = altSrc,
            uid = data.uid,
            device_callsign = data.deviceCallsign,
            stale_seconds = data.staleSeconds,
            tak_version = data.takVersion,
            tak_device = data.takDevice,
            tak_platform = data.takPlatform,
            tak_os = data.takOs,
            endpoint = data.endpoint,
            phone = data.phone,
            // v0.4.0: PLI is implicit — no payload_variant is set for a PLI (the
            // bool pli oneof arm was removed). Pli/None simply set no oneof field.
            chat =
            (data.payload as? TakPacketV2Data.Payload.Chat)?.let { chat ->
                WireGeoChat(
                    message = chat.message,
                    to = chat.to,
                    to_callsign = chat.toCallsign,
                    receipt_for_uid = chat.receiptForUid,
                    receipt_type =
                    WireGeoChat.ReceiptType.fromValue(chat.receiptType)
                        ?: WireGeoChat.ReceiptType.ReceiptType_None,
                    // TAKTALK sidecars.  Empty SDK string → wire null (field absent)
                    // so non-TAKTALK chats don't carry empty sidecar bytes on every
                    // mesh packet.  voice_profile_id stays present-but-empty when
                    // hasVoiceProfile=true so the receiver can re-emit `<voice_profile_id/>`.
                    lang = chat.lang.ifEmpty { null },
                    room_id = chat.roomId.ifEmpty { null },
                    voice_profile_id = if (chat.hasVoiceProfile) chat.voiceProfileId else null,
                )
            },
            aircraft =
            (data.payload as? TakPacketV2Data.Payload.Aircraft)?.let { ac ->
                WireAircraftTrack(
                    icao = ac.icao,
                    registration = ac.registration,
                    flight = ac.flight,
                    aircraft_type = ac.aircraftType,
                    squawk = ac.squawk,
                    category = ac.category,
                    rssi_x10 = ac.rssiX10,
                    gps = ac.gps,
                    cot_host_id = ac.cotHostId,
                )
            },
            shape =
            (data.payload as? TakPacketV2Data.Payload.DrawnShape)?.let { s ->
                WireDrawnShape(
                    kind = WireDrawnShape.Kind.fromValue(s.kind) ?: WireDrawnShape.Kind.Kind_Unspecified,
                    style =
                    WireDrawnShape.StyleMode.fromValue(s.style)
                        ?: WireDrawnShape.StyleMode.StyleMode_Unspecified,
                    major_cm = s.majorCm,
                    minor_cm = s.minorCm,
                    angle_deg = s.angleDeg,
                    stroke_color = WireTeam.fromValue(s.strokeColor) ?: WireTeam.Unspecifed_Color,
                    stroke_argb = s.strokeArgb,
                    stroke_weight_x10 = s.strokeWeightX10,
                    fill_color = WireTeam.fromValue(s.fillColor) ?: WireTeam.Unspecifed_Color,
                    fill_argb = s.fillArgb,
                    labels_on = s.labelsOn,
                    // v0.4.0: delta-encode vertices into two packed sint32 columns
                    // relative to the event anchor (was repeated CotGeoPoint).
                    vertex_lat_deltas = s.vertices.map { it.latI - data.latitudeI },
                    vertex_lon_deltas = s.vertices.map { it.lonI - data.longitudeI },
                    truncated = s.truncated,
                    bullseye_distance_dm = s.bullseyeDistanceDm,
                    bullseye_bearing_ref = s.bullseyeBearingRef,
                    bullseye_flags = s.bullseyeFlags,
                    bullseye_uid_ref = s.bullseyeUidRef,
                )
            },
            marker =
            (data.payload as? TakPacketV2Data.Payload.Marker)?.let { m ->
                WireMarker(
                    kind = WireMarker.Kind.fromValue(m.kind) ?: WireMarker.Kind.Kind_Unspecified,
                    color = WireTeam.fromValue(m.color) ?: WireTeam.Unspecifed_Color,
                    color_argb = m.colorArgb,
                    readiness = m.readiness,
                    parent_uid = m.parentUid,
                    parent_type = m.parentType,
                    parent_callsign = m.parentCallsign,
                    iconset = m.iconset,
                )
            },
            rab =
            (data.payload as? TakPacketV2Data.Payload.RangeAndBearing)?.let { r ->
                WireRangeAndBearing(
                    anchor =
                    WireCotGeoPoint(
                        lat_delta_i = r.anchorLatI - data.latitudeI,
                        lon_delta_i = r.anchorLonI - data.longitudeI,
                    ),
                    anchor_uid = r.anchorUid,
                    range_cm = r.rangeCm,
                    bearing_cdeg = r.bearingCdeg,
                    stroke_color = WireTeam.fromValue(r.strokeColor) ?: WireTeam.Unspecifed_Color,
                    stroke_argb = r.strokeArgb,
                    stroke_weight_x10 = r.strokeWeightX10,
                )
            },
            route =
            (data.payload as? TakPacketV2Data.Payload.Route)?.let { rt ->
                WireRoute(
                    method = WireRoute.Method.fromValue(rt.method) ?: WireRoute.Method.Method_Unspecified,
                    direction =
                    WireRoute.Direction.fromValue(rt.direction) ?: WireRoute.Direction.Direction_Unspecified,
                    prefix = rt.prefix,
                    stroke_weight_x10 = rt.strokeWeightX10,
                    links =
                    rt.links.map { link ->
                        WireRoute.Link(
                            point =
                            WireCotGeoPoint(
                                lat_delta_i = link.latI - data.latitudeI,
                                lon_delta_i = link.lonI - data.longitudeI,
                            ),
                            uid = link.uid,
                            callsign = link.callsign,
                            link_type = link.linkType,
                        )
                    },
                    truncated = rt.truncated,
                )
            },
            casevac =
            (data.payload as? TakPacketV2Data.Payload.CasevacReport)?.let { c ->
                WireCasevacReport(
                    precedence =
                    WireCasevacReport.Precedence.fromValue(c.precedence)
                        ?: WireCasevacReport.Precedence.Precedence_Unspecified,
                    equipment_flags = c.equipmentFlags,
                    litter_patients = c.litterPatients,
                    ambulatory_patients = c.ambulatoryPatients,
                    security =
                    WireCasevacReport.Security.fromValue(c.security)
                        ?: WireCasevacReport.Security.Security_Unspecified,
                    hlz_marking =
                    WireCasevacReport.HlzMarking.fromValue(c.hlzMarking)
                        ?: WireCasevacReport.HlzMarking.HlzMarking_Unspecified,
                    zone_marker = c.zoneMarker,
                    us_military = c.usMilitary,
                    us_civilian = c.usCivilian,
                    non_us_military = c.nonUsMilitary,
                    non_us_civilian = c.nonUsCivilian,
                    epw = c.epw,
                    child = c.child,
                    terrain_flags = c.terrainFlags,
                    frequency = c.frequency,
                )
            },
            emergency =
            (data.payload as? TakPacketV2Data.Payload.EmergencyAlert)?.let { e ->
                WireEmergencyAlert(
                    type = WireEmergencyAlert.Type.fromValue(e.type) ?: WireEmergencyAlert.Type.Type_Unspecified,
                    authoring_uid = e.authoringUid,
                    cancel_reference_uid = e.cancelReferenceUid,
                )
            },
            task =
            (data.payload as? TakPacketV2Data.Payload.TaskRequest)?.let { t ->
                WireTaskRequest(
                    task_type = t.taskType,
                    target_uid = t.targetUid,
                    assignee_uid = t.assigneeUid,
                    priority =
                    WireTaskRequest.Priority.fromValue(t.priority)
                        ?: WireTaskRequest.Priority.Priority_Unspecified,
                    status =
                    WireTaskRequest.Status.fromValue(t.status) ?: WireTaskRequest.Status.Status_Unspecified,
                    note = t.note,
                )
            },
            raw_detail = (data.payload as? TakPacketV2Data.Payload.RawDetail)?.bytes?.toByteString(),
            // TAKTALK voice/text message (m-t-t).  Without this, m-t-t events
            // would compress with no payload set, the receiver's wireToSdkData
            // would fall through to Payload.None, and TAKTALK plugin would
            // never see the rebuilt CoT event for TTS playback.
            taktalk =
            (data.payload as? TakPacketV2Data.Payload.TakTalk)?.let { tt ->
                WireTakTalkMessage(
                    text = tt.text,
                    chatroom_id = tt.chatroomId,
                    lang = tt.lang,
                    from_voice = tt.fromVoice,
                )
            },
            // TAKTALK room/membership broadcast (y-).  Required for receivers
            // to resolve TAKTALK room UUIDs to friendly names + rosters.
            taktalk_room =
            (data.payload as? TakPacketV2Data.Payload.TakTalkRoom)?.let { room ->
                @Suppress("DEPRECATION")
                WireTakTalkRoomData(
                    // sender_callsign deprecated in SDK v0.3.2 — the SDK
                    // builder reconstitutes <sender-callsign> from envelope
                    // packet.callsign, so we stop emitting the duplicate
                    // wire byte. Field stays present for one release so
                    // v0.3.1 receivers continue decoding cleanly.
                    sender_callsign = "",
                    room_id = room.roomId,
                    room_name = room.roomName,
                    participants = room.participants.toList(),
                )
            },
            // Directed-routing recipient list (<marti><dest …/>…</marti>).
            // Empty list = broadcast (default); populated for TAKTALK m-t-t
            // and directed b-t-f DMs. Encode an explicit Marti only when
            // there is at least one destination — the wrapper costs wire
            // bytes for no benefit on broadcast packets.
            marti = data.marti.takeIf { it.isNotEmpty() }?.let { org.meshtastic.proto.Marti(dest_callsign = it) },
        )
    }
}
