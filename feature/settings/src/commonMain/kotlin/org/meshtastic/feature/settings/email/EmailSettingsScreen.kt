/*
 * Copyright (c) 2026 Meshtastic LLC
 */
package org.meshtastic.feature.settings.email

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.*
import org.meshtastic.core.ui.component.MainAppBar

@Composable
fun EmailSettingsScreen(viewModel: EmailQueueViewModel, onBack: () -> Unit) {
    val enabled by viewModel.emailEnabled.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            MainAppBar(
                title = stringResource(Res.string.email_settings_title),
                canNavigateUp = true,
                onNavigateUp = onBack,
                ourNode = null,
                showNodeChip = false,
                actions = {},
                onClickChip = {},
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(Res.string.email_enabled))
                Switch(checked = enabled, onCheckedChange = { viewModel.setEmailEnabled(it) })
            }
        }
    }
}
