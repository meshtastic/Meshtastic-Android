/*
 * Copyright (c) 2026 Meshtastic LLC
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
package org.meshtastic.feature.settings.radio.component

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eygraber.uri.toKmpUri
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.export_keys
import org.meshtastic.core.resources.export_keys_confirmation
import org.meshtastic.core.ui.component.MeshtasticResourceDialog
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Warning
import org.meshtastic.feature.settings.radio.RadioConfigViewModel
import org.meshtastic.proto.Config

@Composable
actual fun ExportSecurityConfigButton(
    viewModel: RadioConfigViewModel,
    enabled: Boolean,
    securityConfig: Config.SecurityConfig,
) {
    val node by viewModel.destNode.collectAsStateWithLifecycle()
    var showEditSecurityConfigDialog by rememberSaveable { mutableStateOf(false) }

    val exportConfigLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == Activity.RESULT_OK) {
                it.data?.data?.let { uri -> viewModel.exportSecurityConfig(uri.toKmpUri(), securityConfig) }
            }
        }

    if (showEditSecurityConfigDialog) {
        MeshtasticResourceDialog(
            titleRes = Res.string.export_keys,
            messageRes = Res.string.export_keys_confirmation,
            onDismiss = { showEditSecurityConfigDialog = false },
            onConfirm = {
                showEditSecurityConfigDialog = false
                val intent =
                    Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "application/*"
                        putExtra(Intent.EXTRA_TITLE, "${node?.user?.short_name}_keys_$nowMillis.json")
                    }
                exportConfigLauncher.launch(intent)
            },
        )
    }

    HorizontalDivider()
    NodeActionButton(
        modifier = Modifier.padding(horizontal = 8.dp),
        title = stringResource(Res.string.export_keys),
        enabled = enabled,
        icon = MeshtasticIcons.Warning,
        onClick = { showEditSecurityConfigDialog = true },
    )
}
