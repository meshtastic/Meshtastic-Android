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

package com.geeksville.mesh.ui.intro

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.SettingsInputAntenna
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.geeksville.mesh.R

/**
 * The initial welcome screen for the app introduction flow. It displays a brief overview of the app's key features.
 *
 * @param onGetStarted Callback invoked when the user proceeds from the welcome screen.
 */
@Composable
internal fun WelcomeScreen(onGetStarted: () -> Unit) {
    val features = remember {
        listOf(
            FeatureUIData(
                icon = Icons.Outlined.SettingsInputAntenna,
                titleRes = R.string.stay_connected_anywhere,
                subtitleRes = R.string.communicate_off_the_grid,
            ),
            FeatureUIData(
                icon = Icons.Outlined.Hub,
                titleRes = R.string.create_your_own_networks,
                subtitleRes = R.string.easily_set_up_private_mesh_networks,
            ),
            FeatureUIData(
                icon = Icons.Outlined.NearMe,
                titleRes = R.string.track_and_share_locations,
                subtitleRes = R.string.share_your_location_in_real_time,
            ),
        )
    }

    Scaffold(
        bottomBar = {
            IntroBottomBar(
                onSkip = {}, // No skip on welcome
                onConfigure = onGetStarted,
                skipButtonText = "", // Not shown
                configureButtonText = stringResource(id = R.string.get_started),
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
                text = stringResource(R.string.intro_welcome),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.meshtastic),
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(32.dp))
            features.forEach { feature ->
                FeatureRow(feature = feature)
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
