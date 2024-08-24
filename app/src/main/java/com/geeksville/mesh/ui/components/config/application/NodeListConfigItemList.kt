package com.geeksville.mesh.ui.components.config.application

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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import com.geeksville.mesh.model.NodeListConfig
import com.geeksville.mesh.ui.components.EditTextPreference
import com.geeksville.mesh.ui.components.PreferenceCategory
import com.geeksville.mesh.ui.components.PreferenceFooter

@Composable
fun NodeListConfigItemList(
    nodeConfig: NodeListConfig,
    onSaveClicked: (NodeListConfig) -> Unit,
) {
    val focusManager = LocalFocusManager.current

    var nodeConfigInput by remember { mutableStateOf(nodeConfig) }


    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        item { PreferenceCategory(text = "Node list") }
        item { Divider() }
        item {
            EditTextPreference(title = "Packet counter reset interval (seconds)",
                value = nodeConfigInput.interval.toString(),
                enabled = true,
                isError = false,
                keyboardOptions = KeyboardOptions.Default.copy(
                    keyboardType = KeyboardType.Number, imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { newValue ->
                    val interval = newValue.toIntOrNull()?.takeIf { it >= 0 } ?: 0
                    nodeConfigInput = nodeConfigInput.copy(interval = interval)
                })
        }
        item { Divider() }

        item {
            PreferenceFooter(
                enabled = true,
                onCancelClicked = {
                    focusManager.clearFocus()
                    nodeConfigInput = nodeConfig
                },
                onSaveClicked = {
                    focusManager.clearFocus()
                    onSaveClicked(nodeConfigInput)

                }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun UserConfigPreview() {
    NodeListConfigItemList(
        nodeConfig = NodeListConfig(),
        onSaveClicked = { }
    )
}