package com.geeksville.mesh.ui.components.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.geeksville.mesh.ChannelProtos
import com.geeksville.mesh.R
import com.geeksville.mesh.channelSettings
import com.geeksville.mesh.copy
import com.geeksville.mesh.model.Channel
import com.geeksville.mesh.ui.components.EditBase64Preference
import com.geeksville.mesh.ui.components.EditTextPreference
import com.geeksville.mesh.ui.components.PositionPrecisionPreference
import com.geeksville.mesh.ui.components.SwitchPreference
import com.google.accompanist.themeadapter.appcompat.AppCompatTheme

@Suppress("LongMethod")
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditChannelDialog(
    channelSettings: ChannelProtos.ChannelSettings,
    onAddClick: (ChannelProtos.ChannelSettings) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    modemPresetName: String = "Default",
) {
    var channelInput by remember(channelSettings) { mutableStateOf(channelSettings) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        text = {
            AppCompatTheme {
                Column(modifier.fillMaxWidth()) {
                    val focusManager = LocalFocusManager.current
                    var isFocused by remember { mutableStateOf(false) }
                    EditTextPreference(
                        title = stringResource(R.string.channel_name),
                        value = if (isFocused) channelInput.name else channelInput.name.ifEmpty { modemPresetName },
                        maxSize = 11, // name max_size:12
                        enabled = true,
                        isError = false,
                        keyboardOptions = KeyboardOptions.Default.copy(
                            keyboardType = KeyboardType.Text, imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        onValueChanged = {
                            channelInput = channelInput.copy {
                                name = it
                                if (psk == Channel.default.settings.psk) psk = Channel.getRandomKey()
                            }
                        },
                        onFocusChanged = { isFocused = it.isFocused },
                    )

                    EditBase64Preference(
                        title = "PSK",
                        value = channelInput.psk,
                        enabled = true,
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        onValueChange = {
                            val fullPsk = Channel(channelSettings { psk = it }).psk
                            if (fullPsk.size() in setOf(0, 16, 32)) {
                                channelInput = channelInput.copy { psk = it }
                            }
                        },
                    )

                    SwitchPreference(
                        title = "Uplink enabled",
                        checked = channelInput.uplinkEnabled,
                        enabled = true,
                        onCheckedChange = {
                            channelInput = channelInput.copy { uplinkEnabled = it }
                        },
                        padding = PaddingValues(0.dp)
                    )

                    SwitchPreference(
                        title = "Downlink enabled",
                        checked = channelInput.downlinkEnabled,
                        enabled = true,
                        onCheckedChange = {
                            channelInput = channelInput.copy { downlinkEnabled = it }
                        },
                        padding = PaddingValues(0.dp)
                    )

                    PositionPrecisionPreference(
                        title = "Position enabled",
                        enabled = true,
                        value = channelInput.moduleSettings.positionPrecision,
                        onValueChanged = {
                            val module = channelInput.moduleSettings.copy { positionPrecision = it }
                            channelInput = channelInput.copy { moduleSettings = module }
                        },
                    )
                }
            }
        },
        buttons = {
            FlowRow(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    modifier = modifier
                        .fillMaxWidth()
                        .weight(1f),
                    onClick = onDismissRequest
                ) { Text(stringResource(R.string.cancel)) }
                Button(
                    modifier = modifier
                        .fillMaxWidth()
                        .weight(1f),
                    onClick = {
                        onAddClick(channelInput.copy { name = channelInput.name.trim() })
                    },
                    enabled = true,
                ) { Text(stringResource(R.string.save)) }
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
private fun EditChannelDialogPreview() {
    EditChannelDialog(
        channelSettings = channelSettings {
            psk = Channel.default.settings.psk
            name = Channel.default.name
        },
        onAddClick = { },
        onDismissRequest = { },
    )
}
