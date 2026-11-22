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
package org.meshtastic.core.ui.component

private val SENDER_LINK_SCHEMES = setOf("http", "https", "mailto")

private val SCHEME_REGEX = Regex("""^[A-Za-z][A-Za-z0-9+.\-]*$""")

// A web address names its host right after `://`; browsers read other shapes differently than a plain split does.
private val WEB_HOST_REGEX = Regex("""^[A-Za-z]+://[^/?#]""")

// Markdown emphasis, code and escape characters, which the rendered label does not show.
private val LABEL_MARKUP = setOf('*', '_', '~', '`', '\\')

private val DOMAIN_LABEL_REGEX =
    Regex(
        """^(?:[a-z][a-z0-9+.\-]*://)?[a-z0-9\-]+(?:\.[a-z0-9\-]+)*""" + """\.[a-z]{2,}(?:[/:?#].*)?$""",
        RegexOption.IGNORE_CASE,
    )

/**
 * Returns the link target to open for [raw], or null when the text must not become a clickable link.
 *
 * Message links open only web and mail addresses: `http`, `https` and `mailto` (plus `tel` when [allowTel] is set, for
 * phone numbers the app detects itself). Surrounding whitespace and one pair of angle brackets are removed, the scheme
 * is compared case-insensitively, and a target containing whitespace, control characters or a backslash, without an
 * explicit scheme, or a web address with no host after `://`, is rejected.
 */
internal fun openableLinkTarget(raw: String, allowTel: Boolean = false): String? {
    val target = raw.trim().removeSurrounding("<", ">").trim()
    val scheme = target.substringBefore(':', "")
    val wellFormed =
        SCHEME_REGEX.matches(scheme) && target.none { it.isWhitespace() || it.isISOControl() || it == '\\' }
    val allowed = scheme.lowercase() in SENDER_LINK_SCHEMES || (allowTel && scheme.equals("tel", ignoreCase = true))
    val hasHost = !scheme.lowercase().startsWith("http") || WEB_HOST_REGEX.containsMatchIn(target)
    return if (wellFormed && allowed && hasHost) target else null
}

/**
 * True when a markdown link [label] reads as a web address or domain whose host differs from the host of [target], so
 * showing the label would present one site while opening another.
 */
internal fun labelNamesDifferentHost(label: String, target: String): Boolean {
    val trimmed = label.filterNot { it in LABEL_MARKUP }.trim()
    if (!DOMAIN_LABEL_REGEX.matches(trimmed)) return false
    return hostOf(trimmed) != hostOf(target)
}

private fun hostOf(address: String): String {
    var rest = address.trim().substringAfter("://", address.trim())
    if (address.trim().startsWith("mailto:", ignoreCase = true)) {
        rest = address.trim().substringAfter(':').substringAfter('@').substringBefore('?')
    }
    rest = rest.substringBefore('/').substringBefore('?').substringBefore('#').substringAfterLast('@')
    rest = rest.substringBefore(':')
    return rest.lowercase().removePrefix("www.")
}
