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

import androidx.annotation.StringRes
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.meshtastic.core.strings.R as Res

/**
 * A generic layout for screens within the app introduction flow. It typically presents a headline, a descriptive text
 * (potentially with clickable annotations), a list of features, and standard navigation buttons.
 *
 * @param headlineRes String resource for the main headline of the screen.
 * @param annotatedDescription The [AnnotatedString] for the main descriptive text.
 * @param features A list of [FeatureUIData] to be displayed using [FeatureRow].
 * @param additionalContent Optional composable lambda for adding custom content below the features.
 * @param onSkip Callback for the skip action.
 * @param onConfigure Callback for the main configure/next action.
 * @param configureButtonTextRes String resource for the main action button.
 * @param onAnnotationClick Callback invoked when a tagged annotation within [annotatedDescription] is clicked.
 */
@Composable
internal fun PermissionScreenLayout(
    @StringRes headlineRes: Int,
    annotatedDescription: AnnotatedString,
    features: List<FeatureUIData>,
    additionalContent: (@Composable () -> Unit)? = null,
    onSkip: () -> Unit,
    onConfigure: () -> Unit,
    @StringRes configureButtonTextRes: Int,
    onAnnotationClick: (String) -> Unit,
) {
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    val pressIndicator =
        Modifier.pointerInput(Unit) {
            detectTapGestures { offset ->
                textLayoutResult?.let { layoutResult ->
                    val position = layoutResult.getOffsetForPosition(offset)
                    annotatedDescription.getStringAnnotations(
                        SETTINGS_TAG,
                        position,
                        position,
                    ).firstOrNull()?.let { annotation ->
                        onAnnotationClick(annotation.item)
                    }
                }
            }
        }

    Scaffold(
        bottomBar = {
            IntroBottomBar(
                onSkip = onSkip,
                onConfigure = onConfigure,
                configureButtonText = stringResource(configureButtonTextRes),
                skipButtonText = stringResource(Res.string.skip),
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
            Modifier.fillMaxSize().padding(innerPadding).padding(16.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(headlineRes),
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = annotatedDescription,
                style =
                MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                ),
                modifier = Modifier.padding(horizontal = 16.dp).then(pressIndicator),
                onTextLayout = { textLayoutResult = it },
            )
            Spacer(modifier = Modifier.height(16.dp))
            features.forEach { feature ->
                FeatureRow(feature = feature)
                Spacer(modifier = Modifier.height(16.dp))
            }
            additionalContent?.invoke()
        }
    }
}
