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
package org.meshtastic.feature.settings.debugging

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.toImmutableList
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.debug_clear
import org.meshtastic.core.resources.debug_decoded_payload
import org.meshtastic.core.resources.debug_panel
import org.meshtastic.core.resources.debug_store_logs_summary
import org.meshtastic.core.resources.debug_store_logs_title
import org.meshtastic.core.resources.log_retention_days
import org.meshtastic.core.resources.log_retention_days_quantity
import org.meshtastic.core.resources.log_retention_days_summary
import org.meshtastic.core.resources.log_retention_hours
import org.meshtastic.core.resources.log_retention_never
import org.meshtastic.core.ui.component.CopyIconButton
import org.meshtastic.core.ui.component.DropDownPreference
import org.meshtastic.core.ui.component.MainAppBar
import org.meshtastic.core.ui.component.SwitchPreference
import org.meshtastic.core.ui.icon.Delete
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Settings
import org.meshtastic.core.ui.theme.AnnotationColor
import org.meshtastic.feature.settings.debugging.DebugViewModel.UiMeshLog
import kotlin.time.Instant.Companion.fromEpochMilliseconds

private val REGEX_ANNOTATED_NODE_ID = Regex("\\(![0-9a-fA-F]{8}\\)$", RegexOption.MULTILINE)

@Suppress("LongMethod")
@Composable
fun DebugScreen(onNavigateUp: () -> Unit, viewModel: DebugViewModel) {
    val listState = rememberLazyListState()
    val logs by viewModel.meshLog.collectAsStateWithLifecycle()
    val searchState by viewModel.searchState.collectAsStateWithLifecycle()
    val filterTexts by viewModel.filterTexts.collectAsStateWithLifecycle()
    val selectedLogId by viewModel.selectedLogId.collectAsStateWithLifecycle()

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
    // Prepare a document creator for exporting logs
    val exportLogsLauncher = rememberLogExporter { viewModel.loadLogsForExport() }

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
                    IconToggleButton(checked = showSettings, onCheckedChange = { showSettings = it }) {
                        Icon(imageVector = MeshtasticIcons.Settings, contentDescription = null)
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
                    DebugSearchStateWithViewModel(
                        viewModel = viewModel,
                        modifier = Modifier.graphicsLayer(alpha = animatedAlpha),
                        searchState = searchState,
                        filterTexts = filterTexts,
                        presetFilters = viewModel.presetFilters,
                        logs = logs,
                        filterMode = filterMode,
                        onFilterModeChange = { filterMode = it },
                        onExportLogs = {
                            val format =
                                LocalDateTime.Format {
                                    year()
                                    monthNumber()
                                    day()
                                    char('_')
                                    hour()
                                    minute()
                                    second()
                                }
                            val timestamp =
                                fromEpochMilliseconds(nowMillis).toLocalDateTime(TimeZone.UTC).format(format)
                            val fileName = "meshtastic_debug_$timestamp.txt"
                            exportLogsLauncher(fileName)
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
        Icon(imageVector = MeshtasticIcons.Delete, contentDescription = stringResource(Res.string.debug_clear))
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
