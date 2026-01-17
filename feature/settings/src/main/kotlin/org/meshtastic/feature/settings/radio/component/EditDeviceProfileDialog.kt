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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.cancel
import org.meshtastic.core.strings.channel_url
import org.meshtastic.core.strings.fixed_position
import org.meshtastic.core.strings.long_name
import org.meshtastic.core.strings.module_settings
import org.meshtastic.core.strings.radio_configuration
import org.meshtastic.core.strings.save
import org.meshtastic.core.strings.short_name
import org.meshtastic.core.ui.component.SwitchPreference
import org.meshtastic.proto.DeviceProfile

private enum class ProfileField(val tag: Int, val labelRes: StringResource) {
    LONG_NAME(1, Res.string.long_name),
    SHORT_NAME(2, Res.string.short_name),
    CHANNEL_URL(3, Res.string.channel_url),
    CONFIG(4, Res.string.radio_configuration),
    MODULE_CONFIG(5, Res.string.module_settings),
    FIXED_POSITION(6, Res.string.fixed_position),
}

@Suppress("LongMethod")
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditDeviceProfileDialog(
    title: String,
    deviceProfile: DeviceProfile,
    onConfirm: (DeviceProfile) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = remember {
        mutableStateMapOf<ProfileField, Boolean>().apply {
            putAll(
                ProfileField.entries.associateWith { field ->
                    when (field) {
                        ProfileField.LONG_NAME -> deviceProfile.long_name != null
                        ProfileField.SHORT_NAME -> deviceProfile.short_name != null
                        ProfileField.CHANNEL_URL -> deviceProfile.channel_url != null
                        ProfileField.CONFIG -> deviceProfile.config != null
                        ProfileField.MODULE_CONFIG -> deviceProfile.module_config != null
                        ProfileField.FIXED_POSITION -> deviceProfile.fixed_position != null
                    }
                },
            )
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        text = {
            Column(modifier.fillMaxWidth()) {
                Text(
                    text = title,
                    style =
                    MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                )
                HorizontalDivider()
                ProfileField.entries.forEach { field ->
                    val isAvailable =
                        when (field) {
                            ProfileField.LONG_NAME -> deviceProfile.long_name != null
                            ProfileField.SHORT_NAME -> deviceProfile.short_name != null
                            ProfileField.CHANNEL_URL -> deviceProfile.channel_url != null
                            ProfileField.CONFIG -> deviceProfile.config != null
                            ProfileField.MODULE_CONFIG -> deviceProfile.module_config != null
                            ProfileField.FIXED_POSITION -> deviceProfile.fixed_position != null
                        }
                    SwitchPreference(
                        title = stringResource(field.labelRes),
                        checked = state[field] == true,
                        enabled = isAvailable,
                        onCheckedChange = { state[field] = it },
                        padding = PaddingValues(0.dp),
                    )
                }
                HorizontalDivider()
            }
        },
        confirmButton = {
            FlowRow(
                modifier = modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(modifier = modifier.weight(1f), onClick = onDismiss) {
                    Text(stringResource(Res.string.cancel))
                }
                Button(
                    modifier = modifier.weight(1f),
                    onClick = {
                        val result =
                            DeviceProfile(
                                long_name =
                                if (state[ProfileField.LONG_NAME] == true) deviceProfile.long_name else null,
                                short_name =
                                if (state[ProfileField.SHORT_NAME] == true) deviceProfile.short_name else null,
                                channel_url =
                                if (state[ProfileField.CHANNEL_URL] == true) deviceProfile.channel_url else null,
                                config = if (state[ProfileField.CONFIG] == true) deviceProfile.config else null,
                                module_config =
                                if (state[ProfileField.MODULE_CONFIG] == true) {
                                    deviceProfile.module_config
                                } else {
                                    null
                                },
                                fixed_position =
                                if (state[ProfileField.FIXED_POSITION] == true) {
                                    deviceProfile.fixed_position
                                } else {
                                    null
                                },
                            )
                        onConfirm(result)
                    },
                    enabled = state.values.any { it },
                ) {
                    Text(stringResource(Res.string.save))
                }
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun EditDeviceProfileDialogPreview() {
    EditDeviceProfileDialog(
        title = "Export configuration",
        deviceProfile = DeviceProfile(),
        onConfirm = {},
        onDismiss = {},
    )
}
