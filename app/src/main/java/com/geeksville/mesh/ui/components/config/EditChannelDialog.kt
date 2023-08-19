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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.geeksville.mesh.ChannelProtos
import com.geeksville.mesh.R
import com.geeksville.mesh.channelSettings
import com.geeksville.mesh.model.Channel
import com.geeksville.mesh.ui.components.EditTextPreference
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
    val base64Flags = Base64.URL_SAFE + Base64.NO_WRAP
    fun encodeToString(input: ByteString) =
        Base64.encodeToString(input.toByteArray() ?: ByteArray(0), base64Flags)

    var pskInput by remember { mutableStateOf(channelSettings.psk) }
    var pskString by remember(pskInput) { mutableStateOf(encodeToString(pskInput)) }
    val pskError = pskString != encodeToString(pskInput)

    var nameInput by remember { mutableStateOf(channelSettings.name) }
    var uplinkInput by remember { mutableStateOf(channelSettings.uplinkEnabled) }
    var downlinkInput by remember { mutableStateOf(channelSettings.downlinkEnabled) }

    fun getRandomKey() {
        val random = SecureRandom()
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        pskInput = ByteString.copyFrom(bytes)
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        text = {
            AppCompatTheme {
                Column(modifier.fillMaxWidth()) {
                    var isFocused by remember { mutableStateOf(false) }
                    EditTextPreference(
                        title = stringResource(R.string.channel_name),
                        value = if (isFocused) nameInput else nameInput.ifEmpty { modemPresetName },
                        maxSize = 11, // name max_size:12
                        enabled = true,
                        isError = false,
                        keyboardOptions = KeyboardOptions.Default.copy(
                            keyboardType = KeyboardType.Text, imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { }),
                        onValueChanged = { nameInput = it },
                        onFocusChanged = { isFocused = it.isFocused },
                    )

                    OutlinedTextField(
                        value = pskString,
                        onValueChange = {
                            try {
                                pskString = it // empty (no crypto), 128 or 256 bit only
                                val decoded = Base64.decode(it, base64Flags).toByteString()
                                if (decoded.size() in setOf(0, 16, 32)) pskInput = decoded
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
                                        pskInput = channelSettings.psk
                                        pskString = encodeToString(pskInput)
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
                        })

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Uplink enabled", // TODO move to resource strings
                            modifier = modifier.weight(1f)
                        )
                        Switch(
                            checked = uplinkInput,
                            onCheckedChange = { uplinkInput = it },
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Downlink enabled", // TODO move to resource strings
                            modifier = modifier.weight(1f)
                        )
                        Switch(
                            checked = downlinkInput,
                            onCheckedChange = { downlinkInput = it },
                        )
                    }
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
                        onAddClick(channelSettings {
                            psk = pskInput
                            name = nameInput.trim()
                            uplinkEnabled = uplinkInput
                            downlinkEnabled = downlinkInput
                        })
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
