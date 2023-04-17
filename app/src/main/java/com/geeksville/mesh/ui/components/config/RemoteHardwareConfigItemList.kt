package com.geeksville.mesh.ui.components.config

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Divider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.tooling.preview.Preview
import com.geeksville.mesh.ModuleConfigProtos.ModuleConfig.RemoteHardwareConfig
import com.geeksville.mesh.copy
import com.geeksville.mesh.ui.components.PreferenceCategory
import com.geeksville.mesh.ui.components.PreferenceFooter
import com.geeksville.mesh.ui.components.SwitchPreference

@Composable
fun RemoteHardwareConfigItemList(
    remoteHardwareConfig: RemoteHardwareConfig,
    enabled: Boolean,
    focusManager: FocusManager,
    onSaveClicked: (RemoteHardwareConfig) -> Unit,
) {
    var remoteHardwareInput by remember(remoteHardwareConfig) { mutableStateOf(remoteHardwareConfig) }

    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        item { PreferenceCategory(text = "Remote Hardware Config") }

        item {
            SwitchPreference(title = "Remote Hardware enabled",
                checked = remoteHardwareInput.enabled,
                enabled = enabled,
                onCheckedChange = {
                    remoteHardwareInput = remoteHardwareInput.copy { this.enabled = it }
                })
        }
        item { Divider() }

        item {
            PreferenceFooter(
                enabled = remoteHardwareInput != remoteHardwareConfig,
                onCancelClicked = {
                    focusManager.clearFocus()
                    remoteHardwareInput = remoteHardwareConfig
                },
                onSaveClicked = { onSaveClicked(remoteHardwareInput) }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun RemoteHardwareConfigPreview(){
    RemoteHardwareConfigItemList(
        remoteHardwareConfig = RemoteHardwareConfig.getDefaultInstance(),
        enabled = true,
        focusManager = LocalFocusManager.current,
        onSaveClicked = { },
    )
}
