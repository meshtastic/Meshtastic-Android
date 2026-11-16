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
package org.meshtastic.feature.map.survey

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.meshtastic.core.common.util.DateFormatter
import org.meshtastic.core.repository.SignalSurveyBlocker
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.cancel
import org.meshtastic.core.resources.delete
import org.meshtastic.core.resources.grant_permission
import org.meshtastic.core.resources.hide_layer
import org.meshtastic.core.resources.not_connected
import org.meshtastic.core.resources.open_settings
import org.meshtastic.core.resources.show_layer
import org.meshtastic.core.resources.signal_survey_delete_confirm
import org.meshtastic.core.resources.signal_survey_description
import org.meshtastic.core.resources.signal_survey_export
import org.meshtastic.core.resources.signal_survey_heatmap
import org.meshtastic.core.resources.signal_survey_location_off
import org.meshtastic.core.resources.signal_survey_location_permission
import org.meshtastic.core.resources.signal_survey_no_fix
import org.meshtastic.core.resources.signal_survey_readings
import org.meshtastic.core.resources.signal_survey_record
import org.meshtastic.core.resources.signal_survey_title
import org.meshtastic.core.ui.component.Quality
import org.meshtastic.core.ui.icon.Delete
import org.meshtastic.core.ui.icon.FileDownload
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Visibility
import org.meshtastic.core.ui.icon.VisibilityOff
import org.meshtastic.core.ui.util.PermissionStatus
import org.meshtastic.core.ui.util.rememberFileExporter
import org.meshtastic.core.ui.util.rememberLocationPermissionState
import org.meshtastic.core.ui.util.rememberOpenLocationSettings

internal const val SIGNAL_SURVEY_SWITCH_TAG = "signal_survey_switch"
internal const val SIGNAL_SURVEY_CONFIRM_DELETE_TAG = "signal_survey_confirm_delete"
internal const val SIGNAL_SURVEY_FIX_TAG = "signal_survey_fix"
internal const val SIGNAL_SURVEY_HEATMAP_TAG = "signal_survey_heatmap"
private const val SURVEY_FILE_NAME = "signal_survey.csv"
private const val CSV_MIME_TYPE = "text/csv"

/** The signal survey controls and session history, for a map's layers sheet. */
@Composable
fun SignalSurveySection(modifier: Modifier = Modifier, viewModel: SignalSurveyViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var exportSessionId by rememberSaveable { mutableStateOf<Long?>(null) }
    var exportStartedAt by rememberSaveable { mutableStateOf<Long?>(null) }
    val export =
        rememberFileExporter(
            content = {
                val id = exportSessionId
                val startedAt = exportStartedAt
                if (id != null && startedAt != null) viewModel.csv(id, startedAt) else null
            },
        )
    val permission = rememberLocationPermissionState()
    val openLocationSettings = rememberOpenLocationSettings()
    val askForLocation = {
        if (permission.status == PermissionStatus.PERMANENTLY_DENIED) {
            permission.openAppSettings()
        } else {
            permission.request()
        }
    }
    SignalSurveyContent(
        state = state,
        actions =
        SignalSurveyActions(
            onRecordingChange = { recording ->
                // A radio that reports its own position can be surveyed without the phone's location, so the survey
                // starts either way and asks for access alongside.
                viewModel.setRecording(recording)
                if (recording && !permission.isGranted) askForLocation()
            },
            onFixBlocker = { blocker ->
                if (blocker == SignalSurveyBlocker.LOCATION_OFF) openLocationSettings() else askForLocation()
            },
            onVisibleChange = viewModel::setVisible,
            onExport = { sessionId ->
                exportSessionId = sessionId
                exportStartedAt =
                    state.sessions.firstOrNull { it.session.id == sessionId }?.session?.startedAtMillis
                export(SURVEY_FILE_NAME, CSV_MIME_TYPE)
            },
            onDelete = viewModel::delete,
            onHeatmapChange = viewModel::setHeatmap,
        ),
        modifier = modifier,
    )
}

/** What the controls can ask for, bundled so the stateless content keeps a short parameter list. */
internal class SignalSurveyActions(
    val onRecordingChange: (Boolean) -> Unit = {},
    val onFixBlocker: (SignalSurveyBlocker) -> Unit = {},
    val onVisibleChange: (sessionId: Long, visible: Boolean) -> Unit = { _, _ -> },
    val onExport: (sessionId: Long) -> Unit = {},
    val onDelete: (sessionId: Long) -> Unit = {},
    val onHeatmapChange: (Boolean) -> Unit = {},
)

@Composable
internal fun SignalSurveyContent(
    state: SignalSurveyUiState,
    actions: SignalSurveyActions,
    modifier: Modifier = Modifier,
) {
    var deleteSessionId by rememberSaveable { mutableStateOf<Long?>(null) }
    val blocker = state.blocker.takeIf { state.recording }
    Column(modifier = modifier.padding(bottom = 16.dp)) {
        Text(
            modifier = Modifier.padding(16.dp),
            text = stringResource(Res.string.signal_survey_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            modifier = Modifier.padding(horizontal = 16.dp),
            text = stringResource(Res.string.signal_survey_description),
            style = MaterialTheme.typography.bodySmall,
        )
        SignalLegend(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp))
        ListItem(
            modifier =
            Modifier.testTag(SIGNAL_SURVEY_SWITCH_TAG)
                .toggleable(value = state.recording, role = Role.Switch, onValueChange = actions.onRecordingChange),
            headlineContent = { Text(stringResource(Res.string.signal_survey_record)) },
            supportingContent = { Text(statusText(blocker, state.readingCount)) },
            // The row is the switch; a second toggle inside it would be announced twice.
            trailingContent = {
                Switch(modifier = Modifier.clearAndSetSemantics {}, checked = state.recording, onCheckedChange = null)
            },
        )
        ListItem(
            modifier =
            Modifier.testTag(SIGNAL_SURVEY_HEATMAP_TAG)
                .toggleable(value = state.heatmap, role = Role.Switch, onValueChange = actions.onHeatmapChange),
            headlineContent = { Text(stringResource(Res.string.signal_survey_heatmap)) },
            trailingContent = {
                Switch(modifier = Modifier.clearAndSetSemantics {}, checked = state.heatmap, onCheckedChange = null)
            },
        )
        fixLabel(blocker)?.let { label ->
            Button(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag(SIGNAL_SURVEY_FIX_TAG),
                onClick = { blocker?.let(actions.onFixBlocker) },
            ) {
                Text(label)
            }
        }
        // Not lazy, like the layer list above it: this sits inside the sheet's single scroll container.
        state.sessions.forEach { item ->
            key(item.session.id) {
                SessionRow(
                    item = item,
                    onVisibleChange = { actions.onVisibleChange(item.session.id, it) },
                    onExport = { actions.onExport(item.session.id) },
                    onDelete = { deleteSessionId = item.session.id },
                )
            }
        }
    }
    deleteSessionId?.let { sessionId ->
        ConfirmDeleteDialog(
            onConfirm = {
                deleteSessionId = null
                actions.onDelete(sessionId)
            },
            onDismiss = { deleteSessionId = null },
        )
    }
}

@Composable
private fun SessionRow(
    item: SurveySessionUi,
    onVisibleChange: (Boolean) -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    val count = item.session.readingCount
    ListItem(
        headlineContent = { Text(DateFormatter.formatDateTimeShort(item.session.startedAtMillis)) },
        supportingContent = { Text(pluralStringResource(Res.plurals.signal_survey_readings, count, count)) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconToggleButton(checked = item.visible, onCheckedChange = onVisibleChange) {
                    Icon(
                        imageVector = if (item.visible) MeshtasticIcons.Visibility else MeshtasticIcons.VisibilityOff,
                        contentDescription =
                        stringResource(if (item.visible) Res.string.hide_layer else Res.string.show_layer),
                    )
                }
                IconButton(onClick = onExport, enabled = count > 0) {
                    Icon(
                        imageVector = MeshtasticIcons.FileDownload,
                        contentDescription = stringResource(Res.string.signal_survey_export),
                    )
                }
                // The session being recorded is closed by the switch, not deleted out from under the recorder.
                IconButton(onClick = onDelete, enabled = !item.recording) {
                    Icon(imageVector = MeshtasticIcons.Delete, contentDescription = stringResource(Res.string.delete))
                }
            }
        },
    )
}

@Composable
private fun ConfirmDeleteDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(stringResource(Res.string.signal_survey_delete_confirm)) },
        confirmButton = {
            TextButton(
                modifier = Modifier.testTag(SIGNAL_SURVEY_CONFIRM_DELETE_TAG),
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(Res.string.delete))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}

/** What the squares' colors mean, strongest first: the node list's own signal bands. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SignalLegend(modifier: Modifier = Modifier) {
    FlowRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Quality.entries.reversed().forEach { quality ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(12.dp).background(quality.color(), RoundedCornerShape(2.dp)))
                Text(stringResource(quality.nameRes), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun statusText(blocker: SignalSurveyBlocker?, readingCount: Int): String = when (blocker) {
    SignalSurveyBlocker.NOT_CONNECTED -> stringResource(Res.string.not_connected)
    SignalSurveyBlocker.LOCATION_PERMISSION -> stringResource(Res.string.signal_survey_location_permission)
    SignalSurveyBlocker.LOCATION_OFF -> stringResource(Res.string.signal_survey_location_off)
    SignalSurveyBlocker.NO_FIX -> stringResource(Res.string.signal_survey_no_fix)
    null -> pluralStringResource(Res.plurals.signal_survey_readings, readingCount, readingCount)
}

/** The label of the one-tap fix for [blocker], where the user can do something about it. */
@Composable
private fun fixLabel(blocker: SignalSurveyBlocker?): String? = when (blocker) {
    SignalSurveyBlocker.LOCATION_PERMISSION -> stringResource(Res.string.grant_permission)
    SignalSurveyBlocker.LOCATION_OFF -> stringResource(Res.string.open_settings)
    else -> null
}
