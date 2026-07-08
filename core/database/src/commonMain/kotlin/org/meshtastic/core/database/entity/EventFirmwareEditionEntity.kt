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
package org.meshtastic.core.database.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.meshtastic.core.model.EventFirmwareEdition
import org.meshtastic.core.model.EventFirmwareLink

/**
 * An event-firmware display record, cached from the Meshtastic API (`/resource/eventFirmware`) during refresh. [links]
 * is stored pre-serialized rather than via a Room type converter — it's the only entity that needs a
 * `List<EventFirmwareLink>` column, so a shared [org.meshtastic.core.database.Converters] entry would be unused
 * elsewhere.
 */
@Serializable
@Entity(tableName = "event_firmware_edition")
data class EventFirmwareEditionEntity(
    @PrimaryKey val edition: String,
    @ColumnInfo(name = "display_name") val displayName: String = "",
    @ColumnInfo(name = "welcome_message") val welcomeMessage: String = "",
    @ColumnInfo(name = "event_start") val eventStart: String? = null,
    @ColumnInfo(name = "event_end") val eventEnd: String? = null,
    @ColumnInfo(name = "time_zone") val timeZone: String? = null,
    val location: String? = null,
    @ColumnInfo(name = "icon_url") val iconUrl: String? = null,
    @ColumnInfo(name = "accent_color") val accentColor: String? = null,
    @ColumnInfo(name = "links_json") val linksJson: String = "[]",
)

fun EventFirmwareEdition.asEntity() = EventFirmwareEditionEntity(
    edition = edition,
    displayName = displayName,
    welcomeMessage = welcomeMessage,
    eventStart = eventStart,
    eventEnd = eventEnd,
    timeZone = timeZone,
    location = location,
    iconUrl = iconUrl,
    accentColor = accentColor,
    linksJson = Json.encodeToString(links),
)

fun EventFirmwareEditionEntity.asExternalModel() = EventFirmwareEdition(
    edition = edition,
    displayName = displayName,
    welcomeMessage = welcomeMessage,
    eventStart = eventStart,
    eventEnd = eventEnd,
    timeZone = timeZone,
    location = location,
    iconUrl = iconUrl,
    accentColor = accentColor,
    links = runCatching { Json.decodeFromString<List<EventFirmwareLink>>(linksJson) }.getOrDefault(emptyList()),
)
