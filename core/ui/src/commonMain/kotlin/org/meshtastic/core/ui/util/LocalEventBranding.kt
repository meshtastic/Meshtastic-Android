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
package org.meshtastic.core.ui.util

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.DrawableResource
import org.meshtastic.core.model.EventFirmwareEdition
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.img_event_hamvention
import kotlin.time.Clock

/**
 * Provides the active [EventFirmwareEdition] (if any) to the composition tree. When a connected device reports an event
 * firmware edition, this local is populated at the app root so that
 * [MainAppBar][org.meshtastic.core.ui.component.MainAppBar] can display event branding automatically — no per-screen
 * wiring needed.
 */
@Suppress("CompositionLocalAllowlist")
val LocalEventBranding = compositionLocalOf<EventFirmwareEdition?> { null }

/**
 * Bundled branding drawable for an edition, or `null`. The metadata's `iconUrl` is null until icons are hosted, so the
 * one drawable we ship stays code-mapped here; remove this once icons are loaded from [EventFirmwareEdition.iconUrl].
 */
fun eventIconFor(editionName: String): DrawableResource? = when (editionName) {
    "HAMVENTION" -> Res.drawable.img_event_hamvention
    else -> null
}

/**
 * Whether the event's last day is in the past, evaluated in the event's own [timeZone][EventFirmwareEdition.timeZone]
 * (falling back to the device time zone). Used to nudge users off event firmware once the event is over. Returns
 * `false` when [eventEnd][EventFirmwareEdition.eventEnd] is absent or unparseable — an unknown end date is never
 * treated as ended.
 */
fun EventFirmwareEdition.hasEnded(): Boolean {
    val end = eventEnd?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return false
    val zone = timeZone?.let { runCatching { TimeZone.of(it) }.getOrNull() } ?: TimeZone.currentSystemDefault()
    return Clock.System.now().toLocalDateTime(zone).date > end
}

/** Parses the edition's `#RRGGBB` [EventFirmwareEdition.accentColor] into a [Color], or `null` if absent/malformed. */
fun EventFirmwareEdition.accentColorOrNull(): Color? {
    val rgb =
        accentColor?.trim()?.removePrefix("#")?.takeIf { it.length == RGB_HEX_LENGTH }?.toIntOrNull(HEX_RADIX)
            ?: return null
    return Color(
        red = (rgb shr RED_SHIFT) and BYTE_MASK,
        green = (rgb shr GREEN_SHIFT) and BYTE_MASK,
        blue = rgb and BYTE_MASK,
    )
}

private const val RGB_HEX_LENGTH = 6
private const val HEX_RADIX = 16
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
private const val BYTE_MASK = 0xFF
