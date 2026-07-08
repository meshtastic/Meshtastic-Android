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
 * Response envelope for event-firmware display metadata. Matches the bundled `event_firmware.json` and the live `GET
 * https://api.meshtastic.org/resource/eventFirmware` API (the source of truth), sharing the `{version, generatedAt,
 * source, <payload>[]}` shape used by [NetworkDeviceLinksResponse].
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
 * @param iconUrl URL of the event branding icon (hosted on api.meshtastic.org); bundled drawables are the fallback.
 * @param accentColor `#RRGGBB` brand color for ambient theming.
 * @param tag short label for the event, e.g. `Hamvention`.
 * @param domain per-event microsite host, e.g. `hamvention.meshtastic.org`.
 * @param theme richer branding (tagline, palette, …) layered over [accentColor]; optional, not all editions carry it.
 * @param firmware the event's own firmware build (version + download); lets a client offer/flash it directly.
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
    val tag: String? = null,
    val domain: String? = null,
    val theme: EventFirmwareTheme? = null,
    val firmware: EventFirmwareBuild? = null,
    val links: List<EventFirmwareLink> = emptyList(),
)

/** A labeled link for an event (website, schedule, map, …). */
@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonIgnoreUnknownKeys
data class EventFirmwareLink(val label: String = "", val url: String = "")

/**
 * Richer event branding beyond a single accent color. `fonts` is intentionally omitted — it is `null` in the current
 * feed and its shape is unspecified, so it is left to [JsonIgnoreUnknownKeys] rather than modeled prematurely.
 */
@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonIgnoreUnknownKeys
data class EventFirmwareTheme(
    val name: String? = null,
    val tagline: String? = null,
    val colors: EventFirmwareThemeColors? = null,
    val palette: List<String> = emptyList(),
)

/** Named brand colors (`#RRGGBB`) for an event theme; any may be absent. */
@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonIgnoreUnknownKeys
data class EventFirmwareThemeColors(
    val primary: String? = null,
    val secondary: String? = null,
    val accent: String? = null,
)

/** The event's own firmware release, so a client can surface its version or offer the download ([zipUrl]). */
@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonIgnoreUnknownKeys
data class EventFirmwareBuild(
    val slug: String? = null,
    val version: String? = null,
    val id: String? = null,
    val title: String? = null,
    val zipUrl: String? = null,
    val releaseNotes: String? = null,
)
