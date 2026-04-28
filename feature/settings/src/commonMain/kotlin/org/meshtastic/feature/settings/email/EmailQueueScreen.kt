/*
 * Copyright (c) 2026 Meshtastic LLC
 */
package org.meshtastic.feature.settings.email

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.database.entity.EmailQueueEntity
import org.meshtastic.core.resources.*
import org.meshtastic.core.ui.component.MainAppBar
import org.meshtastic.core.ui.icon.Delete
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Send
import org.meshtastic.core.ui.icon.Settings

@Composable
fun EmailQueueScreen(
    viewModel: EmailQueueViewModel,
    onBack: () -> Unit,
    onSendEmail: (EmailQueueEntity) -> Unit,
    onSettings: () -> Unit
) {
    val emails by viewModel.unsentEmails.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            MainAppBar(
                title = stringResource(Res.string.email_queue_title),
                canNavigateUp = true,
                onNavigateUp = onBack,
                ourNode = null,
                showNodeChip = false,
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(MeshtasticIcons.Settings, contentDescription = stringResource(Res.string.settings))
                    }
                },
                onClickChip = {},
            )
        },
    ) { paddingValues ->
        if (emails.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                Text(stringResource(Res.string.email_queue_empty), style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(paddingValues).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(emails, key = { it.id }) { email ->
                    EmailItem(
                        email = email,
                        onSend = { onSendEmail(email); viewModel.markAsSent(email.id) },
                        onDelete = { viewModel.delete(email.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmailItem(email: EmailQueueEntity, onSend: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = stringResource(Res.string.email_to, email.recipient), style = MaterialTheme.typography.titleMedium)
            Text(text = stringResource(Res.string.email_subject, email.subject), style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = email.content, style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onDelete) {
                    Icon(MeshtasticIcons.Delete, contentDescription = stringResource(Res.string.delete))
                }
                Button(onClick = onSend) {
                    Icon(MeshtasticIcons.Send, contentDescription = stringResource(Res.string.email_send))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(Res.string.email_send))
                }
            }
        }
    }
}
