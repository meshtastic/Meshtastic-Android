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

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLinkStyles
import org.jetbrains.compose.resources.StringResource

actual fun createClipEntry(text: String, label: String): ClipEntry =
    throw UnsupportedOperationException("ClipEntry instantiation not supported on iOS stub")

actual fun annotatedStringFromHtml(html: String, linkStyles: TextLinkStyles?): AnnotatedString = AnnotatedString(html)

@Composable actual fun rememberOpenNfcSettings(): () -> Unit = {}

@Composable actual fun rememberShowToast(): suspend (String) -> Unit = { _ -> }

@Composable actual fun rememberShowToastResource(): suspend (StringResource) -> Unit = { _ -> }

@Composable actual fun rememberOpenMap(): (Double, Double, String) -> Unit = { _, _, _ -> }

@Composable actual fun rememberOpenUrl(): (String) -> Unit = { _ -> }

@Composable actual fun SetScreenBrightness(brightness: Float) {}
