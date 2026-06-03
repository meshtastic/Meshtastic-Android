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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.get_started
import org.meshtastic.core.resources.permission_bluetooth_summary
import org.meshtastic.core.resources.permission_bluetooth_title
import org.meshtastic.core.resources.permission_grant
import org.meshtastic.core.resources.permission_granted
import org.meshtastic.core.resources.permission_location_summary
import org.meshtastic.core.resources.permission_location_title
import org.meshtastic.core.resources.permission_notifications_summary
import org.meshtastic.core.resources.permission_notifications_title
import org.meshtastic.core.resources.permissions_subtitle
import org.meshtastic.core.resources.permissions_title
import org.meshtastic.core.ui.icon.Bluetooth
import org.meshtastic.core.ui.icon.LocationOn
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Notifications
import org.meshtastic.core.ui.theme.AppTheme

/** A single row describing one runtime permission the app requests. */
private data class PermissionRowData(
    val icon: ImageVector,
    val titleRes: StringResource,
    val summaryRes: StringResource,
    val state: IntroPermissionState,
)

/**
 * The sole onboarding screen. It lists every runtime permission the app needs, lets the user grant each one inline, and
 * provides a button to continue into the app. There is no marketing introduction.
 *
 * @param permissions The aggregated permission states for this platform.
 * @param onContinue Callback invoked when the user finishes the permissions step.
 */
@Composable
internal fun PermissionsScreen(permissions: IntroPermissions, onContinue: () -> Unit) {
    val rows =
        buildList {
            add(
                PermissionRowData(
                    icon = MeshtasticIcons.Bluetooth,
                    titleRes = Res.string.permission_bluetooth_title,
                    summaryRes = Res.string.permission_bluetooth_summary,
                    state = permissions.bluetooth,
                ),
            )
            add(
                PermissionRowData(
                    icon = MeshtasticIcons.LocationOn,
                    titleRes = Res.string.permission_location_title,
                    summaryRes = Res.string.permission_location_summary,
                    state = permissions.location,
                ),
            )
            permissions.notification?.let { notification ->
                add(
                    PermissionRowData(
                        icon = MeshtasticIcons.Notifications,
                        titleRes = Res.string.permission_notifications_title,
                        summaryRes = Res.string.permission_notifications_summary,
                        state = notification,
                    ),
                )
            }
        }

    Scaffold(
        bottomBar = {
            IntroBottomBar(
                onSkip = {},
                onConfigure = onContinue,
                skipButtonText = "",
                configureButtonText = stringResource(Res.string.get_started),
                showSkipButton = false,
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
            Modifier.fillMaxSize().padding(innerPadding).padding(24.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(Res.string.permissions_title),
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(Res.string.permissions_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(32.dp))
            rows.forEach { row ->
                PermissionRow(row)
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun PermissionRow(data: PermissionRowData) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(
            imageVector = data.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 16.dp).size(28.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(data.titleRes),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            )
            Text(
                text = stringResource(data.summaryRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.padding(horizontal = 8.dp))
        if (data.state.isGranted) {
            Text(
                text = stringResource(Res.string.permission_granted),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Button(onClick = { data.state.launchRequest() }) { Text(stringResource(Res.string.permission_grant)) }
        }
    }
}

@Suppress("PreviewPublic")
@PreviewLightDark
@Composable
fun PermissionsScreenPreview() {
    val previewPermissions =
        object : IntroPermissions {
            private fun state(granted: Boolean) =
                object : IntroPermissionState {
                    override val isGranted = granted

                    override fun launchRequest() = Unit
                }

            override val bluetooth = state(false)
            override val location = state(true)
            override val notification = state(false)
        }
    AppTheme { Surface { PermissionsScreen(permissions = previewPermissions, onContinue = {}) } }
}
