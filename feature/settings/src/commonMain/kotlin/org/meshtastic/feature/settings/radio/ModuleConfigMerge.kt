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
package org.meshtastic.feature.settings.radio

import org.meshtastic.proto.LocalModuleConfig
import org.meshtastic.proto.ModuleConfig

/** Replaces the section [config] carries and keeps every other section. */
internal fun LocalModuleConfig.mergedWith(config: ModuleConfig): LocalModuleConfig = newBuilder()
    .also { wb ->
        wb.mqtt = config.mqtt ?: mqtt
        wb.serial = config.serial ?: serial
        wb.external_notification = config.external_notification ?: external_notification
        wb.store_forward = config.store_forward ?: store_forward
        wb.range_test = config.range_test ?: range_test
        wb.telemetry = config.telemetry ?: telemetry
        wb.canned_message = config.canned_message ?: canned_message
        wb.audio = config.audio ?: audio
        wb.remote_hardware = config.remote_hardware ?: remote_hardware
        wb.neighbor_info = config.neighbor_info ?: neighbor_info
        wb.ambient_lighting = config.ambient_lighting ?: ambient_lighting
        wb.detection_sensor = config.detection_sensor ?: detection_sensor
        wb.paxcounter = config.paxcounter ?: paxcounter
        wb.statusmessage = config.statusmessage ?: statusmessage
        wb.traffic_management = config.traffic_management ?: traffic_management
        wb.tak = config.tak ?: tak
        wb.mesh_beacon = config.mesh_beacon ?: mesh_beacon
    }
    .build()
