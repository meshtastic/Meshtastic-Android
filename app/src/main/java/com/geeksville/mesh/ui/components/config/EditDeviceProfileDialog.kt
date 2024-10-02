package com.geeksville.mesh.ui.components.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.geeksville.mesh.ClientOnlyProtos
import com.geeksville.mesh.R
import com.geeksville.mesh.deviceProfile
import com.geeksville.mesh.ui.components.SwitchPreference
import com.google.protobuf.Descriptors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditDeviceProfileDialog(
    title: String,
    deviceProfile: ClientOnlyProtos.DeviceProfile,
    onAddClick: (ClientOnlyProtos.DeviceProfile) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = remember {
        val fields = deviceProfile.descriptorForType.fields
        mutableStateMapOf<Descriptors.FieldDescriptor, Boolean>()
            .apply { putAll(fields.associateWith(deviceProfile::hasField)) }
    }

    AlertDialog(
        title = { Text(title) },
        onDismissRequest = onDismissRequest,
        text = {
            Column(modifier.fillMaxWidth()) {
                state.keys.sortedBy { it.number }.forEach { field ->
                    SwitchPreference(
                        title = field.name,
                        checked = state[field] == true,
                        enabled =  deviceProfile.hasField(field),
                        onCheckedChange = { state[field] = it },
                        padding = PaddingValues(0.dp)
                    )
                }
            }
        },
        buttons = {
            FlowRow(
                modifier = modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(
                    modifier = modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .weight(1f),
                    onClick = onDismissRequest
                ) { Text(stringResource(R.string.cancel)) }
                Button(
                    modifier = modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .weight(1f),
                    onClick = {
                        val builder = ClientOnlyProtos.DeviceProfile.newBuilder()
                        deviceProfile.allFields.forEach { (field, value) ->
                            if (state[field] == true) {
                                builder.setField(field, value)
                            }
                        }
                        onAddClick(builder.build())
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
        deviceProfile = deviceProfile { },
        onAddClick = { },
        onDismissRequest = { },
    )
}
