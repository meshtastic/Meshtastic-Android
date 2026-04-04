/*
 * Copyright (c) 2026 Chris7X (contributor) | Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.meshtastic.feature.ai.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.meshtastic.core.model.Message

/**
 * Small UI component to trigger AI summarization from a chat list.
 * Part of the premium v1.27 'WOW' feature set.
 */
@Composable
fun AiSummarizationComponent(
    messages: List<Message>,
    onSummarize: (List<Message>) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Unread Messages",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "You have ${messages.size} unread messages. Would you like a quick AI summary?",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Button(
                onClick = { onSummarize(messages) },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Summarize with AI")
            }
        }
    }
}
