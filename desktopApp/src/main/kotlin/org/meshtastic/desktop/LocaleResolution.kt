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
package org.meshtastic.desktop

import java.util.Locale

/**
 * Resolves the locale to install as the JVM default from the language preference and the system locale.
 *
 * The preference list holds language-only tags such as `en`, and taking one verbatim discards the region — which also
 * decides the measurement system, so picking English used to switch a user in a metric country to feet. Choosing a
 * language is not choosing a region, so the system region is carried over whenever the preference omits one.
 */
internal fun resolveLocale(localePref: String, systemLocale: Locale): Locale {
    val preferred = localePref.takeIf { it.isNotEmpty() }?.let(Locale::forLanguageTag) ?: return systemLocale

    // Builder rejects legacy or ill-formed fields; the preference as written is the safe answer there.
    return if (preferred.country.isNotEmpty() || systemLocale.country.isEmpty()) {
        preferred
    } else {
        runCatching { Locale.Builder().setLocale(preferred).setRegion(systemLocale.country).build() }
            .getOrDefault(preferred)
    }
}
