package com.geeksville.mesh.ui.components.config

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.Divider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.ModuleConfigProtos.ModuleConfig.StoreForwardConfig
import com.geeksville.mesh.copy
import com.geeksville.mesh.model.RadioConfigViewModel
import com.geeksville.mesh.moduleConfig
import com.geeksville.mesh.ui.components.EditTextPreference
import com.geeksville.mesh.ui.components.PreferenceCategory
import com.geeksville.mesh.ui.components.PreferenceFooter
import com.geeksville.mesh.ui.components.SwitchPreference

@Composable
fun StoreForwardConfigScreen(
    viewModel: RadioConfigViewModel = hiltViewModel(),
) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()

    if (state.responseState.isWaiting()) {
        PacketResponseStateDialog(
            state = state.responseState,
            onDismiss = viewModel::clearPacketResponse,
        )
    }

    StoreForwardConfigItemList(
        storeForwardConfig = state.moduleConfig.storeForward,
        enabled = state.connected,
        onSaveClicked = { storeForwardInput ->
            val config = moduleConfig { storeForward = storeForwardInput }
            viewModel.setModuleConfig(config)
        }
    )
}

@Composable
fun StoreForwardConfigItemList(
    storeForwardConfig: StoreForwardConfig,
    enabled: Boolean,
    onSaveClicked: (StoreForwardConfig) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var storeForwardInput by rememberSaveable { mutableStateOf(storeForwardConfig) }

    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        item { PreferenceCategory(text = "Store & Forward Config") }

        item {
            SwitchPreference(title = "Store & Forward enabled",
                checked = storeForwardInput.enabled,
                enabled = enabled,
                onCheckedChange = {
                    storeForwardInput = storeForwardInput.copy { this.enabled = it }
                })
        }
        item { Divider() }

        item {
            SwitchPreference(title = "Heartbeat",
                checked = storeForwardInput.heartbeat,
                enabled = enabled,
                onCheckedChange = { storeForwardInput = storeForwardInput.copy { heartbeat = it } })
        }
        item { Divider() }

        item {
            EditTextPreference(title = "Number of records",
                value = storeForwardInput.records,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { storeForwardInput = storeForwardInput.copy { records = it } })
        }

        item {
            EditTextPreference(title = "History return max",
                value = storeForwardInput.historyReturnMax,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = {
                    storeForwardInput = storeForwardInput.copy { historyReturnMax = it }
                })
        }

        item {
            EditTextPreference(title = "History return window",
                value = storeForwardInput.historyReturnWindow,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = {
                    storeForwardInput = storeForwardInput.copy { historyReturnWindow = it }
                })
        }

        item {
            SwitchPreference(
                title = "Server",
                checked = storeForwardInput.isServer,
                enabled = enabled,
                onCheckedChange = { storeForwardInput = storeForwardInput.copy { isServer = it } })
        }
        item { Divider() }

        item {
            PreferenceFooter(
                enabled = storeForwardInput != storeForwardConfig,
                onCancelClicked = {
                    focusManager.clearFocus()
                    storeForwardInput = storeForwardConfig
                },
                onSaveClicked = {
                    focusManager.clearFocus()
                    onSaveClicked(storeForwardInput)
                }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun StoreForwardConfigPreview() {
    StoreForwardConfigItemList(
        storeForwardConfig = StoreForwardConfig.getDefaultInstance(),
        enabled = true,
        onSaveClicked = { },
    )
}
