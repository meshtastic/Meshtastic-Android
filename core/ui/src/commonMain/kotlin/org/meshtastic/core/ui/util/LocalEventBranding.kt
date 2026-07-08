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

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.vectorResource
import org.meshtastic.core.model.EventFirmwareEdition
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.ic_meshtastic
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
 * Bundled branding drawable for an edition, or `null`. Used as the offline fallback by [EventBrandingIcon] when a
 * hosted [EventFirmwareEdition.iconUrl] is absent or fails to load.
 */
fun eventIconFor(editionName: String): DrawableResource? = when (editionName) {
    "HAMVENTION" -> Res.drawable.img_event_hamvention
    else -> null
}

/**
 * Event branding icon: loads the hosted [EventFirmwareEdition.iconUrl] when present, falling back to the bundled
 * per-edition drawable ([eventIconFor]), and finally the Meshtastic logo. The fallback painter also backs Coil's
 * loading/error states so there is never an empty slot.
 */
@Composable
fun EventBrandingIcon(
    edition: EventFirmwareEdition,
    modifier: Modifier = Modifier,
    contentDescription: String? = edition.displayName,
) {
    val bundled = eventIconFor(edition.edition)
    val fallback =
        bundled?.let { painterResource(it) } ?: rememberVectorPainter(vectorResource(Res.drawable.ic_meshtastic))
    val url = edition.iconUrl
    if (url.isNullOrBlank()) {
        Image(
            painter = fallback,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = modifier,
        )
    } else {
        AsyncImage(
            model = url,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Fit,
            placeholder = fallback,
            error = fallback,
            fallback = fallback,
        )
    }
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
    return Clock.System.todayIn(zone) > end
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
