package com.geeksville.mesh.ui.components.config

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.Divider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.tooling.preview.Preview
import com.geeksville.mesh.ModuleConfigProtos
import com.geeksville.mesh.copy
import com.geeksville.mesh.ui.components.EditTextPreference
import com.geeksville.mesh.ui.components.PreferenceCategory
import com.geeksville.mesh.ui.components.PreferenceFooter
import com.geeksville.mesh.ui.components.SwitchPreference

@Composable
fun NeighborInfoConfigItemList(
    neighborInfoConfig: ModuleConfigProtos.ModuleConfig.NeighborInfoConfig,
    enabled: Boolean,
    onSaveClicked: (ModuleConfigProtos.ModuleConfig.NeighborInfoConfig) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var neighborInfoInput by remember(neighborInfoConfig) { mutableStateOf(neighborInfoConfig) }

    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        item { PreferenceCategory(text = "Neighbor Info Config") }

        item {
            SwitchPreference(title = "Neighbor Info enabled",
                checked = neighborInfoInput.enabled,
                enabled = enabled,
                onCheckedChange = {
                    neighborInfoInput = neighborInfoInput.copy { this.enabled = it }
                })
        }
        item { Divider() }

        item {
            EditTextPreference(title = "Update interval (seconds)",
                value = neighborInfoInput.updateInterval,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = {
                    neighborInfoInput = neighborInfoInput.copy { updateInterval = it }
                })
        }

        item {
            PreferenceFooter(
                enabled = neighborInfoInput != neighborInfoConfig,
                onCancelClicked = {
                    focusManager.clearFocus()
                    neighborInfoInput = neighborInfoConfig
                },
                onSaveClicked = {
                    focusManager.clearFocus()
                    onSaveClicked(neighborInfoInput)
                }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun NeighborInfoConfigPreview() {
    NeighborInfoConfigItemList(
        neighborInfoConfig = ModuleConfigProtos.ModuleConfig.NeighborInfoConfig.getDefaultInstance(),
        enabled = true,
        onSaveClicked = { },
    )
}
