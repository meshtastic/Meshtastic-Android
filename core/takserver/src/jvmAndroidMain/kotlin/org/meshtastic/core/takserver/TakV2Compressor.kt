/*
 * Copyright (c) 2026 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.meshtastic.core.takserver

import okio.ByteString.Companion.toByteString
import org.meshtastic.proto.AircraftTrack as WireAircraftTrack
import org.meshtastic.proto.CasevacReport as WireCasevacReport
import org.meshtastic.proto.CotGeoPoint as WireCotGeoPoint
import org.meshtastic.proto.DrawnShape as WireDrawnShape
import org.meshtastic.proto.EmergencyAlert as WireEmergencyAlert
import org.meshtastic.proto.GeoChat as WireGeoChat
import org.meshtastic.proto.Marker as WireMarker
import org.meshtastic.proto.RangeAndBearing as WireRangeAndBearing
import org.meshtastic.proto.Route as WireRoute
import org.meshtastic.proto.TAKPacketV2
import org.meshtastic.proto.TaskRequest as WireTaskRequest
import org.meshtastic.proto.Team as WireTeam
import org.meshtastic.tak.TakCompressor as SdkCompressor
import org.meshtastic.tak.TakPacketV2Data
import org.meshtastic.tak.TakPacketV2Serializer
import org.meshtastic.tak.CotTypeMapper

/**
 * JVM/Android implementation of TakV2Compressor.
 * Delegates to TAKPacket-SDK's TakCompressor for zstd dictionary compression.
 *
 * The SDK compressor is constructed lazily and its result is cached in a
 * nullable field so that a native-library failure (e.g. missing Android .so)
 * does NOT poison this object. Without lazy/try-catch, a failure inside a
 * top-level `val` initializer runs at class `<clinit>` time, marks the class
 * ERRONEOUS, and turns every subsequent reference into `NoClassDefFoundError`.
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
        sdkCompressorOrNull?.let { return it }
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
     * Convert Wire-generated TAKPacketV2 → SDK's TakPacketV2Data.
     */
    private fun wireToSdkData(packet: TAKPacketV2): TakPacketV2Data {
        val cotTypeId = packet.cot_type_id.value
        val cotTypeStr = if (cotTypeId == 0 && packet.cot_type_str.isNotEmpty()) packet.cot_type_str else null

        val payload = when {
            packet.pli != null -> TakPacketV2Data.Payload.Pli(true)
            packet.chat != null -> TakPacketV2Data.Payload.Chat(
                message = packet.chat!!.message,
                to = packet.chat!!.to,
                toCallsign = packet.chat!!.to_callsign,
                receiptForUid = packet.chat!!.receipt_for_uid,
                receiptType = packet.chat!!.receipt_type.value,
            )
            packet.aircraft != null -> TakPacketV2Data.Payload.Aircraft(
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
                    vertices = s.vertices.map { v ->
                        TakPacketV2Data.Payload.Vertex(
                            latI = packet.latitude_i + v.lat_delta_i,
                            lonI = packet.longitude_i + v.lon_delta_i,
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
                    links = rt.links.map { link ->
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
            else -> TakPacketV2Data.Payload.None
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
            payload = payload,
        )
    }

    /**
     * Convert SDK's TakPacketV2Data → Wire-generated TAKPacketV2.
     */
    private fun sdkDataToWire(data: TakPacketV2Data): TAKPacketV2 {
        val cotType = org.meshtastic.proto.CotType.fromValue(data.cotTypeId)
            ?: org.meshtastic.proto.CotType.CotType_Other
        val how = org.meshtastic.proto.CotHow.fromValue(data.how)
            ?: org.meshtastic.proto.CotHow.CotHow_Unspecified
        val team = org.meshtastic.proto.Team.fromValue(data.team)
            ?: org.meshtastic.proto.Team.Unspecifed_Color
        val role = org.meshtastic.proto.MemberRole.fromValue(data.role)
            ?: org.meshtastic.proto.MemberRole.Unspecifed
        val geoSrc = org.meshtastic.proto.GeoPointSource.fromValue(data.geoSrc)
            ?: org.meshtastic.proto.GeoPointSource.GeoPointSource_Unspecified
        val altSrc = org.meshtastic.proto.GeoPointSource.fromValue(data.altSrc)
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
            pli = if (data.payload is TakPacketV2Data.Payload.Pli) true else null,
            chat = (data.payload as? TakPacketV2Data.Payload.Chat)?.let { chat ->
                WireGeoChat(
                    message = chat.message,
                    to = chat.to,
                    to_callsign = chat.toCallsign,
                    receipt_for_uid = chat.receiptForUid,
                    receipt_type = WireGeoChat.ReceiptType.fromValue(chat.receiptType)
                        ?: WireGeoChat.ReceiptType.ReceiptType_None,
                )
            },
            aircraft = (data.payload as? TakPacketV2Data.Payload.Aircraft)?.let { ac ->
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
            shape = (data.payload as? TakPacketV2Data.Payload.DrawnShape)?.let { s ->
                WireDrawnShape(
                    kind = WireDrawnShape.Kind.fromValue(s.kind) ?: WireDrawnShape.Kind.Kind_Unspecified,
                    style = WireDrawnShape.StyleMode.fromValue(s.style) ?: WireDrawnShape.StyleMode.StyleMode_Unspecified,
                    major_cm = s.majorCm,
                    minor_cm = s.minorCm,
                    angle_deg = s.angleDeg,
                    stroke_color = WireTeam.fromValue(s.strokeColor) ?: WireTeam.Unspecifed_Color,
                    stroke_argb = s.strokeArgb,
                    stroke_weight_x10 = s.strokeWeightX10,
                    fill_color = WireTeam.fromValue(s.fillColor) ?: WireTeam.Unspecifed_Color,
                    fill_argb = s.fillArgb,
                    labels_on = s.labelsOn,
                    // Delta-encode vertices relative to the event anchor.
                    vertices = s.vertices.map { v ->
                        WireCotGeoPoint(
                            lat_delta_i = v.latI - data.latitudeI,
                            lon_delta_i = v.lonI - data.longitudeI,
                        )
                    },
                    truncated = s.truncated,
                    bullseye_distance_dm = s.bullseyeDistanceDm,
                    bullseye_bearing_ref = s.bullseyeBearingRef,
                    bullseye_flags = s.bullseyeFlags,
                    bullseye_uid_ref = s.bullseyeUidRef,
                )
            },
            marker = (data.payload as? TakPacketV2Data.Payload.Marker)?.let { m ->
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
            rab = (data.payload as? TakPacketV2Data.Payload.RangeAndBearing)?.let { r ->
                WireRangeAndBearing(
                    anchor = WireCotGeoPoint(
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
            route = (data.payload as? TakPacketV2Data.Payload.Route)?.let { rt ->
                WireRoute(
                    method = WireRoute.Method.fromValue(rt.method) ?: WireRoute.Method.Method_Unspecified,
                    direction = WireRoute.Direction.fromValue(rt.direction) ?: WireRoute.Direction.Direction_Unspecified,
                    prefix = rt.prefix,
                    stroke_weight_x10 = rt.strokeWeightX10,
                    links = rt.links.map { link ->
                        WireRoute.Link(
                            point = WireCotGeoPoint(
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
            casevac = (data.payload as? TakPacketV2Data.Payload.CasevacReport)?.let { c ->
                WireCasevacReport(
                    precedence = WireCasevacReport.Precedence.fromValue(c.precedence)
                        ?: WireCasevacReport.Precedence.Precedence_Unspecified,
                    equipment_flags = c.equipmentFlags,
                    litter_patients = c.litterPatients,
                    ambulatory_patients = c.ambulatoryPatients,
                    security = WireCasevacReport.Security.fromValue(c.security)
                        ?: WireCasevacReport.Security.Security_Unspecified,
                    hlz_marking = WireCasevacReport.HlzMarking.fromValue(c.hlzMarking)
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
            emergency = (data.payload as? TakPacketV2Data.Payload.EmergencyAlert)?.let { e ->
                WireEmergencyAlert(
                    type = WireEmergencyAlert.Type.fromValue(e.type)
                        ?: WireEmergencyAlert.Type.Type_Unspecified,
                    authoring_uid = e.authoringUid,
                    cancel_reference_uid = e.cancelReferenceUid,
                )
            },
            task = (data.payload as? TakPacketV2Data.Payload.TaskRequest)?.let { t ->
                WireTaskRequest(
                    task_type = t.taskType,
                    target_uid = t.targetUid,
                    assignee_uid = t.assigneeUid,
                    priority = WireTaskRequest.Priority.fromValue(t.priority)
                        ?: WireTaskRequest.Priority.Priority_Unspecified,
                    status = WireTaskRequest.Status.fromValue(t.status)
                        ?: WireTaskRequest.Status.Status_Unspecified,
                    note = t.note,
                )
            },
            raw_detail = (data.payload as? TakPacketV2Data.Payload.RawDetail)?.bytes?.toByteString(),
        )
    }
}
