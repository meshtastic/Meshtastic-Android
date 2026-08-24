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
import org.meshtastic.core.model.EventFirmwareLink
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.ic_meshtastic
import org.meshtastic.core.resources.img_event_defcon
import org.meshtastic.core.resources.img_event_hamvention
import kotlin.time.Clock

/**
 * Provides the active [EventFirmwareEdition] (if any) to the composition tree. When a connected device reports an event
 * firmware edition, this local is populated at the app root so consumers can pick it up without per-screen wiring: the
 * Connections screen surfaces the edition itself, and [LocalEventTheme][org.meshtastic.core.ui.theme.LocalEventTheme]
 * derives the ambient theme from it. The app-bar logo is deliberately *not* one of those consumers — the Meshtastic
 * identity stays in place regardless of the firmware running on the device.
 */
@Suppress("CompositionLocalAllowlist")
val LocalEventBranding = compositionLocalOf<EventFirmwareEdition?> { null }

/**
 * Bundled branding drawable for an edition, or `null`. Used as the offline fallback by [EventBrandingIcon] when a
 * hosted [EventFirmwareEdition.iconUrl] is absent or fails to load.
 */
fun eventIconFor(editionName: String): DrawableResource? = when (editionName) {
    "HAMVENTION" -> Res.drawable.img_event_hamvention
    "DEFCON" -> Res.drawable.img_event_defcon
    else -> null
}

/**
 * Whether [url] is safe to fetch or open from event metadata: an absolute `https` URL whose authority is a valid host
 * with an optional numeric port.
 *
 * The manifest is first-party but arrives over the network, and its URLs reach an image loader and the platform URI
 * handler. A URI handler will honour whatever scheme it is given, so without this check a bad or tampered manifest
 * entry could invoke arbitrary handlers on the device. Scheme comparison is case-insensitive; everything else is
 * rejected, including protocol-relative (`//host`) and scheme-relative input.
 *
 * The authority is matched whole rather than merely tested for emptiness, so a port with no host (`https://:443`),
 * whitespace, a truncated IPv6 literal, and userinfo (`https://trusted.host@evil.example`, which wears a trusted
 * prefix) are all rejected — none of those characters appear in the permitted set.
 */
fun safeBrandUrlOrNull(url: String?): String? {
    val trimmed = url?.trim().orEmpty()
    if (!trimmed.startsWith(HTTPS_SCHEME, ignoreCase = true)) return null
    val authority = trimmed.drop(HTTPS_SCHEME.length).takeWhile { it != '/' && it != '?' && it != '#' }
    // Return the trimmed form, not the caller's original: consumers must use the string that was actually validated,
    // or a padded-but-otherwise-valid URL passes here and then fails as a malformed URI at the image loader.
    return trimmed.takeIf { AUTHORITY_REGEX.matches(authority) }
}

/** Whether [url] is safe to fetch or open — see [safeBrandUrlOrNull], which callers should prefer for the value. */
fun isSafeBrandUrl(url: String?): Boolean = safeBrandUrlOrNull(url) != null

/**
 * Links that are safe to open, carrying the normalized URL — see [safeBrandUrlOrNull]. Unsafe or blank entries are
 * dropped rather than rendered and refused on tap.
 */
fun EventFirmwareEdition.safeLinks(): List<EventFirmwareLink> =
    links.mapNotNull { link -> safeBrandUrlOrNull(link.url)?.let { link.copy(url = it) } }

/**
 * Event branding icon: loads the hosted [EventFirmwareEdition.iconUrl] when present *and* safe to fetch, falling back
 * to the bundled per-edition drawable ([eventIconFor]), and finally the Meshtastic logo. The fallback painter also
 * backs Coil's loading/error states so there is never an empty slot.
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
    val url = safeBrandUrlOrNull(edition.iconUrl)
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

/**
 * Parses a `#RRGGBB` brand color into a [Color], or `null` if absent/malformed. Tolerates a missing `#` and any case.
 */
fun parseBrandColor(hex: String?): Color? {
    val rgb =
        hex?.trim()?.removePrefix("#")?.takeIf { it.length == RGB_HEX_LENGTH }?.toIntOrNull(HEX_RADIX) ?: return null
    return Color(
        red = (rgb shr RED_SHIFT) and BYTE_MASK,
        green = (rgb shr GREEN_SHIFT) and BYTE_MASK,
        blue = rgb and BYTE_MASK,
    )
}

/** Parses the edition's `#RRGGBB` [EventFirmwareEdition.accentColor] into a [Color], or `null` if absent/malformed. */
fun EventFirmwareEdition.accentColorOrNull(): Color? = parseBrandColor(accentColor)

/**
 * The edition's full brand palette (`theme.palette`) as colors, malformed entries dropped. Falls back to the named
 * `theme.colors` (primary/secondary/accent) and finally the top-level [accentColor][EventFirmwareEdition.accentColor],
 * so an edition that carries only one color still yields a usable, non-empty list where one exists.
 */
fun EventFirmwareEdition.brandPalette(): List<Color> {
    // distinct() on both paths: a repeated hex would otherwise become a repeated gradient stop, flattening the
    // gradient over that span. Order is preserved, so the authored reading order still holds.
    val fromPalette = theme?.palette.orEmpty().mapNotNull(::parseBrandColor).distinct()
    if (fromPalette.isNotEmpty()) return fromPalette
    val named = listOfNotNull(theme?.colors?.primary, theme?.colors?.secondary, theme?.colors?.accent, accentColor)
    return named.mapNotNull(::parseBrandColor).distinct()
}

/**
 * The edition's high-contrast detail color — `theme.colors.accent` (DEF CON's pink against its navy primary), falling
 * back to `theme.colors.secondary`. Distinct from [accentColorOrNull], which is the *primary* used for the ambient
 * wash: the wash wants the calm color, small details want the loud one.
 */
fun EventFirmwareEdition.brandHighlightOrNull(): Color? =
    parseBrandColor(theme?.colors?.accent) ?: parseBrandColor(theme?.colors?.secondary)

private const val HTTPS_SCHEME = "https://"

/** A registered name or bracketed IPv6 literal, plus an optional numeric port. See [isSafeBrandUrl]. */
private val AUTHORITY_REGEX = Regex("""(?:[A-Za-z0-9._~-]+|\[[0-9A-Fa-f:.]+])(?::\d+)?""")
private const val RGB_HEX_LENGTH = 6
private const val HEX_RADIX = 16
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
private const val BYTE_MASK = 0xFF
