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

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import nl.adaptivity.xmlutil.serialization.XmlValue

@Serializable
@XmlSerialName("event", "", "")
internal data class CoTEventXml(
    val version: String = "2.0",
    val uid: String,
    val type: String,
    val time: String,
    val start: String,
    val stale: String,
    val how: String,
    @XmlElement(true) val point: CoTPointXml,
    @XmlElement(true) val detail: CoTDetailXml? = null,
)

@Serializable
@XmlSerialName("point", "", "")
internal data class CoTPointXml(val lat: Double, val lon: Double, val hae: Double, val ce: Double, val le: Double)

@Serializable
@XmlSerialName("detail", "", "")
internal data class CoTDetailXml(
    @XmlElement(true) val contact: CoTContactXml? = null,
    @XmlElement(true) @XmlSerialName("__group", "", "") val group: CoTGroupXml? = null,
    @XmlElement(true) val status: CoTStatusXml? = null,
    @XmlElement(true) val track: CoTTrackXml? = null,
    @XmlElement(true) @XmlSerialName("__chat", "", "") val chat: CoTChatXml? = null,
    @XmlElement(true) val remarks: CoTRemarksXml? = null,
)

@Serializable
@XmlSerialName("contact", "", "")
internal data class CoTContactXml(val callsign: String = "", val endpoint: String? = null, val phone: String? = null)

@Serializable
@XmlSerialName("__group", "", "")
internal data class CoTGroupXml(val role: String = "", val name: String = "")

@Serializable
@XmlSerialName("status", "", "")
internal data class CoTStatusXml(val battery: Int = 100)

@Serializable
@XmlSerialName("track", "", "")
internal data class CoTTrackXml(val speed: Double = 0.0, val course: Double = 0.0)

@Serializable
@XmlSerialName("__chat", "", "")
internal data class CoTChatXml(
    val senderCallsign: String? = null,
    val chatroom: String = "All Chat Rooms",
    val id: String? = null,
)

@Serializable
@XmlSerialName("remarks", "", "")
internal data class CoTRemarksXml(
    val source: String? = null,
    val to: String? = null,
    val time: String? = null,
    @XmlValue(true) val value: String = "",
)
