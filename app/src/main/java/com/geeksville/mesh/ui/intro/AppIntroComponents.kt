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

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Message
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.SettingsInputAntenna
import androidx.compose.material.icons.outlined.SpeakerPhone
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.geeksville.mesh.R
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.rememberPermissionState

/** Sealed class defining type-safe navigation routes for the app introduction flow. */
sealed class IntroRoute(val route: String) {
    object Welcome : IntroRoute("welcome")

    object Notifications : IntroRoute("notifications")

    object Location : IntroRoute("location")

    object CriticalAlerts : IntroRoute("critical_alerts")
}

private const val SETTINGS_TAG = "settings_link_tag"

/**
 * Data class representing the UI elements for a feature row.
 *
 * @param icon The vector asset for the feature icon.
 * @param titleRes Optional string resource ID for the feature title.
 * @param subtitleRes String resource ID for the feature subtitle.
 */
private data class FeatureUIData(
    val icon: ImageVector,
    @StringRes val titleRes: Int? = null,
    @StringRes val subtitleRes: Int,
)

/**
 * Composable function for the main application introduction screen. This screen guides the user through initial setup
 * steps like granting permissions.
 *
 * @param onDone Callback invoked when the introduction flow is completed.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun AppIntroductionScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val navController = rememberNavController()

    val notificationPermissionState: PermissionState? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            null
        }

    val locationPermissions =
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    val locationPermissionState = rememberMultiplePermissionsState(permissions = locationPermissions)

    NavHost(navController = navController, startDestination = IntroRoute.Welcome.route) {
        composable(IntroRoute.Welcome.route) {
            WelcomeScreen(onGetStarted = { navController.navigate(IntroRoute.Notifications.route) })
        }
        composable(IntroRoute.Notifications.route) {
            val notificationsAlreadyGranted = notificationPermissionState?.status?.isGranted ?: true
            NotificationsScreen(
                showNextButton = notificationsAlreadyGranted,
                onSkip = { navController.navigate(IntroRoute.Location.route) },
                onConfigure = {
                    if (notificationsAlreadyGranted) {
                        navController.navigate(IntroRoute.CriticalAlerts.route)
                    } else {
                        notificationPermissionState.launchPermissionRequest()
                    }
                },
            )
        }
        composable(IntroRoute.CriticalAlerts.route) {
            CriticalAlertsScreen(
                onSkip = { navController.navigate(IntroRoute.Location.route) },
                onConfigure = {
                    val intent =
                        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            putExtra(Settings.EXTRA_CHANNEL_ID, "my_alerts")
                        }
                    context.startActivity(intent)
                    navController.navigate(IntroRoute.Location.route)
                },
            )
        }
        composable(IntroRoute.Location.route) {
            val locationAlreadyGranted = locationPermissionState.allPermissionsGranted
            LocationScreen(
                showNextButton = locationAlreadyGranted,
                onSkip = onDone,
                onConfigure = {
                    if (locationAlreadyGranted) {
                        onDone()
                    } else {
                        locationPermissionState.launchMultiplePermissionRequest()
                    }
                },
            )
        }
    }
}

@Composable
private fun WelcomeScreen(onGetStarted: () -> Unit) {
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
                onSkip = {}, // No skip on welcome, but could be passed if needed
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

@Composable
private fun NotificationsScreen(showNextButton: Boolean, onSkip: () -> Unit, onConfigure: () -> Unit) {
    val context = LocalContext.current
    val annotatedString =
        context.createClickableAnnotatedString(
            fullTextRes = R.string.notification_permissions_description,
            linkTextRes = R.string.settings,
            tag = SETTINGS_TAG,
        )

    val features = remember {
        listOf(
            FeatureUIData(
                icon = Icons.Outlined.Message,
                titleRes = R.string.incoming_messages,
                subtitleRes = R.string.notifications_for_channel_and_direct_messages,
            ),
            FeatureUIData(
                icon = Icons.Outlined.SpeakerPhone,
                titleRes = R.string.new_nodes,
                subtitleRes = R.string.notifications_for_newly_discovered_nodes,
            ),
            FeatureUIData(
                icon = Icons.Outlined.BatteryAlert,
                titleRes = R.string.low_battery,
                subtitleRes = R.string.notifications_for_low_battery_alerts,
            ),
        )
    }

    PermissionScreenLayout(
        headlineRes = R.string.app_notifications,
        annotatedDescription = annotatedString,
        features = features,
        additionalContent = {
            Text(
                text = stringResource(R.string.critical_alerts),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(16.dp))
            FeatureRow(
                feature =
                FeatureUIData(icon = Icons.Filled.Notifications, subtitleRes = R.string.critical_alerts_description),
            )
        },
        onSkip = onSkip,
        onConfigure = onConfigure,
        configureButtonTextRes = if (showNextButton) R.string.next else R.string.configure_notification_permissions,
        onAnnotationClick = {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.fromParts("package", context.packageName, null)
            context.startActivity(intent)
        },
    )
}

@Composable
fun CriticalAlertsScreen(onSkip: () -> Unit, onConfigure: () -> Unit) {
    Scaffold(
        bottomBar = {
            IntroBottomBar(
                onSkip = onSkip,
                onConfigure = onConfigure,
                configureButtonText = stringResource(id = R.string.configure_critical_alerts),
                skipButtonText = stringResource(id = R.string.skip),
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
            Modifier.fillMaxSize().padding(innerPadding).padding(16.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.critical_alerts),
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.critical_alerts_dnd_request_text),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun LocationScreen(showNextButton: Boolean, onSkip: () -> Unit, onConfigure: () -> Unit) {
    val context = LocalContext.current
    val annotatedString =
        context.createClickableAnnotatedString(
            fullTextRes = R.string.phone_location_description,
            linkTextRes = R.string.settings,
            tag = SETTINGS_TAG,
        )

    val features = remember {
        listOf(
            FeatureUIData(
                icon = Icons.Outlined.LocationOn,
                titleRes = R.string.share_location,
                subtitleRes = R.string.share_location_description,
            ),
            FeatureUIData(
                icon = Icons.Outlined.Router,
                titleRes = R.string.distance_measurements,
                subtitleRes = R.string.distance_measurements_description,
            ),
            FeatureUIData(
                icon = Icons.Outlined.Router,
                titleRes = R.string.distance_filters,
                subtitleRes = R.string.distance_filters_description,
            ),
            FeatureUIData(
                icon = Icons.Outlined.LocationOn,
                titleRes = R.string.mesh_map_location,
                subtitleRes = R.string.mesh_map_location_description,
            ),
        )
    }

    PermissionScreenLayout(
        headlineRes = R.string.phone_location,
        annotatedDescription = annotatedString,
        features = features,
        onSkip = onSkip,
        onConfigure = onConfigure,
        configureButtonTextRes = if (showNextButton) R.string.next else R.string.configure_location_permissions,
        onAnnotationClick = {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.fromParts("package", context.packageName, null)
            context.startActivity(intent)
        },
    )
}

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
private fun PermissionScreenLayout(
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
                configureButtonText = stringResource(id = configureButtonTextRes),
                skipButtonText = stringResource(id = R.string.skip),
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

/**
 * A common bottom bar used across app introduction screens. Provides consistent "Skip" and "Configure" (or "Next")
 * buttons.
 *
 * @param onSkip Callback for the skip action.
 * @param onConfigure Callback for the main configure/next action.
 * @param skipButtonText Text for the skip button.
 * @param configureButtonText Text for the configure/next button.
 * @param showSkipButton Whether to display the skip button. Defaults to true.
 */
@Composable
private fun IntroBottomBar(
    onSkip: () -> Unit,
    onConfigure: () -> Unit,
    skipButtonText: String,
    configureButtonText: String,
    showSkipButton: Boolean = true,
) {
    Surface(shadowElevation = 8.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (showSkipButton) Arrangement.SpaceBetween else Arrangement.End,
        ) {
            if (showSkipButton) {
                Button(onClick = onSkip) { Text(skipButtonText) }
            }
            Button(onClick = onConfigure) { Text(configureButtonText) }
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
private fun Context.createClickableAnnotatedString(
    @StringRes fullTextRes: Int,
    @StringRes linkTextRes: Int,
    tag: String,
): AnnotatedString {
    val fullText = stringResource(id = fullTextRes)
    val linkText = stringResource(id = linkTextRes)
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

/**
 * Displays a row for a feature, including an icon, an optional title, and a subtitle.
 *
 * @param feature The [FeatureUIData] containing information for the row.
 */
@Composable
private fun FeatureRow(feature: FeatureUIData) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(
            imageVector = feature.icon,
            contentDescription =
            feature.titleRes?.let { stringResource(id = it) } ?: stringResource(id = feature.subtitleRes),
            modifier = Modifier.padding(end = 16.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Column {
            feature.titleRes?.let { titleRes ->
                Text(
                    text = stringResource(id = titleRes),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                )
            }
            Text(
                text = stringResource(id = feature.subtitleRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
