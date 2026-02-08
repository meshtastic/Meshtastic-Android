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
package org.meshtastic.feature.map.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.data.model.CustomTileProviderConfig
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.add_custom_tile_source
import org.meshtastic.core.strings.cancel
import org.meshtastic.core.strings.delete_custom_tile_source
import org.meshtastic.core.strings.edit_custom_tile_source
import org.meshtastic.core.strings.manage_custom_tile_sources
import org.meshtastic.core.strings.name
import org.meshtastic.core.strings.name_cannot_be_empty
import org.meshtastic.core.strings.no_custom_tile_sources_found
import org.meshtastic.core.strings.provider_name_exists
import org.meshtastic.core.strings.save
import org.meshtastic.core.strings.url_cannot_be_empty
import org.meshtastic.core.strings.url_must_contain_placeholders
import org.meshtastic.core.strings.url_template
import org.meshtastic.core.strings.url_template_hint
import org.meshtastic.core.ui.component.MeshtasticDialog
import org.meshtastic.core.ui.util.showToast
import org.meshtastic.feature.map.MapViewModel

@Suppress("LongMethod")
@Composable
fun CustomTileProviderManagerSheet(mapViewModel: MapViewModel) {
    val customTileProviders by mapViewModel.customTileProviderConfigs.collectAsStateWithLifecycle()
    var editingConfig by remember { mutableStateOf<CustomTileProviderConfig?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(Unit) { mapViewModel.errorFlow.collectLatest { errorMessage -> context.showToast(errorMessage) } }

    if (showEditDialog) {
        AddEditCustomTileProviderDialog(
            config = editingConfig,
            onDismiss = { showEditDialog = false },
            onSave = { name, url ->
                if (editingConfig == null) { // Adding new
                    mapViewModel.addCustomTileProvider(name, url)
                } else { // Editing existing
                    mapViewModel.updateCustomTileProvider(editingConfig!!.copy(name = name, urlTemplate = url))
                }
                showEditDialog = false
            },
            mapViewModel = mapViewModel,
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

        if (customTileProviders.isEmpty()) {
            item {
                Text(
                    text = stringResource(Res.string.no_custom_tile_sources_found),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            items(customTileProviders, key = { it.id }) { config ->
                ListItem(
                    headlineContent = { Text(config.name) },
                    supportingContent = { Text(config.urlTemplate, style = MaterialTheme.typography.bodySmall) },
                    trailingContent = {
                        Row {
                            IconButton(
                                onClick = {
                                    editingConfig = config
                                    showEditDialog = true
                                },
                            ) {
                                Icon(
                                    Icons.Filled.Edit,
                                    contentDescription = stringResource(Res.string.edit_custom_tile_source),
                                )
                            }
                            IconButton(onClick = { mapViewModel.removeCustomTileProvider(config.id) }) {
                                Icon(
                                    Icons.Filled.Delete,
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
            Button(
                onClick = {
                    editingConfig = null
                    showEditDialog = true
                },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) {
                Text(stringResource(Res.string.add_custom_tile_source))
            }
        }
    }
}

@Suppress("LongMethod")
@Composable
private fun AddEditCustomTileProviderDialog(
    config: CustomTileProviderConfig?,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
    mapViewModel: MapViewModel,
) {
    var name by rememberSaveable { mutableStateOf(config?.name ?: "") }
    var url by rememberSaveable { mutableStateOf(config?.urlTemplate ?: "") }
    var nameError by remember { mutableStateOf<String?>(null) }
    var urlError by remember { mutableStateOf<String?>(null) }
    val customTileProviders by mapViewModel.customTileProviderConfigs.collectAsStateWithLifecycle()

    val emptyNameError = stringResource(Res.string.name_cannot_be_empty)
    val providerNameExistsError = stringResource(Res.string.provider_name_exists)
    val urlCannotBeEmptyError = stringResource(Res.string.url_cannot_be_empty)
    val urlMustContainPlaceholdersError = stringResource(Res.string.url_must_contain_placeholders)

    fun validateAndSave() {
        val currentNameError =
            validateName(name, customTileProviders, config?.id, emptyNameError, providerNameExistsError)
        val currentUrlError = validateUrl(url, urlCannotBeEmptyError, urlMustContainPlaceholdersError)

        nameError = currentNameError
        urlError = currentUrlError

        if (currentNameError == null && currentUrlError == null) {
            onSave(name, url)
        }
    }

    MeshtasticDialog(
        onDismiss = onDismiss,
        title =
        if (config == null) {
            stringResource(Res.string.add_custom_tile_source)
        } else {
            stringResource(Res.string.edit_custom_tile_source)
        },
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
                    supportingText = {
                        if (urlError != null) {
                            Text(urlError!!)
                        } else {
                            Text(stringResource(Res.string.url_template_hint))
                        }
                    },
                    singleLine = false,
                    maxLines = 2,
                )
            }
        },
        onConfirm = { validateAndSave() },
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
): String? = if (name.isBlank()) {
    emptyNameError
} else if (providers.any { it.name.equals(name, ignoreCase = true) && it.id != currentId }) {
    nameExistsError
} else {
    null
}

private fun validateUrl(url: String, emptyUrlError: String, mustContainPlaceholdersError: String): String? =
    if (url.isBlank()) {
        emptyUrlError
    } else if (
        !url.contains("{z}", ignoreCase = true) ||
        !url.contains("{x}", ignoreCase = true) ||
        !url.contains("{y}", ignoreCase = true)
    ) {
        mustContainPlaceholdersError
    } else {
        null
    }
