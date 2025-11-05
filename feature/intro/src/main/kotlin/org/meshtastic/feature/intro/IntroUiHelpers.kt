/*
 * Copyright (c) 2025 Meshtastic LLC
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

package org.meshtastic.feature.intro

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

/** Tag used for identifying clickable annotations in text, specifically for linking to settings. */
internal const val SETTINGS_TAG = "settings_link_tag"

/**
 * Displays a row for a feature, including an icon, an optional title, and a subtitle.
 *
 * @param feature The [FeatureUIData] containing information for the row.
 */
@Composable
internal fun FeatureRow(feature: FeatureUIData) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(
            imageVector = feature.icon,
            contentDescription = feature.titleRes?.let { stringResource(it) } ?: stringResource(feature.subtitleRes),
            modifier = Modifier.padding(end = 16.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Column {
            feature.titleRes?.let { titleRes ->
                Text(
                    text = stringResource(titleRes),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                )
            }
            Text(
                text = stringResource(feature.subtitleRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Creates an [AnnotatedString] with a clickable portion.
 *
 * @param fullTextRes String resource for the entire text.
 * @param linkTextRes String resource for the portion of text that should be clickable.
 * @param tag A tag to identify the annotation.
 * @return An [AnnotatedString] with the specified portion styled and annotated.
 */
@Composable
internal fun Context.createClickableAnnotatedString(
    @StringRes fullTextRes: Int,
    @StringRes linkTextRes: Int,
    tag: String,
): AnnotatedString {
    val fullText = stringResource(fullTextRes)
    val linkText = stringResource(linkTextRes)
    val startIndex = fullText.indexOf(linkText)

    return buildAnnotatedString {
        append(fullText)
        if (startIndex != -1) {
            val endIndex = startIndex + linkText.length
            addStyle(
                style = SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline),
                start = startIndex,
                end = endIndex,
            )
            addStringAnnotation(tag = tag, annotation = linkText, start = startIndex, end = endIndex)
        }
    }
}
