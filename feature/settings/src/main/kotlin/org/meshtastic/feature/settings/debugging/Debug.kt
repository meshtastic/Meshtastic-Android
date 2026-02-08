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
package org.meshtastic.feature.settings.debugging

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.twotone.FilterAltOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.touchlab.kermit.Logger
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.debug_clear
import org.meshtastic.core.strings.debug_decoded_payload
import org.meshtastic.core.strings.debug_default_search
import org.meshtastic.core.strings.debug_export_failed
import org.meshtastic.core.strings.debug_export_success
import org.meshtastic.core.strings.debug_filters
import org.meshtastic.core.strings.debug_logs_export
import org.meshtastic.core.strings.debug_panel
import org.meshtastic.core.strings.debug_store_logs_summary
import org.meshtastic.core.strings.debug_store_logs_title
import org.meshtastic.core.strings.log_retention_days
import org.meshtastic.core.strings.log_retention_days_quantity
import org.meshtastic.core.strings.log_retention_days_summary
import org.meshtastic.core.strings.log_retention_hours
import org.meshtastic.core.strings.log_retention_never
import org.meshtastic.core.ui.component.CopyIconButton
import org.meshtastic.core.ui.component.DropDownPreference
import org.meshtastic.core.ui.component.MainAppBar
import org.meshtastic.core.ui.component.SwitchPreference
import org.meshtastic.core.ui.theme.AnnotationColor
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.core.ui.util.showToast
import org.meshtastic.feature.settings.debugging.DebugViewModel.UiMeshLog
import java.io.IOException
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val REGEX_ANNOTATED_NODE_ID = Regex("\\(![0-9a-fA-F]{8}\\)$", RegexOption.MULTILINE)

// list of dict keys to redact when exporting logs. These are evaluated as line.contains, so partials are fine.
private var redactedKeys: List<String> = listOf("session_passkey", "private_key", "admin_key")

@Suppress("LongMethod")
@Composable
fun DebugScreen(onNavigateUp: () -> Unit, viewModel: DebugViewModel = hiltViewModel()) {
    val listState = rememberLazyListState()
    val logs by viewModel.meshLog.collectAsStateWithLifecycle()
    val searchState by viewModel.searchState.collectAsStateWithLifecycle()
    val filterTexts by viewModel.filterTexts.collectAsStateWithLifecycle()
    val selectedLogId by viewModel.selectedLogId.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var filterMode by remember { mutableStateOf(FilterMode.OR) }

    val filteredLogsState by
        remember(logs, filterTexts, filterMode) {
            derivedStateOf { viewModel.filterManager.filterLogs(logs, filterTexts, filterMode).toImmutableList() }
        }
    val filteredLogs = filteredLogsState

    LaunchedEffect(filteredLogs) { viewModel.updateFilteredLogs(filteredLogs) }

    val shouldAutoScroll by remember { derivedStateOf { listState.firstVisibleItemIndex < 3 } }
    if (shouldAutoScroll) {
        LaunchedEffect(filteredLogs) {
            if (!listState.isScrollInProgress) {
                listState.animateScrollToItem(0)
            }
        }
    }
    // Handle search result navigation
    LaunchedEffect(searchState) {
        if (searchState.currentMatchIndex >= 0 && searchState.currentMatchIndex < searchState.allMatches.size) {
            listState.requestScrollToItem(searchState.allMatches[searchState.currentMatchIndex].logIndex)
        }
    }
    // Prepare a document creator for exporting logs via SAF
    val exportLogsLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { createdUri ->
            if (createdUri != null) {
                scope.launch { exportAllLogsToUri(context, createdUri, viewModel.loadLogsForExport()) }
            }
        }

    var showSettings by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            MainAppBar(
                title = stringResource(Res.string.debug_panel),
                ourNode = null,
                showNodeChip = false,
                canNavigateUp = true,
                onNavigateUp = onNavigateUp,
                actions = {
                    IconButton(onClick = { showSettings = !showSettings }) {
                        Icon(imageVector = Icons.Rounded.Settings, contentDescription = null)
                    }
                    DebugMenuActions(deleteLogs = { viewModel.requestDeleteAllLogs() })
                },
                onClickChip = {},
            )
        },
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            LazyColumn(modifier = Modifier.fillMaxSize(), state = listState) {
                stickyHeader {
                    val animatedAlpha by
                        animateFloatAsState(
                            targetValue = if (!listState.isScrollInProgress) 1.0f else 0f,
                            label = "alpha",
                        )
                    DebugSearchStateviewModelDefaults(
                        modifier = Modifier.graphicsLayer(alpha = animatedAlpha),
                        searchState = searchState,
                        filterTexts = filterTexts,
                        presetFilters = viewModel.presetFilters,
                        logs = logs,
                        filterMode = filterMode,
                        onFilterModeChange = { filterMode = it },
                        onExportLogs = {
                            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                            val fileName = "meshtastic_debug_$timestamp.txt"
                            exportLogsLauncher.launch(fileName)
                        },
                    )
                    if (showSettings) {
                        DebugLogSettings(viewModel = viewModel)
                    }
                }
                items(filteredLogs, key = { it.uuid }) { log ->
                    DebugItem(
                        modifier = Modifier.animateItem(),
                        log = log,
                        searchText = searchState.searchText,
                        isSelected = selectedLogId == log.uuid,
                        onLogClick = { viewModel.setSelectedLogId(if (selectedLogId == log.uuid) null else log.uuid) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DebugLogSettings(viewModel: DebugViewModel) {
    val retentionDays = viewModel.retentionDays.collectAsStateWithLifecycle().value
    val loggingEnabled = viewModel.loggingEnabled.collectAsStateWithLifecycle().value

    Column(
        modifier =
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        @Suppress("MagicNumber")
        val retentionItems =
            listOf((-1L) to pluralStringResource(Res.plurals.log_retention_hours, 1, 1)) +
                listOf(1, 3, 7, 14, 30, 60, 90, 180, 365).map { days ->
                    days.toLong() to pluralStringResource(Res.plurals.log_retention_days_quantity, days, days)
                } +
                listOf(0L to stringResource(Res.string.log_retention_never))
        DropDownPreference(
            title = stringResource(Res.string.log_retention_days),
            enabled = loggingEnabled,
            items = retentionItems,
            selectedItem = retentionDays.toLong(),
            onItemSelected = { selected: Long -> viewModel.setRetentionDays(selected.toInt()) },
            summary = stringResource(Res.string.log_retention_days_summary),
        )

        SwitchPreference(
            title = stringResource(Res.string.debug_store_logs_title),
            enabled = true,
            checked = loggingEnabled,
            onCheckedChange = { viewModel.setLoggingEnabled(it) },
            summary = stringResource(Res.string.debug_store_logs_summary),
        )
    }
}

@Composable
internal fun DebugItem(
    log: UiMeshLog,
    modifier: Modifier = Modifier,
    searchText: String = "",
    isSelected: Boolean = false,
    onLogClick: () -> Unit = {},
) {
    val colorScheme = MaterialTheme.colorScheme

    Card(
        modifier = modifier.fillMaxWidth().padding(4.dp),
        colors =
        CardDefaults.cardColors(
            containerColor =
            if (isSelected) {
                colorScheme.primary.copy(alpha = 0.1f)
            } else {
                colorScheme.surface
            },
        ),
        border =
        if (isSelected) {
            BorderStroke(2.dp, colorScheme.primary)
        } else {
            null
        },
    ) {
        SelectionContainer {
            Column(
                modifier = Modifier.padding(if (isSelected) 12.dp else 8.dp).fillMaxWidth().clickable { onLogClick() },
            ) {
                DebugItemHeader(log = log, searchText = searchText, isSelected = isSelected, theme = colorScheme)
                val messageAnnotatedString = rememberAnnotatedLogMessage(log, searchText)
                Text(
                    text = messageAnnotatedString,
                    style =
                    TextStyle(
                        fontSize = if (isSelected) 12.sp else 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = colorScheme.onSurface,
                    ),
                )
                // Show decoded payload if available, with search highlighting
                if (!log.decodedPayload.isNullOrBlank()) {
                    DecodedPayloadBlock(
                        decodedPayload = log.decodedPayload,
                        isSelected = isSelected,
                        colorScheme = colorScheme,
                        searchText = searchText,
                        modifier = Modifier,
                    )
                }
            }
        }
    }
}

@Composable
private fun DebugItemHeader(log: UiMeshLog, searchText: String, isSelected: Boolean, theme: ColorScheme) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = if (isSelected) 12.dp else 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val typeAnnotatedString = rememberAnnotatedString(text = log.messageType, searchText = searchText)
        Text(
            text = typeAnnotatedString,
            modifier = Modifier.weight(1f),
            style =
            TextStyle(
                fontWeight = FontWeight.Bold,
                fontSize = if (isSelected) 16.sp else 14.sp,
                color = theme.onSurface,
            ),
        )
        // Copy full log: message + decoded payload if present
        val fullLogText =
            remember(log.logMessage, log.decodedPayload) {
                buildString {
                    append(log.logMessage)
                    if (!log.decodedPayload.isNullOrBlank()) {
                        append("\n\nDecoded Payload:\n{")
                        append("\n")
                        append(log.decodedPayload)
                        append("\n}")
                    }
                }
            }
        CopyIconButton(valueToCopy = fullLogText, modifier = Modifier.padding(start = 8.dp))
        val dateAnnotatedString = rememberAnnotatedString(text = log.formattedReceivedDate, searchText = searchText)
        Text(
            text = dateAnnotatedString,
            style =
            TextStyle(
                fontWeight = FontWeight.Bold,
                fontSize = if (isSelected) 14.sp else 12.sp,
                color = theme.onSurface,
            ),
        )
    }
}

@Composable
private fun rememberAnnotatedString(text: String, searchText: String): AnnotatedString {
    val theme = MaterialTheme.colorScheme
    val highlightStyle = SpanStyle(background = theme.primary.copy(alpha = 0.3f), color = theme.onSurface)

    return remember(text, searchText) {
        buildAnnotatedString {
            append(text)
            if (searchText.isNotEmpty()) {
                searchText.split(" ").forEach { term ->
                    Regex(Regex.escape(term), RegexOption.IGNORE_CASE).findAll(text).forEach { match ->
                        addStyle(style = highlightStyle, start = match.range.first, end = match.range.last + 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberAnnotatedLogMessage(log: UiMeshLog, searchText: String): AnnotatedString {
    val theme = MaterialTheme.colorScheme
    val style = SpanStyle(color = AnnotationColor, fontStyle = FontStyle.Italic)
    val highlightStyle = SpanStyle(background = theme.primary.copy(alpha = 0.3f), color = theme.onSurface)

    return remember(log.uuid, searchText) {
        buildAnnotatedString {
            append(log.logMessage)

            // Add node ID annotations
            REGEX_ANNOTATED_NODE_ID.findAll(log.logMessage).toList().reversed().forEach {
                addStyle(style = style, start = it.range.first, end = it.range.last + 1)
            }

            // Add search highlight annotations
            if (searchText.isNotEmpty()) {
                searchText.split(" ").forEach { term ->
                    Regex(Regex.escape(term), RegexOption.IGNORE_CASE).findAll(log.logMessage).forEach { match ->
                        addStyle(style = highlightStyle, start = match.range.first, end = match.range.last + 1)
                    }
                }
            }
        }
    }
}

@Composable
fun DebugMenuActions(deleteLogs: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = deleteLogs, modifier = modifier.padding(4.dp)) {
        Icon(imageVector = Icons.Rounded.Delete, contentDescription = stringResource(Res.string.debug_clear))
    }
}

private suspend fun exportAllLogsToUri(context: Context, targetUri: Uri, logs: List<UiMeshLog>) =
    withContext(Dispatchers.IO) {
        try {
            if (logs.isEmpty()) {
                withContext(Dispatchers.Main) { context.showToast(Res.string.debug_export_failed, "No logs to export") }
                Logger.w { "MeshLog export aborted: no logs available" }
                return@withContext
            }

            context.contentResolver.openOutputStream(targetUri)?.use { os ->
                OutputStreamWriter(os, StandardCharsets.UTF_8).use { writer ->
                    logs.forEach { log ->
                        writer.write("${log.formattedReceivedDate} [${log.messageType}]\n")
                        writer.write(log.logMessage)
                        log.decodedPayload?.let { decodedPayload ->
                            if (decodedPayload.isNotBlank()) {
                                writer.write("\n\nDecoded Payload:\n{")
                                writer.write("\n")
                                // Redact Decoded keys.
                                decodedPayload.lineSequence().forEach { line ->
                                    var outputLine = line
                                    val redacted = redactedKeys.firstOrNull { line.contains(it) }
                                    if (redacted != null) {
                                        val idx = line.indexOf(':')
                                        if (idx != -1) {
                                            outputLine = line.take(idx + 1)
                                            outputLine += "<redacted>"
                                        }
                                    }
                                    writer.write(outputLine)
                                    writer.write("\n")
                                }
                                writer.write("\n}")
                            }
                        }
                        writer.write("\n\n")
                    }
                }
            } ?: run { throw IOException("Unable to open output stream for URI: $targetUri") }

            withContext(Dispatchers.Main) { context.showToast(Res.string.debug_export_success, logs.size) }
        } catch (e: IOException) {
            withContext(Dispatchers.Main) { context.showToast(Res.string.debug_export_failed, e.message ?: "") }
            Logger.w(e) { "MeshLog export failed" }
        }
    }

@Composable
private fun DecodedPayloadBlock(
    decodedPayload: String,
    isSelected: Boolean,
    colorScheme: ColorScheme,
    searchText: String = "",
    modifier: Modifier = Modifier,
) {
    val commonTextStyle =
        TextStyle(
            fontSize =
            if (isSelected) {
                10.sp
            } else {
                8.sp
            },
            fontWeight = FontWeight.Bold,
            color = colorScheme.primary,
        )

    Column(modifier = modifier) {
        Text(
            text = stringResource(Res.string.debug_decoded_payload),
            style = commonTextStyle,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
        )
        Text(text = "{", style = commonTextStyle, modifier = Modifier.padding(start = 8.dp, bottom = 2.dp))
        val annotatedPayload = rememberAnnotatedDecodedPayload(decodedPayload, searchText, colorScheme)
        Text(
            text = annotatedPayload,
            softWrap = true,
            style =
            TextStyle(
                fontSize = if (isSelected) 10.sp else 8.sp,
                fontFamily = FontFamily.Monospace,
                color = colorScheme.onSurface.copy(alpha = 0.8f),
            ),
            modifier = Modifier.padding(start = 16.dp, bottom = 0.dp),
        )
        Text(text = "}", style = commonTextStyle, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp))
    }
}

@Composable
private fun rememberAnnotatedDecodedPayload(
    decodedPayload: String,
    searchText: String,
    colorScheme: ColorScheme,
): AnnotatedString {
    val highlightStyle = SpanStyle(background = colorScheme.primary.copy(alpha = 0.3f), color = colorScheme.onSurface)
    return remember(decodedPayload, searchText) {
        buildAnnotatedString {
            append(decodedPayload)
            if (searchText.isNotEmpty()) {
                searchText.split(" ").forEach { term ->
                    Regex(Regex.escape(term), RegexOption.IGNORE_CASE).findAll(decodedPayload).forEach { match ->
                        addStyle(style = highlightStyle, start = match.range.first, end = match.range.last + 1)
                    }
                }
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun DebugPacketPreview() {
    AppTheme {
        DebugItem(
            UiMeshLog(
                uuid = "",
                messageType = "NodeInfo",
                formattedReceivedDate = "9/27/20, 8:00:58 PM",
                logMessage =
                "from: 2885173132\n" +
                    "decoded {\n" +
                    "   position {\n" +
                    "       altitude: 60\n" +
                    "       battery_level: 81\n" +
                    "       latitude_i: 411111136\n" +
                    "       longitude_i: -711111805\n" +
                    "       time: 1600390966\n" +
                    "   }\n" +
                    "}\n" +
                    "hop_limit: 3\n" +
                    "id: 1737414295\n" +
                    "rx_snr: 9.5\n" +
                    "rx_time: 316400569\n" +
                    "to: -1409790708",
            ),
        )
    }
}

@PreviewLightDark
@Composable
private fun DebugItemWithSearchHighlightPreview() {
    AppTheme {
        DebugItem(
            UiMeshLog(
                uuid = "1",
                messageType = "TextMessage",
                formattedReceivedDate = "9/27/20, 8:00:58 PM",
                logMessage = "Hello world! This is a test message with some keywords to search for.",
            ),
            searchText = "test message",
        )
    }
}

@PreviewLightDark
@Composable
private fun DebugItemPositionPreview() {
    AppTheme {
        DebugItem(
            UiMeshLog(
                uuid = "2",
                messageType = "Position",
                formattedReceivedDate = "9/27/20, 8:01:15 PM",
                logMessage = "Position update from node (!a1b2c3d4) at coordinates 40.7128, -74.0060",
            ),
        )
    }
}

@PreviewLightDark
@Composable
private fun DebugItemErrorPreview() {
    AppTheme {
        DebugItem(
            UiMeshLog(
                uuid = "3",
                messageType = "Error",
                formattedReceivedDate = "9/27/20, 8:02:30 PM",
                logMessage =
                "Connection failed: timeout after 30 seconds\n" +
                    "Retry attempt: 3/5\n" +
                    "Last known position: 40.7128, -74.0060",
            ),
        )
    }
}

@PreviewLightDark
@Composable
private fun DebugItemLongMessagePreview() {
    AppTheme {
        DebugItem(
            UiMeshLog(
                uuid = "4",
                messageType = "Waypoint",
                formattedReceivedDate = "9/27/20, 8:03:45 PM",
                logMessage =
                "Waypoint created:\n" +
                    "  Name: Home Base\n" +
                    "  Description: Primary meeting location\n" +
                    "  Latitude: 40.7128\n" +
                    "  Longitude: -74.0060\n" +
                    "  Altitude: 100m\n" +
                    "  Icon: 🏠\n" +
                    "  Created by: (!a1b2c3d4)\n" +
                    "  Expires: 2025-12-31 23:59:59",
            ),
        )
    }
}

@PreviewLightDark
@Composable
private fun DebugItemSelectedPreview() {
    AppTheme {
        DebugItem(
            UiMeshLog(
                uuid = "5",
                messageType = "TextMessage",
                formattedReceivedDate = "9/27/20, 8:04:20 PM",
                logMessage = "This is a selected log item with larger font sizes for better readability.",
            ),
            isSelected = true,
        )
    }
}

@PreviewLightDark
@Composable
private fun DebugMenuActionsPreview() {
    AppTheme {
        Row(modifier = Modifier.padding(16.dp)) {
            IconButton(onClick = { /* Preview only */ }, modifier = Modifier.padding(4.dp)) {
                Icon(
                    imageVector = Icons.Outlined.FileDownload,
                    contentDescription = stringResource(Res.string.debug_logs_export),
                )
            }
            IconButton(onClick = { /* Preview only */ }, modifier = Modifier.padding(4.dp)) {
                Icon(imageVector = Icons.Rounded.Delete, contentDescription = stringResource(Res.string.debug_clear))
            }
        }
    }
}

@PreviewLightDark
@Composable
@Suppress("detekt:LongMethod") // big preview
private fun DebugScreenEmptyPreview() {
    AppTheme {
        Surface {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                stickyHeader {
                    Surface(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.Start,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    OutlinedTextField(
                                        value = "",
                                        onValueChange = {},
                                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                                        placeholder = { Text(stringResource(Res.string.debug_default_search)) },
                                        singleLine = true,
                                    )
                                    TextButton(onClick = {}) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(text = "Filters", style = TextStyle(fontWeight = FontWeight.Bold))
                                            Icon(
                                                imageVector = Icons.TwoTone.FilterAltOff,
                                                contentDescription = stringResource(Res.string.debug_filters),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                // Empty state
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "No Debug Logs",
                                style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                            )
                            Text(
                                text = "Debug logs will appear here when available",
                                style = TextStyle(fontSize = 14.sp, color = Color.Gray),
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@PreviewLightDark
@Composable
@Suppress("detekt:LongMethod") // big preview
private fun DebugScreenWithSampleDataPreview() {
    AppTheme {
        val sampleLogs =
            listOf(
                UiMeshLog(
                    uuid = "1",
                    messageType = "NodeInfo",
                    formattedReceivedDate = "9/27/20, 8:00:58 PM",
                    logMessage =
                    "from: 2885173132\n" +
                        "decoded {\n" +
                        "   position {\n" +
                        "       altitude: 60\n" +
                        "       battery_level: 81\n" +
                        "       latitude_i: 411111136\n" +
                        "       longitude_i: -711111805\n" +
                        "       time: 1600390966\n" +
                        "   }\n" +
                        "}\n" +
                        "hop_limit: 3\n" +
                        "id: 1737414295\n" +
                        "rx_snr: 9.5\n" +
                        "rx_time: 316400569\n" +
                        "to: -1409790708",
                ),
                UiMeshLog(
                    uuid = "2",
                    messageType = "TextMessage",
                    formattedReceivedDate = "9/27/20, 8:01:15 PM",
                    logMessage = "Hello from node (!a1b2c3d4)! How's the weather today?",
                ),
                UiMeshLog(
                    uuid = "3",
                    messageType = "Position",
                    formattedReceivedDate = "9/27/20, 8:02:30 PM",
                    logMessage = "Position update: 40.7128, -74.0060, altitude: 100m, battery: 85%",
                ),
                UiMeshLog(
                    uuid = "4",
                    messageType = "Waypoint",
                    formattedReceivedDate = "9/27/20, 8:03:45 PM",
                    logMessage = "New waypoint created: 'Meeting Point' at 40.7589, -73.9851",
                ),
                UiMeshLog(
                    uuid = "5",
                    messageType = "Error",
                    formattedReceivedDate = "9/27/20, 8:04:20 PM",
                    logMessage = "Connection timeout - retrying in 5 seconds...",
                ),
            )

        // Note: This preview shows the UI structure but won't have actual data
        // since the ViewModel isn't injected in previews
        Surface {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                stickyHeader {
                    Surface(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = "Debug Screen Preview",
                                style = TextStyle(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                            Text(
                                text = "Search and filter controls would appear here",
                                style = TextStyle(fontSize = 12.sp, color = Color.Gray),
                            )
                        }
                    }
                }
                items(sampleLogs) { log -> DebugItem(log = log) }
            }
        }
    }
}
