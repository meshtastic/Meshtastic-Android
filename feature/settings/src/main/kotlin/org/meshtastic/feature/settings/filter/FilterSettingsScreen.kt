/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package org.meshtastic.feature.settings.filter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.add
import org.meshtastic.core.strings.delete
import org.meshtastic.core.strings.filter_add_placeholder
import org.meshtastic.core.strings.filter_enable
import org.meshtastic.core.strings.filter_enable_summary
import org.meshtastic.core.strings.filter_no_words
import org.meshtastic.core.strings.filter_regex_pattern
import org.meshtastic.core.strings.filter_settings
import org.meshtastic.core.strings.filter_whole_word
import org.meshtastic.core.strings.filter_words
import org.meshtastic.core.strings.filter_words_summary
import org.meshtastic.core.ui.component.MainAppBar

@Composable
fun FilterSettingsScreen(viewModel: FilterSettingsViewModel = hiltViewModel(), onBack: () -> Unit) {
    val filterEnabled by viewModel.filterEnabled.collectAsState()
    val filterWords by viewModel.filterWords.collectAsState()
    var newWord by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            MainAppBar(
                title = stringResource(Res.string.filter_settings),
                canNavigateUp = true,
                onNavigateUp = onBack,
                ourNode = null,
                showNodeChip = false,
                actions = {},
                onClickChip = {},
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.padding(paddingValues).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { FilterEnableCard(filterEnabled) { viewModel.setFilterEnabled(it) } }
            item {
                FilterWordsInputCard(
                    newWord = newWord,
                    onNewWordChange = { newWord = it },
                    onAddWord = {
                        viewModel.addFilterWord(newWord)
                        newWord = ""
                    },
                )
            }
            if (filterWords.isEmpty()) {
                item {
                    Text(
                        stringResource(Res.string.filter_no_words),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
            items(filterWords, key = { it }) { word ->
                FilterWordItem(word = word, onRemove = { viewModel.removeFilterWord(word) })
            }
        }
    }
}

@Composable
private fun FilterEnableCard(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(Res.string.filter_enable), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(Res.string.filter_enable_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun FilterWordsInputCard(newWord: String, onNewWordChange: (String) -> Unit, onAddWord: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(Res.string.filter_words), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(Res.string.filter_words_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newWord,
                    onValueChange = onNewWordChange,
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(Res.string.filter_add_placeholder)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onAddWord() }),
                )
                IconButton(onClick = onAddWord) {
                    Icon(Icons.Rounded.Add, contentDescription = stringResource(Res.string.add))
                }
            }
        }
    }
}

@Composable
private fun FilterWordItem(word: String, onRemove: () -> Unit) {
    val isRegex = word.startsWith("regex:")
    val displayText = if (isRegex) word.removePrefix("regex:") else word

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = displayText, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text =
                    stringResource(if (isRegex) Res.string.filter_regex_pattern else Res.string.filter_whole_word),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Rounded.Delete, contentDescription = stringResource(Res.string.delete))
            }
        }
    }
}
