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

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLinkStyles

// androidMain's actual (`AnnotatedString.fromHtml`) is a genuinely Android-specific real HTML renderer — it isn't a
// common Compose Multiplatform API, so it isn't available here. The desktop JVM actual, despite running on a full
// desktop JVM with far more capability than a browser sandbox, *also* takes the plain-text shortcut below rather than
// pulling in an HTML parser — that's the established bar for "good enough" fidelity on every non-Android platform in
// this codebase today, so wasmJs matches it rather than reaching for `org.jetbrains:markdown`-style HTML parsing
// (this module already depends on that library for actual Markdown, but it doesn't parse HTML) or hand-rolling a
// `dangerouslySetInnerHTML`-style DOM injection, which would also sidestep Compose's own text layout/selection.
/** Web stub — returns the raw HTML as plain text (no HTML rendering), matching the JVM/Desktop and iOS actuals. */
actual fun annotatedStringFromHtml(html: String, linkStyles: TextLinkStyles?): AnnotatedString = AnnotatedString(html)
