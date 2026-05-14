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
@file:Suppress("LongMethod", "ModifierMissing", "ParameterNaming")

package org.meshtastic.feature.settings.radio.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.meshtastic.core.common.BuildConfigProvider
import org.meshtastic.core.model.getColorFrom
import org.meshtastic.core.model.getStringResFrom
import org.meshtastic.core.repository.CommandSender
import org.meshtastic.core.repository.TakPrefs
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.back
import org.meshtastic.core.resources.export_tak_data_package
import org.meshtastic.core.resources.tak
import org.meshtastic.core.resources.tak_config
import org.meshtastic.core.resources.tak_role
import org.meshtastic.core.resources.tak_server
import org.meshtastic.core.resources.tak_server_enabled
import org.meshtastic.core.resources.tak_server_enabled_desc
import org.meshtastic.core.resources.tak_server_export_data_package_desc
import org.meshtastic.core.resources.tak_server_loading
import org.meshtastic.core.resources.tak_server_section
import org.meshtastic.core.resources.tak_server_test_card_title
import org.meshtastic.core.resources.tak_server_test_idle
import org.meshtastic.core.resources.tak_server_test_result_bytes
import org.meshtastic.core.resources.tak_server_test_result_unknown_error
import org.meshtastic.core.resources.tak_server_test_results
import org.meshtastic.core.resources.tak_server_test_run
import org.meshtastic.core.resources.tak_server_test_running
import org.meshtastic.core.resources.tak_team
import org.meshtastic.core.takserver.TAKDataPackageGenerator
import org.meshtastic.core.takserver.TakMeshTestRunner
import org.meshtastic.core.takserver.TakTestResult
import org.meshtastic.core.ui.component.DropDownPreference
import org.meshtastic.core.ui.component.SwitchPreference
import org.meshtastic.core.ui.component.TitledCard
import org.meshtastic.core.ui.icon.ArrowBack
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.PlayArrow
import org.meshtastic.core.ui.icon.Share
import org.meshtastic.feature.settings.radio.RadioConfigViewModel
import org.meshtastic.feature.settings.radio.ResponseState
import org.meshtastic.feature.settings.tak.TakPermissionHandler
import org.meshtastic.feature.settings.tak.rememberDataPackageExporter
import org.meshtastic.proto.MemberRole
import org.meshtastic.proto.ModuleConfig
import org.meshtastic.proto.Team

// ── TAK Config Screen (Module Settings) ─────────────────────────────────────
// Shows only the firmware module config: team and role dropdowns.

@Composable
fun TAKConfigScreen(viewModel: RadioConfigViewModel, onBack: () -> Unit) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()
    val takConfig = state.moduleConfig.tak ?: ModuleConfig.TAKConfig()
    val formState = rememberConfigState(initialValue = takConfig)

    LaunchedEffect(takConfig) { formState.value = takConfig }

    val effectiveResponseState =
        when (state.responseState) {
            is ResponseState.Loading -> ResponseState.Empty
            else -> state.responseState
        }

    RadioConfigScreenList(
        title = stringResource(Res.string.tak),
        onBack = onBack,
        configState = formState,
        enabled = state.connected,
        responseState = effectiveResponseState,
        onDismissPacketResponse = viewModel::clearPacketResponse,
        onSave = {
            val config = ModuleConfig(tak = it)
            viewModel.setModuleConfig(config)
        },
    ) {
        item {
            TakConfigCard(
                team = formState.value.team,
                role = formState.value.role,
                enabled = state.connected,
                onTeamSelected = { formState.value = formState.value.copy(team = it) },
                onRoleSelected = { formState.value = formState.value.copy(role = it) },
            )
        }
    }
}

/** Stateless TAK team/role config card — previewable without DI. */
@Composable
internal fun TakConfigCard(
    team: Team,
    role: MemberRole,
    enabled: Boolean,
    onTeamSelected: (Team) -> Unit,
    onRoleSelected: (MemberRole) -> Unit,
) {
    TitledCard(title = stringResource(Res.string.tak_config)) {
        DropDownPreference(
            title = stringResource(Res.string.tak_team),
            enabled = enabled,
            selectedItem = team,
            itemLabel = { stringResource(getStringResFrom(it)) },
            itemColor = { Color(getColorFrom(it)) },
            onItemSelected = onTeamSelected,
        )
        HorizontalDivider()
        DropDownPreference(
            title = stringResource(Res.string.tak_role),
            enabled = enabled,
            selectedItem = role,
            itemLabel = { stringResource(getStringResFrom(it)) },
            onItemSelected = onRoleSelected,
        )
    }
}

// ── TAK Server Screen (Settings → Advanced) ─────────────────────────────────
// App-local TAK server controls: enable/disable, export data package, debug test harness.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TakServerScreen(onBack: () -> Unit) {
    val takPrefs: TakPrefs = koinInject()
    val isTakServerEnabled by takPrefs.isTakServerEnabled.collectAsStateWithLifecycle()
    val exportLauncher = rememberDataPackageExporter { TAKDataPackageGenerator.generateDataPackage() }

    TakPermissionHandler(
        isTakServerEnabled = isTakServerEnabled,
        onPermissionResult = { granted ->
            if (!granted && isTakServerEnabled) {
                takPrefs.setTakServerEnabled(false)
            }
        },
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.tak_server)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = MeshtasticIcons.ArrowBack,
                            contentDescription = stringResource(Res.string.back),
                        )
                    }
                },
                actions = {
                    if (isTakServerEnabled) {
                        IconButton(onClick = { exportLauncher("Meshtastic_TAK_Server.zip") }) {
                            Icon(
                                imageVector = MeshtasticIcons.Share,
                                contentDescription = stringResource(Res.string.export_tak_data_package),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(horizontal = 16.dp)) {
            TakServerSection(
                isTakServerEnabled = isTakServerEnabled,
                onEnabledChange = { takPrefs.setTakServerEnabled(it) },
                onExport = { exportLauncher("Meshtastic_TAK_Server.zip") },
            )
            TakMeshTestCard()
        }
    }
}

/** Stateless TAK server enable/disable section — previewable without DI. */
@Composable
internal fun TakServerSection(isTakServerEnabled: Boolean, onEnabledChange: (Boolean) -> Unit, onExport: () -> Unit) {
    TitledCard(title = stringResource(Res.string.tak_server_section)) {
        SwitchPreference(
            title = stringResource(Res.string.tak_server_enabled),
            summary = stringResource(Res.string.tak_server_enabled_desc),
            checked = isTakServerEnabled,
            enabled = true,
            onCheckedChange = onEnabledChange,
        )
        if (isTakServerEnabled) {
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Res.string.export_tak_data_package),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(Res.string.tak_server_export_data_package_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onExport) {
                    Icon(
                        imageVector = MeshtasticIcons.Share,
                        contentDescription = stringResource(Res.string.export_tak_data_package),
                    )
                }
            }
        }
    }
}

// ── Debug-only TAK Mesh Test Card ────────────────────────────────────────────

@Composable
private fun TakMeshTestCard() {
    val buildConfig: BuildConfigProvider = koinInject()
    if (!buildConfig.isDebug) return

    val commandSender: CommandSender = koinInject()
    val testRunner = remember { TakMeshTestRunner(commandSender) }
    val results by testRunner.results.collectAsStateWithLifecycle()
    val isRunning by testRunner.isRunning.collectAsStateWithLifecycle()
    val currentFixture by testRunner.currentFixture.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    TakMeshTestCardContent(
        results = results,
        isRunning = isRunning,
        currentFixture = currentFixture,
        fixtureCount = TakMeshTestRunner.FIXTURE_NAMES.size,
        onRunTests = { scope.launch { testRunner.runAll() } },
    )
}

/** Stateless TAK test results card — previewable without DI. */
@Composable
internal fun TakMeshTestCardContent(
    results: List<TakTestResult>,
    isRunning: Boolean,
    currentFixture: String?,
    fixtureCount: Int,
    onRunTests: () -> Unit,
) {
    val loadingLabel = stringResource(Res.string.tak_server_loading)
    val passed = results.count { it.passed }
    val failed = results.count { !it.passed }

    TitledCard(title = stringResource(Res.string.tak_server_test_card_title)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text =
                    if (isRunning) {
                        stringResource(Res.string.tak_server_test_running, currentFixture ?: loadingLabel)
                    } else {
                        stringResource(Res.string.tak_server_test_idle, fixtureCount)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (results.isNotEmpty()) {
                    Text(
                        text =
                        stringResource(
                            Res.string.tak_server_test_results,
                            passed,
                            failed,
                            results.size,
                            fixtureCount,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color =
                        if (failed > 0) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            if (isRunning) {
                CircularProgressIndicator()
            } else {
                Button(onClick = onRunTests) {
                    Icon(imageVector = MeshtasticIcons.PlayArrow, contentDescription = null)
                    Text(stringResource(Res.string.tak_server_test_run))
                }
            }
        }

        // Results list
        for (result in results) {
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = result.fixtureName.removeSuffix(".xml"),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text =
                    if (result.passed) {
                        stringResource(Res.string.tak_server_test_result_bytes, result.compressedBytes)
                    } else {
                        result.error ?: stringResource(Res.string.tak_server_test_result_unknown_error)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (result.passed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
