/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package com.geeksville.mesh.service

import org.meshtastic.core.api.MeshtasticIntent

const val PREFIX = "com.geeksville.mesh"

const val ACTION_NODE_CHANGE = MeshtasticIntent.ACTION_NODE_CHANGE
const val ACTION_MESH_CONNECTED = MeshtasticIntent.ACTION_MESH_CONNECTED
const val ACTION_MESH_DISCONNECTED = MeshtasticIntent.ACTION_MESH_DISCONNECTED
const val ACTION_CONNECTION_CHANGED = MeshtasticIntent.ACTION_CONNECTION_CHANGED
const val ACTION_MESSAGE_STATUS = MeshtasticIntent.ACTION_MESSAGE_STATUS

const val ACTION_RECEIVED_TEXT_MESSAGE_APP = MeshtasticIntent.ACTION_RECEIVED_TEXT_MESSAGE_APP
const val ACTION_RECEIVED_POSITION_APP = MeshtasticIntent.ACTION_RECEIVED_POSITION_APP
const val ACTION_RECEIVED_NODEINFO_APP = MeshtasticIntent.ACTION_RECEIVED_NODEINFO_APP
const val ACTION_RECEIVED_TELEMETRY_APP = MeshtasticIntent.ACTION_RECEIVED_TELEMETRY_APP
const val ACTION_RECEIVED_ATAK_PLUGIN = MeshtasticIntent.ACTION_RECEIVED_ATAK_PLUGIN
const val ACTION_RECEIVED_ATAK_FORWARDER = MeshtasticIntent.ACTION_RECEIVED_ATAK_FORWARDER
const val ACTION_RECEIVED_DETECTION_SENSOR_APP = MeshtasticIntent.ACTION_RECEIVED_DETECTION_SENSOR_APP
const val ACTION_RECEIVED_PRIVATE_APP = MeshtasticIntent.ACTION_RECEIVED_PRIVATE_APP

fun actionReceived(portNum: String) = "$PREFIX.RECEIVED.$portNum"

//
// standard EXTRA bundle definitions
//

const val EXTRA_CONNECTED = MeshtasticIntent.EXTRA_CONNECTED
const val EXTRA_PROGRESS = "$PREFIX.Progress"
const val EXTRA_PERMANENT = "$PREFIX.Permanent"

const val EXTRA_PAYLOAD = MeshtasticIntent.EXTRA_PAYLOAD
const val EXTRA_NODEINFO = MeshtasticIntent.EXTRA_NODEINFO
const val EXTRA_PACKET_ID = MeshtasticIntent.EXTRA_PACKET_ID
const val EXTRA_STATUS = MeshtasticIntent.EXTRA_STATUS
