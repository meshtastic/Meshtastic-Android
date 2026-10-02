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
import org.meshtastic.tak.CotXmlBuilder
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
 * TAKPacket V2 wire format compressor/decompressor.
 *
 * Wire format: [1 byte flags][zstd-compressed TAKPacketV2 protobuf] Flags byte bits 0-5 = dictionary ID, bits 6-7 =
 * reserved. Special value 0xFF = uncompressed raw protobuf (from TAK_TRACKER firmware).
 *
 * Delegates to the TAKPacket-SDK's [SdkCompressor] for zstd dictionary compression. The SDK is multiplatform since
 * 0.7.0 and its zstd codec is the transitive pure-Kotlin kzstd, so this runs on every target with no native library.
 */
@Suppress(
    "CyclomaticComplexMethod",
    "LongMethod", // wireToSdkData and sdkDataToWire are payload mapping switches
)
internal object TakV2Compressor {

    /** Maximum allowed decompressed payload size (bytes). */
    const val MAX_DECOMPRESSED_SIZE: Int = 4096

    /** Dictionary ID for non-aircraft types. */
    const val DICT_ID_NON_AIRCRAFT: Int = 0

    /** Dictionary ID for aircraft types. */
    const val DICT_ID_AIRCRAFT: Int = 1

    /** Special flags byte value indicating uncompressed raw protobuf. */
    const val DICT_ID_UNCOMPRESSED: Int = 0xFF

    // The SDK compressor bakes in pre-trained zstd dictionaries, so construction is the expensive part — build it once,
    // lazily, on first use.
    private val sdkCompressor: SdkCompressor by lazy { SdkCompressor() }

    /**
     * Compress a TAKPacketV2 into wire payload: [flags byte][zstd compressed protobuf]. Selects dictionary based on the
     * CoT type classification.
     */
    fun compress(packet: TAKPacketV2): ByteArray = sdkCompressor.compress(wireToSdkData(packet))

    /**
     * Decompress a wire payload back to TAKPacketV2. Handles both compressed (dict-based) and uncompressed (0xFF)
     * payloads.
     *
     * @throws IllegalArgumentException if payload is malformed or exceeds size limits.
     */
    fun decompress(wirePayload: ByteArray): TAKPacketV2 = sdkDataToWire(sdkCompressor.decompress(wirePayload))

    /**
     * Decompress a V2 wire payload and reconstruct CoT XML via the SDK's [CotXmlBuilder]. This handles ALL payload
     * types (DrawnShape, Marker, Route, etc.) without going through the Wire proto intermediate, avoiding the gap where
     * `toCoTMessage()` only handles PLI/GeoChat.
     */
    fun decompressToXml(wirePayload: ByteArray): String = CotXmlBuilder().build(sdkCompressor.decompress(wirePayload))

    /** Convert Wire-generated TAKPacketV2 → SDK's TakPacketV2Data. */
    private fun wireToSdkData(packet: TAKPacketV2): TakPacketV2Data {
        val cotTypeId = packet.cot_type_id.value
        val cotTypeStr = if (cotTypeId == 0 && packet.cot_type_str.isNotEmpty()) packet.cot_type_str else null

        // Wire's generated fields live in another module, so only locals smart-cast after a null check.
        val chat = packet.chat
        val taktalk = packet.taktalk
        val taktalkRoom = packet.taktalk_room
        val aircraft = packet.aircraft
        val shape = packet.shape
        val marker = packet.marker
        val rab = packet.rab
        val route = packet.route
        val casevac = packet.casevac
        val emergency = packet.emergency
        val task = packet.task
        val rawDetail = packet.raw_detail
        val payload =
            when {
                chat != null ->
                    TakPacketV2Data.Payload.Chat(
                        message = chat.message,
                        to = chat.to,
                        toCallsign = chat.to_callsign,
                        receiptForUid = chat.receipt_for_uid,
                        receiptType = chat.receipt_type.value,
                        // TAKTALK sidecars (proto3 optional → wire nullable).
                        // Empty string = empty `<Ea/>` / `<roomId/>` in source XML;
                        // null on the wire = field absent.  The SDK's Chat data class
                        // uses "" for absent, so map null → "".  voice_profile_id has
                        // a present-vs-empty-marker distinction tracked separately
                        // via hasVoiceProfile.
                        lang = chat.lang.orEmpty(),
                        roomId = chat.room_id.orEmpty(),
                        voiceProfileId = chat.voice_profile_id.orEmpty(),
                        hasVoiceProfile = chat.voice_profile_id != null,
                    )

                // TAKTALK voice/text message (m-t-t).  Without this branch,
                // m-t-t events fall through to Payload.None and the receiver
                // can't rebuild the CoT event, so TTS playback never fires.
                taktalk != null ->
                    TakPacketV2Data.Payload.TakTalk(
                        text = taktalk.text,
                        chatroomId = taktalk.chatroom_id,
                        lang = taktalk.lang,
                        fromVoice = taktalk.from_voice,
                    )

                // TAKTALK room/membership broadcast (y-).
                taktalkRoom != null ->
                    TakPacketV2Data.Payload.TakTalkRoom(
                        roomId = taktalkRoom.room_id,
                        roomName = taktalkRoom.room_name,
                        participants = taktalkRoom.participants.toList(),
                    )

                aircraft != null ->
                    TakPacketV2Data.Payload.Aircraft(
                        icao = aircraft.icao,
                        registration = aircraft.registration,
                        flight = aircraft.flight,
                        aircraftType = aircraft.aircraft_type,
                        squawk = aircraft.squawk,
                        category = aircraft.category,
                        rssiX10 = aircraft.rssi_x10,
                        gps = aircraft.gps,
                        cotHostId = aircraft.cot_host_id,
                    )

                // Typed geometry variants added by takv2_geometry (tags 34-37).
                // All GeoPoint fields on the wire are delta-encoded from the
                // event anchor; the SDK data class stores absolute lat/lon, so
                // we add packet.latitude_i / longitude_i here.
                shape != null -> {
                    TakPacketV2Data.Payload.DrawnShape(
                        kind = shape.kind.value,
                        style = shape.style.value,
                        majorCm = shape.major_cm,
                        minorCm = shape.minor_cm,
                        angleDeg = shape.angle_deg,
                        strokeColor = shape.stroke_color.value,
                        strokeArgb = shape.stroke_argb,
                        strokeWeightX10 = shape.stroke_weight_x10,
                        fillColor = shape.fill_color.value,
                        fillArgb = shape.fill_argb,
                        labelsOn = shape.labels_on,
                        // v0.4.0: vertices are two packed sint32 delta columns
                        // (vertex_lat_deltas / vertex_lon_deltas), zigzag deltas
                        // from the event anchor; SDK data stores absolute lat/lon.
                        vertices =
                        shape.vertex_lat_deltas.zip(shape.vertex_lon_deltas) { latD, lonD ->
                            TakPacketV2Data.Payload.Vertex(
                                latI = packet.latitude_i + latD,
                                lonI = packet.longitude_i + lonD,
                            )
                        },
                        truncated = shape.truncated,
                        bullseyeDistanceDm = shape.bullseye_distance_dm,
                        bullseyeBearingRef = shape.bullseye_bearing_ref,
                        bullseyeFlags = shape.bullseye_flags,
                        bullseyeUidRef = shape.bullseye_uid_ref,
                    )
                }

                marker != null -> {
                    TakPacketV2Data.Payload.Marker(
                        kind = marker.kind.value,
                        color = marker.color.value,
                        colorArgb = marker.color_argb,
                        readiness = marker.readiness,
                        parentUid = marker.parent_uid,
                        parentType = marker.parent_type,
                        parentCallsign = marker.parent_callsign,
                        iconset = marker.iconset,
                    )
                }

                rab != null -> {
                    val anchor = rab.anchor
                    TakPacketV2Data.Payload.RangeAndBearing(
                        anchorLatI = packet.latitude_i + (anchor?.lat_delta_i ?: 0),
                        anchorLonI = packet.longitude_i + (anchor?.lon_delta_i ?: 0),
                        anchorUid = rab.anchor_uid,
                        rangeCm = rab.range_cm,
                        bearingCdeg = rab.bearing_cdeg,
                        strokeColor = rab.stroke_color.value,
                        strokeArgb = rab.stroke_argb,
                        strokeWeightX10 = rab.stroke_weight_x10,
                    )
                }

                route != null -> {
                    TakPacketV2Data.Payload.Route(
                        method = route.method.value,
                        direction = route.direction.value,
                        prefix = route.prefix,
                        strokeWeightX10 = route.stroke_weight_x10,
                        links =
                        route.links.map { link ->
                            val pt = link.point
                            TakPacketV2Data.Payload.Route.Link(
                                latI = packet.latitude_i + (pt?.lat_delta_i ?: 0),
                                lonI = packet.longitude_i + (pt?.lon_delta_i ?: 0),
                                uid = link.uid,
                                callsign = link.callsign,
                                linkType = link.link_type,
                            )
                        },
                        truncated = route.truncated,
                    )
                }

                casevac != null -> {
                    TakPacketV2Data.Payload.CasevacReport(
                        precedence = casevac.precedence.value,
                        equipmentFlags = casevac.equipment_flags,
                        litterPatients = casevac.litter_patients,
                        ambulatoryPatients = casevac.ambulatory_patients,
                        security = casevac.security.value,
                        hlzMarking = casevac.hlz_marking.value,
                        zoneMarker = casevac.zone_marker,
                        usMilitary = casevac.us_military,
                        usCivilian = casevac.us_civilian,
                        nonUsMilitary = casevac.non_us_military,
                        nonUsCivilian = casevac.non_us_civilian,
                        epw = casevac.epw,
                        child = casevac.child,
                        terrainFlags = casevac.terrain_flags,
                        frequency = casevac.frequency,
                    )
                }

                emergency != null -> {
                    TakPacketV2Data.Payload.EmergencyAlert(
                        type = emergency.type.value,
                        authoringUid = emergency.authoring_uid,
                        cancelReferenceUid = emergency.cancel_reference_uid,
                    )
                }

                task != null -> {
                    TakPacketV2Data.Payload.TaskRequest(
                        taskType = task.task_type,
                        targetUid = task.target_uid,
                        assigneeUid = task.assignee_uid,
                        priority = task.priority.value,
                        status = task.status.value,
                        note = task.note,
                    )
                }

                rawDetail != null -> TakPacketV2Data.Payload.RawDetail(rawDetail.toByteArray())

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

        return TAKPacketV2.Builder()
            .also { wb ->
                wb.cot_type_id = cotType
                wb.cot_type_str = data.cotTypeStr ?: ""
                wb.how = how
                wb.callsign = data.callsign
                wb.team = team
                wb.role = role
                wb.latitude_i = data.latitudeI
                wb.longitude_i = data.longitudeI
                wb.altitude = data.altitude
                wb.speed = data.speed
                wb.course = data.course
                wb.battery = data.battery
                wb.geo_src = geoSrc
                wb.alt_src = altSrc
                wb.uid = data.uid
                wb.device_callsign = data.deviceCallsign
                wb.stale_seconds = data.staleSeconds
                wb.tak_version = data.takVersion
                wb.tak_device = data.takDevice
                wb.tak_platform = data.takPlatform
                wb.tak_os = data.takOs
                wb.endpoint = data.endpoint
                wb.phone = data.phone
                // v0.4.0: PLI is implicit — no payload_variant is set for a PLI (the
                // bool pli oneof arm was removed). Pli/None simply set no oneof field.
                wb.chat =
                    (data.payload as? TakPacketV2Data.Payload.Chat)?.let { chat ->
                        WireGeoChat.Builder()
                            .also { wb ->
                                wb.message = chat.message
                                wb.to = chat.to
                                wb.to_callsign = chat.toCallsign
                                wb.receipt_for_uid = chat.receiptForUid
                                wb.receipt_type =
                                    WireGeoChat.ReceiptType.fromValue(chat.receiptType)
                                        ?: WireGeoChat.ReceiptType.ReceiptType_None
                                // TAKTALK sidecars.  Empty SDK string → wire null (field absent)
                                // so non-TAKTALK chats don't carry empty sidecar bytes on every
                                // mesh packet.  voice_profile_id stays present-but-empty when
                                // hasVoiceProfile=true so the receiver can re-emit `<voice_profile_id/>`.
                                wb.lang = chat.lang.ifEmpty { null }
                                wb.room_id = chat.roomId.ifEmpty { null }
                                wb.voice_profile_id = if (chat.hasVoiceProfile) chat.voiceProfileId else null
                            }
                            .build()
                    }
                wb.aircraft =
                    (data.payload as? TakPacketV2Data.Payload.Aircraft)?.let { ac ->
                        WireAircraftTrack.Builder()
                            .also { wb ->
                                wb.icao = ac.icao
                                wb.registration = ac.registration
                                wb.flight = ac.flight
                                wb.aircraft_type = ac.aircraftType
                                wb.squawk = ac.squawk
                                wb.category = ac.category
                                wb.rssi_x10 = ac.rssiX10
                                wb.gps = ac.gps
                                wb.cot_host_id = ac.cotHostId
                            }
                            .build()
                    }
                wb.shape =
                    (data.payload as? TakPacketV2Data.Payload.DrawnShape)?.let { s ->
                        WireDrawnShape.Builder()
                            .also { wb ->
                                wb.kind = WireDrawnShape.Kind.fromValue(s.kind) ?: WireDrawnShape.Kind.Kind_Unspecified
                                wb.style =
                                    WireDrawnShape.StyleMode.fromValue(s.style)
                                        ?: WireDrawnShape.StyleMode.StyleMode_Unspecified
                                wb.major_cm = s.majorCm
                                wb.minor_cm = s.minorCm
                                wb.angle_deg = s.angleDeg
                                wb.stroke_color = WireTeam.fromValue(s.strokeColor) ?: WireTeam.Unspecifed_Color
                                wb.stroke_argb = s.strokeArgb
                                wb.stroke_weight_x10 = s.strokeWeightX10
                                wb.fill_color = WireTeam.fromValue(s.fillColor) ?: WireTeam.Unspecifed_Color
                                wb.fill_argb = s.fillArgb
                                wb.labels_on = s.labelsOn
                                // v0.4.0: delta-encode vertices into two packed sint32 columns
                                // relative to the event anchor (was repeated CotGeoPoint).
                                wb.vertex_lat_deltas = s.vertices.map { it.latI - data.latitudeI }
                                wb.vertex_lon_deltas = s.vertices.map { it.lonI - data.longitudeI }
                                wb.truncated = s.truncated
                                wb.bullseye_distance_dm = s.bullseyeDistanceDm
                                wb.bullseye_bearing_ref = s.bullseyeBearingRef
                                wb.bullseye_flags = s.bullseyeFlags
                                wb.bullseye_uid_ref = s.bullseyeUidRef
                            }
                            .build()
                    }
                wb.marker =
                    (data.payload as? TakPacketV2Data.Payload.Marker)?.let { m ->
                        WireMarker.Builder()
                            .also { wb ->
                                wb.kind = WireMarker.Kind.fromValue(m.kind) ?: WireMarker.Kind.Kind_Unspecified
                                wb.color = WireTeam.fromValue(m.color) ?: WireTeam.Unspecifed_Color
                                wb.color_argb = m.colorArgb
                                wb.readiness = m.readiness
                                wb.parent_uid = m.parentUid
                                wb.parent_type = m.parentType
                                wb.parent_callsign = m.parentCallsign
                                wb.iconset = m.iconset
                            }
                            .build()
                    }
                wb.rab =
                    (data.payload as? TakPacketV2Data.Payload.RangeAndBearing)?.let { r ->
                        WireRangeAndBearing.Builder()
                            .also { wb ->
                                wb.anchor =
                                    WireCotGeoPoint.Builder()
                                        .also { wb ->
                                            wb.lat_delta_i = r.anchorLatI - data.latitudeI
                                            wb.lon_delta_i = r.anchorLonI - data.longitudeI
                                        }
                                        .build()
                                wb.anchor_uid = r.anchorUid
                                wb.range_cm = r.rangeCm
                                wb.bearing_cdeg = r.bearingCdeg
                                wb.stroke_color = WireTeam.fromValue(r.strokeColor) ?: WireTeam.Unspecifed_Color
                                wb.stroke_argb = r.strokeArgb
                                wb.stroke_weight_x10 = r.strokeWeightX10
                            }
                            .build()
                    }
                wb.route =
                    (data.payload as? TakPacketV2Data.Payload.Route)?.let { rt ->
                        WireRoute.Builder()
                            .also { wb ->
                                wb.method = WireRoute.Method.fromValue(rt.method) ?: WireRoute.Method.Method_Unspecified
                                wb.direction =
                                    WireRoute.Direction.fromValue(rt.direction)
                                        ?: WireRoute.Direction.Direction_Unspecified
                                wb.prefix = rt.prefix
                                wb.stroke_weight_x10 = rt.strokeWeightX10
                                wb.links =
                                    rt.links.map { link ->
                                        WireRoute.Link.Builder()
                                            .also { wb ->
                                                wb.point =
                                                    WireCotGeoPoint.Builder()
                                                        .also { wb ->
                                                            wb.lat_delta_i = link.latI - data.latitudeI
                                                            wb.lon_delta_i = link.lonI - data.longitudeI
                                                        }
                                                        .build()
                                                wb.uid = link.uid
                                                wb.callsign = link.callsign
                                                wb.link_type = link.linkType
                                            }
                                            .build()
                                    }
                                wb.truncated = rt.truncated
                            }
                            .build()
                    }
                wb.casevac =
                    (data.payload as? TakPacketV2Data.Payload.CasevacReport)?.let { c ->
                        WireCasevacReport.Builder()
                            .also { wb ->
                                wb.precedence =
                                    WireCasevacReport.Precedence.fromValue(c.precedence)
                                        ?: WireCasevacReport.Precedence.Precedence_Unspecified
                                wb.equipment_flags = c.equipmentFlags
                                wb.litter_patients = c.litterPatients
                                wb.ambulatory_patients = c.ambulatoryPatients
                                wb.security =
                                    WireCasevacReport.Security.fromValue(c.security)
                                        ?: WireCasevacReport.Security.Security_Unspecified
                                wb.hlz_marking =
                                    WireCasevacReport.HlzMarking.fromValue(c.hlzMarking)
                                        ?: WireCasevacReport.HlzMarking.HlzMarking_Unspecified
                                wb.zone_marker = c.zoneMarker
                                wb.us_military = c.usMilitary
                                wb.us_civilian = c.usCivilian
                                wb.non_us_military = c.nonUsMilitary
                                wb.non_us_civilian = c.nonUsCivilian
                                wb.epw = c.epw
                                wb.child = c.child
                                wb.terrain_flags = c.terrainFlags
                                wb.frequency = c.frequency
                            }
                            .build()
                    }
                wb.emergency =
                    (data.payload as? TakPacketV2Data.Payload.EmergencyAlert)?.let { e ->
                        WireEmergencyAlert.Builder()
                            .also { wb ->
                                wb.type =
                                    WireEmergencyAlert.Type.fromValue(e.type)
                                        ?: WireEmergencyAlert.Type.Type_Unspecified
                                wb.authoring_uid = e.authoringUid
                                wb.cancel_reference_uid = e.cancelReferenceUid
                            }
                            .build()
                    }
                wb.task =
                    (data.payload as? TakPacketV2Data.Payload.TaskRequest)?.let { t ->
                        WireTaskRequest.Builder()
                            .also { wb ->
                                wb.task_type = t.taskType
                                wb.target_uid = t.targetUid
                                wb.assignee_uid = t.assigneeUid
                                wb.priority =
                                    WireTaskRequest.Priority.fromValue(t.priority)
                                        ?: WireTaskRequest.Priority.Priority_Unspecified
                                wb.status =
                                    WireTaskRequest.Status.fromValue(t.status)
                                        ?: WireTaskRequest.Status.Status_Unspecified
                                wb.note = t.note
                            }
                            .build()
                    }
                wb.raw_detail = (data.payload as? TakPacketV2Data.Payload.RawDetail)?.bytes?.toByteString()
                // TAKTALK voice/text message (m-t-t).  Without this, m-t-t events
                // would compress with no payload set, the receiver's wireToSdkData
                // would fall through to Payload.None, and TAKTALK plugin would
                // never see the rebuilt CoT event for TTS playback.
                wb.taktalk =
                    (data.payload as? TakPacketV2Data.Payload.TakTalk)?.let { tt ->
                        WireTakTalkMessage.Builder()
                            .also { wb ->
                                wb.text = tt.text
                                wb.chatroom_id = tt.chatroomId
                                wb.lang = tt.lang
                                wb.from_voice = tt.fromVoice
                            }
                            .build()
                    }
                // TAKTALK room/membership broadcast (y-).  Required for receivers
                // to resolve TAKTALK room UUIDs to friendly names + rosters.
                wb.taktalk_room =
                    (data.payload as? TakPacketV2Data.Payload.TakTalkRoom)?.let { room ->
                        @Suppress("DEPRECATION")
                        WireTakTalkRoomData.Builder()
                            .also { wb ->
                                // sender_callsign deprecated in SDK v0.3.2 — the SDK
                                // builder reconstitutes <sender-callsign> from envelope
                                // packet.callsign, so we stop emitting the duplicate
                                // wire byte. Field stays present for one release so
                                // v0.3.1 receivers continue decoding cleanly.
                                wb.sender_callsign = ""
                                wb.room_id = room.roomId
                                wb.room_name = room.roomName
                                wb.participants = room.participants.toList()
                            }
                            .build()
                    }
                // Directed-routing recipient list (<marti><dest …/>…</marti>).
                // Empty list = broadcast (default); populated for TAKTALK m-t-t
                // and directed b-t-f DMs. Encode an explicit Marti only when
                // there is at least one destination — the wrapper costs wire
                // bytes for no benefit on broadcast packets.
                wb.marti =
                    data.marti
                        .takeIf { it.isNotEmpty() }
                        ?.let { org.meshtastic.proto.Marti.Builder().also { wb -> wb.dest_callsign = it }.build() }
            }
            .build()
    }
}
