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
package org.meshtastic.core.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

/**
 * Response envelope for event-firmware display metadata. Matches the bundled `event_firmware.json` and the planned `GET
 * /resource/eventFirmware` API, sharing the `{version, generatedAt, source, <payload>[]}` shape used by
 * [NetworkDeviceLinksResponse]. See `schemas/event_firmware.schema.json` for the authoring contract.
 */
@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonIgnoreUnknownKeys
data class EventFirmwareResponse(
    val version: Int = 1,
    val generatedAt: String? = null,
    val source: String? = null,
    val editions: List<EventFirmwareEdition> = emptyList(),
)

/**
 * Display metadata for one event-specific firmware edition (Hamvention, DEFCON, …).
 *
 * @param edition the `FirmwareEdition` proto enum name, e.g. `HAMVENTION` — the key a connected device's reported
 *   edition is matched against.
 * @param displayName human-readable name, e.g. `Hamvention` (vs the enum name `HAMVENTION`).
 * @param welcomeMessage plain English welcome string. NOTE: intentionally not localized — moving it into data drops the
 *   Crowdin per-language translation the string resources gave; revisit with the upstream API (see PR #5920).
 * @param eventStart ISO-8601 date (`YYYY-MM-DD`) the event begins, in [timeZone].
 * @param eventEnd ISO-8601 date the event ends, in [timeZone].
 * @param timeZone IANA time-zone id so [eventStart]/[eventEnd] resolve in the event's local time.
 * @param location free-text venue/city for display.
 * @param iconUrl URL of the event branding icon; `null` until icons are hosted (bundled drawables fill in meanwhile).
 * @param accentColor `#RRGGBB` brand color for ambient theming (not yet consumed).
 * @param links labeled links for the event (website, schedule, …).
 */
@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonIgnoreUnknownKeys
data class EventFirmwareEdition(
    val edition: String = "",
    val displayName: String = "",
    val welcomeMessage: String = "",
    val eventStart: String? = null,
    val eventEnd: String? = null,
    val timeZone: String? = null,
    val location: String? = null,
    val iconUrl: String? = null,
    val accentColor: String? = null,
    val links: List<EventFirmwareLink> = emptyList(),
)

/** A labeled link for an event (website, schedule, map, …). */
@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonIgnoreUnknownKeys
data class EventFirmwareLink(val label: String = "", val url: String = "")
