package com.geeksville.mesh.ui.components.config

import android.util.Base64
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Close
import androidx.compose.material.icons.twotone.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import com.geeksville.mesh.ui.components.EditTextPreference
import com.geeksville.mesh.ui.components.PositionPrecisionPreference
import com.google.accompanist.themeadapter.appcompat.AppCompatTheme
import com.google.protobuf.ByteString
import com.google.protobuf.kotlin.toByteString
import java.security.SecureRandom

@Composable
fun EditChannelDialog(
    channelSettings: ChannelProtos.ChannelSettings,
    onAddClick: (ChannelProtos.ChannelSettings) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    modemPresetName: String = "Default",
) {
    fun encodeToString(input: ByteString) =
        Base64.encodeToString(input.toByteArray() ?: ByteArray(0), Base64.DEFAULT)

    var channelInput by remember(channelSettings) { mutableStateOf(channelSettings) }
    var pskString by remember(channelInput) { mutableStateOf(encodeToString(channelInput.psk)) }
    val pskError = pskString != encodeToString(channelInput.psk)

    fun getRandomKey() {
        val random = SecureRandom()
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        channelInput = channelInput.copy { psk = ByteString.copyFrom(bytes) }
    }

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
                            channelInput = channelInput.copy { name = it }
                            if (channelInput.psk == Channel.default.settings.psk) getRandomKey()
                        },
                        onFocusChanged = { isFocused = it.isFocused },
                    )

                    OutlinedTextField(
                        value = pskString,
                        onValueChange = {
                            try {
                                pskString = it // empty (no crypto), 128 or 256 bit only
                                val decoded = Base64.decode(it, Base64.DEFAULT).toByteString()
                                val fullPsk = Channel(channelSettings { psk = decoded }).psk
                                if (fullPsk.size() in setOf(0, 16, 32)) {
                                    channelInput = channelInput.copy { psk = decoded }
                                }
                            } catch (ex: Throwable) {
                                // Base64 decode failed, pskError true
                            }
                        },
                        modifier = modifier.fillMaxWidth(),
                        enabled = true,
                        label = { Text("PSK") },
                        isError = pskError,
                        keyboardOptions = KeyboardOptions.Default.copy(
                            keyboardType = KeyboardType.Password, imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { }),
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    if (pskError) {
                                        channelInput = channelInput.copy { psk = channelSettings.psk }
                                        pskString = encodeToString(channelInput.psk)
                                    } else getRandomKey()
                                }
                            ) {
                                Icon(
                                    if (pskError) Icons.TwoTone.Close else Icons.TwoTone.Refresh,
                                    contentDescription = stringResource(R.string.reset),
                                    tint = if (pskError) MaterialTheme.colors.error
                                    else LocalContentColor.current.copy(alpha = LocalContentAlpha.current)
                                )
                            }
                        },
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Uplink enabled", // TODO move to resource strings
                            modifier = modifier.weight(1f)
                        )
                        Switch(
                            checked = channelInput.uplinkEnabled,
                            onCheckedChange = {
                                channelInput = channelInput.copy { uplinkEnabled = it }
                            },
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Downlink enabled", // TODO move to resource strings
                            modifier = modifier.weight(1f)
                        )
                        Switch(
                            checked = channelInput.downlinkEnabled,
                            onCheckedChange = {
                                channelInput = channelInput.copy { downlinkEnabled = it }
                            },
                        )
                    }

                    PositionPrecisionPreference(
                        title = "Position",
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
            Row(
                modifier = modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    modifier = modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp)
                        .weight(1f),
                    onClick = onDismissRequest
                ) { Text(stringResource(R.string.cancel)) }
                Button(
                    modifier = modifier
                        .fillMaxWidth()
                        .padding(end = 24.dp)
                        .weight(1f),
                    onClick = {
                        onAddClick(channelInput.copy { name = channelInput.name.trim() })
                    },
                    enabled = !pskError,
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
