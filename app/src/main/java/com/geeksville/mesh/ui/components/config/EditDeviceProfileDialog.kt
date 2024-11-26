/*
 * Copyright (c) 2024 Meshtastic LLC
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

package com.geeksville.mesh.ui.components.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.geeksville.mesh.ClientOnlyProtos.DeviceProfile
import com.geeksville.mesh.R
import com.geeksville.mesh.ui.components.SwitchPreference
import com.google.protobuf.Descriptors

private const val SupportedFields = 7

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
        val fields = deviceProfile.descriptorForType.fields
            .filter { it.number < SupportedFields } // TODO add ringtone & canned messages
        mutableStateMapOf<Descriptors.FieldDescriptor, Boolean>()
            .apply { putAll(fields.associateWith(deviceProfile::hasField)) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        backgroundColor = MaterialTheme.colors.background,
        text = {
            Column(modifier.fillMaxWidth()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.h6.copy(
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                )
                Divider()
                state.keys.sortedBy { it.number }.forEach { field ->
                    SwitchPreference(
                        title = field.name,
                        checked = state[field] == true,
                        enabled = deviceProfile.hasField(field),
                        onCheckedChange = { state[field] = it },
                        padding = PaddingValues(0.dp)
                    )
                }
                Divider()
            }
        },
        buttons = {
            FlowRow(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    modifier = modifier.weight(1f),
                    onClick = onDismiss
                ) { Text(stringResource(R.string.cancel)) }
                Button(
                    modifier = modifier.weight(1f),
                    onClick = {
                        val builder = DeviceProfile.newBuilder()
                        deviceProfile.allFields.forEach { (field, value) ->
                            if (state[field] == true) {
                                builder.setField(field, value)
                            }
                        }
                        onConfirm(builder.build())
                    },
                    enabled = state.values.any { it },
                ) { Text(stringResource(R.string.save)) }
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
private fun EditDeviceProfileDialogPreview() {
    EditDeviceProfileDialog(
        title = "Export configuration",
        deviceProfile = DeviceProfile.getDefaultInstance(),
        onConfirm = {},
        onDismiss = {},
    )
}
