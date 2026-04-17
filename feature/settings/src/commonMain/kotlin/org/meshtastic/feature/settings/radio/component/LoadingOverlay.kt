/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package org.meshtastic.feature.settings.radio.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.meshtastic.core.common.util.MetricFormatter
import org.meshtastic.feature.settings.radio.ResponseState

private const val LOADING_OVERLAY_ALPHA = 0.8f
private const val PERCENTAGE_FACTOR = 100

@Composable
fun LoadingOverlay(state: ResponseState<*>, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible = state is ResponseState.Loading, enter = fadeIn(), exit = fadeOut()) {
        Box(
            modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = LOADING_OVERLAY_ALPHA))
                .clickable(enabled = false) {},
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.padding(32.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                if (state is ResponseState.Loading) {
                    val clampedProgress =
                        (state.completed.toFloat() / state.total.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
                    val progress by animateFloatAsState(targetValue = clampedProgress, label = "loadingProgress")

                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.size(80.dp),
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                        Text(
                            text = MetricFormatter.percent(progress * PERCENTAGE_FACTOR, decimalPlaces = 0),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    state.status?.let { status ->
                        Text(
                            text = status,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
