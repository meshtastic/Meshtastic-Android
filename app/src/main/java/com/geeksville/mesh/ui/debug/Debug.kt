/*
 * Copyright (c) 2025 Meshtastic LLC
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

package com.geeksville.mesh.ui.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.twotone.FilterAlt
import androidx.compose.material.icons.twotone.FilterAltOff
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.R
import com.geeksville.mesh.model.DebugViewModel
import com.geeksville.mesh.model.DebugViewModel.UiMeshLog
import com.geeksville.mesh.ui.common.theme.AppTheme
import com.geeksville.mesh.ui.common.components.CopyIconButton
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp


private val REGEX_ANNOTATED_NODE_ID = Regex("\\(![0-9a-fA-F]{8}\\)$", RegexOption.MULTILINE)

@Composable
internal fun DebugScreen(
    viewModel: DebugViewModel = hiltViewModel(),
) {
    val listState = rememberLazyListState()
    val logs by viewModel.meshLog.collectAsStateWithLifecycle()

    var filterTexts by remember { mutableStateOf(listOf<String>()) }
    var customFilterText by remember { mutableStateOf("") }
    var showFilterMenu by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var currentMatchIndex by remember { mutableStateOf(-1) }
    
    val filteredLogs by remember(logs) {
        derivedStateOf {
            logs.filter { log ->
                filterTexts.isEmpty() || filterTexts.any { filterText ->
                    log.logMessage.contains(filterText, ignoreCase = true) ||
                    log.messageType.contains(filterText, ignoreCase = true) ||
                    log.formattedReceivedDate.contains(filterText, ignoreCase = true)
                }
            }
        }
    }

    val shouldAutoScroll by remember { derivedStateOf { listState.firstVisibleItemIndex < 3 } }
    if (shouldAutoScroll) {
        LaunchedEffect(filteredLogs) {
            if (!listState.isScrollInProgress) {
                listState.animateScrollToItem(0)
            }
        }
    }

    data class SearchMatch(
        val logIndex: Int,
        val start: Int,
        val end: Int,
        val field: String
    )
    
    val allMatches = remember(searchText, filteredLogs) {
        if (searchText.isEmpty()) emptyList()
        else filteredLogs.flatMapIndexed { logIndex, log ->
            searchText.split(" ").flatMap { term ->
                val messageMatches = term.toRegex(RegexOption.IGNORE_CASE).findAll(log.logMessage)
                    .map { match -> SearchMatch(logIndex, match.range.first, match.range.last, "message") }
                val typeMatches = term.toRegex(RegexOption.IGNORE_CASE).findAll(log.messageType)
                    .map { match -> SearchMatch(logIndex, match.range.first, match.range.last, "type") }
                val dateMatches = term.toRegex(RegexOption.IGNORE_CASE).findAll(log.formattedReceivedDate)
                    .map { match -> SearchMatch(logIndex, match.range.first, match.range.last, "date") }
                messageMatches + typeMatches + dateMatches
            }
        }.sortedBy { it.start }
    }

    val hasMatches = allMatches.isNotEmpty()
    
    fun scrollToMatch(index: Int) {
        if (index in allMatches.indices) {
            currentMatchIndex = index
            val match = allMatches[index]
            listState.requestScrollToItem(match.logIndex)
        }
    }

    fun goToNextMatch() {
        if (hasMatches) {
            val nextIndex = if (currentMatchIndex < allMatches.lastIndex) currentMatchIndex + 1 else 0
            scrollToMatch(nextIndex)
        }
    }

    fun goToPreviousMatch() {
        if (hasMatches) {
            val prevIndex = if (currentMatchIndex > 0) currentMatchIndex - 1 else allMatches.lastIndex
            scrollToMatch(prevIndex)
        }
    }

    // Reset current match when search text changes
    LaunchedEffect(searchText) {
        currentMatchIndex = -1
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
    ) {
        stickyHeader {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = searchText,
                                onValueChange = { searchText = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp),
                                placeholder = { Text("Search in logs...") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(
                                    onSearch = {
                                        // Clear focus when search is performed
                                    }
                                ),
                                trailingIcon = {
                                    Row {
                                        if (hasMatches) {
                                            Text(
                                                text = "${currentMatchIndex + 1}/${allMatches.size}",
                                                modifier = Modifier.padding(end = 8.dp),
                                                style = TextStyle(fontSize = 12.sp)
                                            )
                                            IconButton(
                                                onClick = { goToPreviousMatch() },
                                                enabled = hasMatches
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.KeyboardArrowUp,
                                                    contentDescription = "Previous match"
                                                )
                                            }
                                            IconButton(
                                                onClick = { goToNextMatch() },
                                                enabled = hasMatches
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.KeyboardArrowDown,
                                                    contentDescription = "Next match"
                                                )
                                            }
                                        }
                                        if (searchText.isNotEmpty()) {
                                            IconButton(
                                                onClick = { searchText = "" }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Clear,
                                                    contentDescription = "Clear search"
                                                )
                                            }
                                        }
                                    }
                                }
                            )
                            Box {
                                TextButton(
                                    onClick = { showFilterMenu = !showFilterMenu }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = stringResource(R.string.debug_filters),
                                            style = TextStyle(fontWeight = FontWeight.Bold)
                                        )
                                        Icon(
                                            imageVector = if (filterTexts.isNotEmpty()) 
                                                Icons.TwoTone.FilterAlt else Icons.TwoTone.FilterAltOff,
                                            contentDescription = "Filter"
                                        )
                                    }
                                }
                                DropdownMenu(
                                    expanded = showFilterMenu,
                                    onDismissRequest = { showFilterMenu = false },
                                    offset = DpOffset(0.dp, 8.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .padding(8.dp)
                                            .width(300.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            OutlinedTextField(
                                                value = customFilterText,
                                                onValueChange = { customFilterText = it },
                                                modifier = Modifier.weight(1f),
                                                placeholder = { Text("Add custom filter") },
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                                keyboardActions = KeyboardActions(
                                                    onDone = {
                                                        if (customFilterText.isNotBlank()) {
                                                            filterTexts = filterTexts + customFilterText
                                                            customFilterText = ""
                                                        }
                                                    }
                                                )
                                            )
                                            Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                                            IconButton(
                                                onClick = {
                                                    if (customFilterText.isNotBlank()) {
                                                        filterTexts = filterTexts + customFilterText
                                                        customFilterText = ""
                                                    }
                                                },
                                                enabled = customFilterText.isNotBlank()
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Add,
                                                    contentDescription = "Add filter"
                                                )
                                            }
                                        }

                                        Text(
                                            text = "Preset Filters",
                                            style = TextStyle(fontWeight = FontWeight.Bold),
                                            modifier = Modifier.padding(vertical = 4.dp)
                                        )
                                        FlowRow(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 0.dp),
                                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                                            verticalArrangement = Arrangement.spacedBy(0.dp)
                                        ) {
                                            for (filter in viewModel.presetFilters) {
                                                FilterChip(
                                                    selected = filter in filterTexts,
                                                    onClick = {
                                                        filterTexts = if (filter in filterTexts) {
                                                            filterTexts - filter
                                                        } else {
                                                            filterTexts + filter
                                                        }
                                                    },
                                                    label = { Text(filter) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (filterTexts.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.debug_active_filters),
                                style = TextStyle(fontWeight = FontWeight.Bold)
                            )
                            IconButton(
                                onClick = { filterTexts = emptyList() }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear all filters"
                                )
                            }
                        }
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 0.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalArrangement = Arrangement.spacedBy(0.dp)
                        ) {
                            for (filter in filterTexts) {
                                FilterChip(
                                    selected = true,
                                    onClick = {
                                        filterTexts = filterTexts - filter
                                    },
                                    label = { Text(filter) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.TwoTone.FilterAlt,
                                            contentDescription = null
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        items(filteredLogs, key = { it.uuid }) { log ->
            DebugItem(
                modifier = Modifier.animateItem(),
                log = log,
                searchText = searchText
            )
        }
    }
}

@Composable
internal fun DebugItem(
    log: UiMeshLog,
    modifier: Modifier = Modifier,
    searchText: String = "",
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(4.dp),
    ) {
        SelectionContainer {
            Column(
                modifier = Modifier.padding(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val typeAnnotatedString = rememberAnnotatedString(
                        text = log.messageType,
                        searchText = searchText
                    )
                    Text(
                        text = typeAnnotatedString,
                        modifier = Modifier.weight(1f),
                        style = TextStyle(fontWeight = FontWeight.Bold),
                    )
                    CopyIconButton(
                        valueToCopy = log.logMessage,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                    Icon(
                        imageVector = Icons.Outlined.CloudDownload,
                        contentDescription = stringResource(id = R.string.logs),
                        tint = Color.Gray.copy(alpha = 0.6f),
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    val dateAnnotatedString = rememberAnnotatedString(
                        text = log.formattedReceivedDate,
                        searchText = searchText
                    )
                    Text(
                        text = dateAnnotatedString,
                        style = TextStyle(fontWeight = FontWeight.Bold),
                    )
                }

                val messageAnnotatedString = rememberAnnotatedLogMessage(log, searchText)
                Text(
                    text = messageAnnotatedString,
                    softWrap = false,
                    style = TextStyle(
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                )
            }
        }
    }
}

@Composable
private fun rememberAnnotatedString(
    text: String,
    searchText: String
): AnnotatedString {
    val highlightStyle = SpanStyle(
        background = Color.Yellow.copy(alpha = 0.3f),
        color = Color.Black
    )
    
    return remember(text, searchText) {
        buildAnnotatedString {
            append(text)
            
            if (searchText.isNotEmpty()) {
                searchText.split(" ").forEach { term ->
                    term.toRegex(RegexOption.IGNORE_CASE).findAll(text).forEach { match ->
                        addStyle(
                            style = highlightStyle,
                            start = match.range.first,
                            end = match.range.last + 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberAnnotatedLogMessage(log: UiMeshLog, searchText: String): AnnotatedString {
    val style = SpanStyle(
        color = colorResource(id = R.color.colorAnnotation),
        fontStyle = FontStyle.Italic,
    )
    val highlightStyle = SpanStyle(
        background = Color.Yellow.copy(alpha = 0.3f),
        color = Color.Black
    )
    
    return remember(log.uuid, searchText) {
        buildAnnotatedString {
            append(log.logMessage)
            
            // Add node ID annotations
            REGEX_ANNOTATED_NODE_ID.findAll(log.logMessage).toList().reversed()
                .forEach {
                    addStyle(
                        style = style,
                        start = it.range.first,
                        end = it.range.last + 1
                    )
                }
            
            // Add search highlight annotations
            if (searchText.isNotEmpty()) {
                searchText.split(" ").forEach { term ->
                    term.toRegex(RegexOption.IGNORE_CASE).findAll(log.logMessage).forEach { match ->
                        addStyle(
                            style = highlightStyle,
                            start = match.range.first,
                            end = match.range.last + 1
                        )
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
                logMessage = "from: 2885173132\n" +
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
            )
        )
    }
}

@Composable
fun DebugMenuActions(
    viewModel: DebugViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {

    // Button(
    //     // FIXME: needs to do what it says on the label 
    //     onClick = viewModel::deleteAllLogs,
    //     modifier = modifier,
    // ) {
    //     Text(text = stringResource(R.string.map_start_download)) // should rename to be generic 
    // }
    Button(
        onClick = viewModel::deleteAllLogs,
        modifier = modifier,
    ) {
        Text(text = stringResource(R.string.clear))
    }
}
