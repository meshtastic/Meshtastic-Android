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
package org.meshtastic.app.map.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.app.map.model.CustomTileProviderConfig
import org.meshtastic.app.map.model.isValidTileUrlTemplate
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.add_custom_tile_source
import org.meshtastic.core.resources.add_local_mbtiles_file
import org.meshtastic.core.resources.cancel
import org.meshtastic.core.resources.delete_custom_tile_source
import org.meshtastic.core.resources.edit_custom_tile_source
import org.meshtastic.core.resources.local_mbtiles_file
import org.meshtastic.core.resources.manage_custom_tile_sources
import org.meshtastic.core.resources.name
import org.meshtastic.core.resources.name_cannot_be_empty
import org.meshtastic.core.resources.no_custom_tile_sources_found
import org.meshtastic.core.resources.provider_name_exists
import org.meshtastic.core.resources.save
import org.meshtastic.core.resources.url_cannot_be_empty
import org.meshtastic.core.resources.url_must_contain_placeholders
import org.meshtastic.core.resources.url_template
import org.meshtastic.core.resources.url_template_hint
import org.meshtastic.core.ui.component.MeshtasticDialog
import org.meshtastic.core.ui.icon.Delete
import org.meshtastic.core.ui.icon.Edit
import org.meshtastic.core.ui.icon.MeshtasticIcons

@Suppress("LongMethod", "LongParameterList")
@Composable
internal fun CustomTileProviderManager(
    providers: List<CustomTileProviderConfig>,
    onAdd: (CustomTileProviderConfig) -> Unit,
    onUpdate: (CustomTileProviderConfig) -> Unit,
    onDelete: (String) -> Unit,
    onAddLocalMbTiles: (() -> Unit)? = null,
) {
    var editingConfig by remember { mutableStateOf<CustomTileProviderConfig?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }

    if (showEditDialog) {
        AddEditCustomTileProviderDialog(
            config = editingConfig,
            providers = providers,
            onDismiss = { showEditDialog = false },
            onSave = { config ->
                if (editingConfig == null) onAdd(config) else onUpdate(config)
                showEditDialog = false
            },
        )
    }

    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Text(
                text = stringResource(Res.string.manage_custom_tile_sources),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(16.dp),
            )
            HorizontalDivider()
        }

        if (providers.isEmpty()) {
            item {
                Text(
                    text = stringResource(Res.string.no_custom_tile_sources_found),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            items(providers, key = { it.id }) { config ->
                ListItem(
                    headlineContent = { Text(config.name) },
                    supportingContent = {
                        Text(
                            if (config.isLocal) {
                                stringResource(Res.string.local_mbtiles_file)
                            } else {
                                config.urlTemplate
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    trailingContent = {
                        Row {
                            if (!config.isLocal) {
                                IconButton(
                                    onClick = {
                                        editingConfig = config
                                        showEditDialog = true
                                    },
                                ) {
                                    Icon(
                                        MeshtasticIcons.Edit,
                                        contentDescription = stringResource(Res.string.edit_custom_tile_source),
                                    )
                                }
                            }
                            IconButton(onClick = { onDelete(config.id) }) {
                                Icon(
                                    MeshtasticIcons.Delete,
                                    contentDescription = stringResource(Res.string.delete_custom_tile_source),
                                )
                            }
                        }
                    },
                )
                HorizontalDivider()
            }
        }

        item {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        editingConfig = null
                        showEditDialog = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(Res.string.add_custom_tile_source))
                }
                onAddLocalMbTiles?.let { onAddLocal ->
                    Button(onClick = onAddLocal, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(Res.string.add_local_mbtiles_file))
                    }
                }
            }
        }
    }
}

@Suppress("LongMethod")
@Composable
private fun AddEditCustomTileProviderDialog(
    config: CustomTileProviderConfig?,
    providers: List<CustomTileProviderConfig>,
    onDismiss: () -> Unit,
    onSave: (CustomTileProviderConfig) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(config?.name ?: "") }
    var url by rememberSaveable { mutableStateOf(config?.urlTemplate ?: "") }
    var nameError by remember { mutableStateOf<String?>(null) }
    var urlError by remember { mutableStateOf<String?>(null) }

    val emptyNameError = stringResource(Res.string.name_cannot_be_empty)
    val providerNameExistsError = stringResource(Res.string.provider_name_exists)
    val urlCannotBeEmptyError = stringResource(Res.string.url_cannot_be_empty)
    val urlMustContainPlaceholdersError = stringResource(Res.string.url_must_contain_placeholders)

    fun validateAndSave() {
        nameError = validateName(name, providers, config?.id, emptyNameError, providerNameExistsError)
        urlError = validateUrl(url, urlCannotBeEmptyError, urlMustContainPlaceholdersError)
        if (nameError == null && urlError == null) {
            onSave(
                (config ?: CustomTileProviderConfig(name = name, urlTemplate = url))
                    .copy(name = name, urlTemplate = url)
                    .normalized(),
            )
        }
    }

    MeshtasticDialog(
        onDismiss = onDismiss,
        title =
        stringResource(
            if (config == null) Res.string.add_custom_tile_source else Res.string.edit_custom_tile_source,
        ),
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameError = null
                    },
                    label = { Text(stringResource(Res.string.name)) },
                    isError = nameError != null,
                    supportingText = { nameError?.let { Text(it) } },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        urlError = null
                    },
                    label = { Text(stringResource(Res.string.url_template)) },
                    isError = urlError != null,
                    supportingText = { Text(urlError ?: stringResource(Res.string.url_template_hint)) },
                    singleLine = false,
                    maxLines = 2,
                )
            }
        },
        onConfirm = ::validateAndSave,
        confirmTextRes = Res.string.save,
        dismissTextRes = Res.string.cancel,
    )
}

private fun validateName(
    name: String,
    providers: List<CustomTileProviderConfig>,
    currentId: String?,
    emptyNameError: String,
    nameExistsError: String,
): String? = when {
    name.isBlank() -> emptyNameError
    providers.any { it.id != currentId && it.name.trim().equals(name.trim(), ignoreCase = true) } -> nameExistsError
    else -> null
}

private fun validateUrl(url: String, emptyUrlError: String, missingPlaceholdersError: String): String? = when {
    url.isBlank() -> emptyUrlError
    !url.isValidTileUrlTemplate(requireHttps = false) -> missingPlaceholdersError
    else -> null
}
