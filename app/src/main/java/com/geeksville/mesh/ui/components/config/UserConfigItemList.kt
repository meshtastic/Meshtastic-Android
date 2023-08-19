package com.geeksville.mesh.ui.components.config

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Divider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import com.geeksville.mesh.MeshProtos
import com.geeksville.mesh.copy
import com.geeksville.mesh.model.getInitials
import com.geeksville.mesh.ui.components.EditTextPreference
import com.geeksville.mesh.ui.components.PreferenceCategory
import com.geeksville.mesh.ui.components.PreferenceFooter
import com.geeksville.mesh.ui.components.RegularPreference
import com.geeksville.mesh.ui.components.SwitchPreference
import com.geeksville.mesh.user

@Composable
fun UserConfigItemList(
    userConfig: MeshProtos.User,
    enabled: Boolean,
    focusManager: FocusManager,
    onSaveClicked: (MeshProtos.User) -> Unit,
) {
    var userInput by remember(userConfig) { mutableStateOf(userConfig) }

    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        item { PreferenceCategory(text = "User Config") }

        item {
            RegularPreference(title = "Node ID",
                subtitle = userInput.id,
                onClick = {})
        }
        item { Divider() }

        item {
            EditTextPreference(title = "Long name",
                value = userInput.longName,
                maxSize = 39, // long_name max_size:40
                enabled = enabled,
                isError = userInput.longName.isEmpty(),
                keyboardOptions = KeyboardOptions.Default.copy(
                    keyboardType = KeyboardType.Text, imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = {
                    userInput = userInput.copy { longName = it }
                    if (getInitials(it).toByteArray().size <= 4) // short_name max_size:5
                        userInput = userInput.copy { shortName = getInitials(it) }
                })
        }

        item {
            EditTextPreference(title = "Short name",
                value = userInput.shortName,
                maxSize = 4, // short_name max_size:5
                enabled = enabled,
                isError = userInput.shortName.isEmpty(),
                keyboardOptions = KeyboardOptions.Default.copy(
                    keyboardType = KeyboardType.Text, imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { userInput = userInput.copy { shortName = it } })
        }

        item {
            RegularPreference(title = "Hardware model",
                subtitle = userInput.hwModel.name,
                onClick = {})
        }
        item { Divider() }

        item {
            SwitchPreference(title = "Licensed amateur radio",
                checked = userInput.isLicensed,
                enabled = enabled,
                onCheckedChange = { userInput = userInput.copy { isLicensed = it } })
        }
        item { Divider() }

        item {
            PreferenceFooter(
                enabled = userInput != userConfig,
                onCancelClicked = {
                    focusManager.clearFocus()
                    userInput = userConfig
                }, onSaveClicked = { onSaveClicked(userInput) }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun UserConfigPreview() {
    UserConfigItemList(
        userConfig = user {
            id = "!a280d9c8"
            longName = "Meshtastic d9c8"
            shortName = "d9c8"
            hwModel = MeshProtos.HardwareModel.RAK4631
            isLicensed = false
        },
        enabled = true,
        focusManager = LocalFocusManager.current,
        onSaveClicked = { },
    )
}
