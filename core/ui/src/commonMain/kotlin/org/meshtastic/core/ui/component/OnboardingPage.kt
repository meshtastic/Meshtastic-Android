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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The page shell every onboarding flow shares: the first-launch intro and the radio setup. A page is a centered
 * headline and body, the page's rows, and a bottom bar with one text action and one filled action, the shape of the
 * platform setup wizard.
 */
@Composable
fun OnboardingPageHeader(headline: String, body: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = headline,
            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.padding(top = 16.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

/** One text secondary action on the left and one filled primary action on the right. */
@Composable
fun OnboardingBottomBar(
    primaryText: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    primaryEnabled: Boolean = true,
    secondaryText: String? = null,
    onSecondary: () -> Unit = {},
) {
    // The labels wrap rather than clip when a long one meets a large font scale.
    BottomAppBar(modifier = modifier, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp)) {
        if (secondaryText != null) {
            TextButton(onClick = onSecondary) { Text(secondaryText, textAlign = TextAlign.Center) }
        }
        Spacer(modifier = Modifier.weight(1f))
        Button(onClick = onPrimary, enabled = primaryEnabled) { Text(primaryText, textAlign = TextAlign.Center) }
    }
}

/** An icon, an optional title and a subtitle: one thing the page or the app does. */
data class OnboardingFeature(
    val icon: ImageVector,
    val titleRes: StringResource? = null,
    val subtitleRes: StringResource,
)

@Composable
fun OnboardingFeatureRow(feature: OnboardingFeature, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
        // Decorative: the title beside it already says what the row is, so a description here is read twice.
        Icon(
            imageVector = feature.icon,
            contentDescription = null,
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

/** A notice the page wants read: announced politely so a change after a tap is not missed. */
@Composable
fun OnboardingNotice(text: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
