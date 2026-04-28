/*
 * Copyright (c) 2026 Meshtastic LLC
 */
package org.meshtastic.feature.settings.email

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.*
import org.meshtastic.core.ui.component.MainAppBar

@Composable
fun EmailSettingsScreen(viewModel: EmailQueueViewModel, onBack: () -> Unit) {
    val enabled by viewModel.emailEnabled.collectAsStateWithLifecycle()
    val host by viewModel.smtpHost.collectAsStateWithLifecycle()
    val port by viewModel.smtpPort.collectAsStateWithLifecycle()
    val user by viewModel.smtpUser.collectAsStateWithLifecycle()
    val pass by viewModel.smtpPassword.collectAsStateWithLifecycle()
    val auth by viewModel.smtpAuth.collectAsStateWithLifecycle()
    val startTls by viewModel.smtpStartTls.collectAsStateWithLifecycle()

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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(Res.string.email_enabled))
                Switch(checked = enabled, onCheckedChange = { viewModel.setEmailEnabled(it) })
            }

            OutlinedTextField(
                value = host,
                onValueChange = { viewModel.setSmtpHost(it) },
                label = { Text(stringResource(Res.string.email_smtp_host)) },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = port.toString(),
                onValueChange = { viewModel.setSmtpPort(it.toIntOrNull() ?: 587) },
                label = { Text(stringResource(Res.string.email_smtp_port)) },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = user,
                onValueChange = { viewModel.setSmtpUser(it) },
                label = { Text(stringResource(Res.string.email_smtp_user)) },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = pass,
                onValueChange = { viewModel.setSmtpPassword(it) },
                label = { Text(stringResource(Res.string.email_smtp_password)) },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation()
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(Res.string.email_smtp_auth))
                Switch(checked = auth, onCheckedChange = { viewModel.setSmtpAuth(it) })
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(Res.string.email_smtp_starttls))
                Switch(checked = startTls, onCheckedChange = { viewModel.setSmtpStartTls(it) })
            }
            
            Button(onClick = { viewModel.testEmail() }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.email_test_button))
            }
        }
    }
}
