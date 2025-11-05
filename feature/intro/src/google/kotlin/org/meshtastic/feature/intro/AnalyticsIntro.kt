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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.meshtastic.core.ui.component.AutoLinkText
import org.meshtastic.core.strings.R as Res

@Composable
fun AnalyticsIntro(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val textModifier = Modifier.fillMaxWidth().align(Alignment.CenterHorizontally)
        Text(modifier = textModifier, textAlign = TextAlign.Center, text = stringResource(Res.string.analytics_notice))
        Text(
            modifier = textModifier,
            textAlign = TextAlign.Center,
            text = stringResource(Res.string.analytics_platforms),
        )
        AutoLinkText(stringResource(Res.string.firebase_link))
        AutoLinkText(stringResource(Res.string.datadog_link))

        Text(
            modifier = textModifier,
            textAlign = TextAlign.Center,
            text = stringResource(Res.string.for_more_information_see_our_privacy_policy),
        )
        AutoLinkText(text = stringResource(Res.string.privacy_url))
    }
}

@Preview(showBackground = true)
@Composable
private fun AnalyticsIntroPreview() {
    AnalyticsIntro()
}
