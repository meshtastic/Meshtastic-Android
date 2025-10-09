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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.meshtastic.core.ui.component.AutoLinkText

@Composable
fun AnalyticsIntro(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val textModifier = Modifier.fillMaxWidth().align(Alignment.CenterHorizontally)
        Text(
            modifier = textModifier,
            textAlign = TextAlign.Center,
            text =
            "Analytics are collected to help us improve the Android app (thank you), we will receive anonymized information about user behavior. This includes crash reports, screens used in the app, etc.",
        )
        Text(modifier = textModifier, textAlign = TextAlign.Center, text = "Analytics platforms used are: ")
        AutoLinkText("Firebase https://firebase.google.com/")
        AutoLinkText("Datadog  https://www.datadoghq.com/")

        Text(
            modifier = textModifier,
            textAlign = TextAlign.Center,
            text = "For more information, see our privacy policy.",
        )
        AutoLinkText(text = " https://meshtastic.org/docs/legal/privacy/")
    }
}

@Preview(showBackground = true)
@Composable
private fun AnalyticsIntroPreview() {
    AnalyticsIntro()
}
