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
package org.meshtastic.feature.intro

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.communicate_off_the_grid
import org.meshtastic.core.resources.create_your_own_networks
import org.meshtastic.core.resources.easily_set_up_private_mesh_networks
import org.meshtastic.core.resources.get_started
import org.meshtastic.core.resources.intro_welcome
import org.meshtastic.core.resources.meshtastic
import org.meshtastic.core.resources.share_your_location_in_real_time
import org.meshtastic.core.resources.stay_connected_anywhere
import org.meshtastic.core.resources.track_and_share_locations
import org.meshtastic.core.ui.icon.Antenna
import org.meshtastic.core.ui.icon.MeshHub
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.NearMe
import org.meshtastic.core.ui.util.LocalAnalyticsIntroProvider

/**
 * The initial welcome screen for the app introduction flow. It displays a brief overview of the app's key features.
 *
 * @param onGetStarted Callback invoked when the user proceeds from the welcome screen.
 */
@Composable
internal fun WelcomeScreen(onGetStarted: () -> Unit) {
    val analyticsIntro = LocalAnalyticsIntroProvider.current
    val features =
        listOf(
            FeatureUIData(
                icon = MeshtasticIcons.Antenna,
                titleRes = Res.string.stay_connected_anywhere,
                subtitleRes = Res.string.communicate_off_the_grid,
            ),
            FeatureUIData(
                icon = MeshtasticIcons.MeshHub,
                titleRes = Res.string.create_your_own_networks,
                subtitleRes = Res.string.easily_set_up_private_mesh_networks,
            ),
            FeatureUIData(
                icon = MeshtasticIcons.NearMe,
                titleRes = Res.string.track_and_share_locations,
                subtitleRes = Res.string.share_your_location_in_real_time,
            ),
        )

    Scaffold(
        bottomBar = {
            IntroBottomBar(
                onSkip = {}, // No skip on welcome
                onConfigure = onGetStarted,
                skipButtonText = "", // Not shown
                configureButtonText = stringResource(Res.string.get_started),
                showSkipButton = false, // Explicitly hide skip for welcome
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
            Modifier.fillMaxSize().padding(innerPadding).padding(16.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(Res.string.intro_welcome),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(Res.string.meshtastic),
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(32.dp))
            features.forEach { feature ->
                FeatureRow(feature = feature)
                Spacer(modifier = Modifier.height(16.dp))
            }
            Spacer(modifier = Modifier.weight(1f))
            analyticsIntro()
        }
    }
}

@Preview
@Composable
private fun WelcomeScreenPreview() {
    WelcomeScreen(onGetStarted = {})
}
