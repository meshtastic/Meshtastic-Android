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
@file:Suppress("MatchingDeclarationName") // named for its main export LogcatContent; LogLevel is a supporting type

package org.meshtastic.feature.settings.debugging

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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.common.util.ioDispatcher
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.debug_default_search
import org.meshtastic.core.resources.debug_logcat_empty
import org.meshtastic.core.resources.debug_logcat_refresh
import org.meshtastic.core.resources.debug_logs_export
import org.meshtastic.core.ui.icon.FileDownload
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Refresh

/** Logcat priority levels the user can toggle. Fatal/assert lines carry an unknown code and are always shown. */
enum class LogLevel(val code: Char) {
    VERBOSE('V'),
    DEBUG('D'),
    INFO('I'),
    WARN('W'),
    ERROR('E'),
}

private val KNOWN_LEVEL_CODES = LogLevel.entries.map { it.code }.toSet()

// Matches the priority in both Android's `logcat -v time` (" D/Tag") and the desktop ring buffer ("D/Tag") shapes.
private val LEVEL_REGEX = Regex("(?:^|\\s)([VDIWEFA])/")

/** The priority code of a `-v time` logcat line (the letter before `/`), or null for continuation lines. */
fun logcatLineLevel(line: String): Char? = LEVEL_REGEX.find(line)?.groupValues?.getOrNull(1)?.firstOrNull()

/** Filters raw logcat text by selected [levels] and a case-insensitive [query] substring. */
fun filterLogcat(raw: String, levels: Set<LogLevel>, query: String): List<String> = raw.lineSequence()
    .filter { it.isNotBlank() }
    .filter { line ->
        val code = logcatLineLevel(line)
        // Keep continuation lines and unknown priorities (e.g. F/A); only hide a known-but-deselected level.
        code == null || code !in KNOWN_LEVEL_CODES || levels.any { it.code == code }
    }
    .filter { query.isBlank() || it.contains(query, ignoreCase = true) }
    .toList()

@Composable
private fun logcatLineColor(line: String): Color = when (logcatLineLevel(line)) {
    'E',
    'F',
    'A',
    -> MaterialTheme.colorScheme.error

    'W' -> MaterialTheme.colorScheme.tertiary

    else -> MaterialTheme.colorScheme.onSurface
}

/** Simple in-app viewer for the app's own logcat: search box, level chips, refresh, and export to a file. */
@Suppress("LongMethod")
@Composable
fun LogcatContent(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var raw by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var levels by remember { mutableStateOf(LogLevel.entries.toSet()) }
    val listState = rememberLazyListState()

    val export = rememberLogExporter { buildString { appendLogcat(this, raw.orEmpty()) } }

    fun refresh() = scope.launch { raw = withContext(ioDispatcher) { captureAppLogcat() } }
    LaunchedEffect(Unit) { refresh() }

    // Redact sensitive keys before display, matching the export path (appendLogcat) so the on-screen view is safe too.
    val lines = remember(raw, levels, query) { filterLogcat(redactText(raw.orEmpty()), levels, query) }

    Column(modifier = modifier.fillMaxSize().padding(8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(stringResource(Res.string.debug_default_search)) },
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = { refresh() }) {
                    Icon(MeshtasticIcons.Refresh, contentDescription = stringResource(Res.string.debug_logcat_refresh))
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LogLevel.entries.forEach { level ->
                FilterChip(
                    selected = level in levels,
                    onClick = { levels = if (level in levels) levels - level else levels + level },
                    label = { Text(level.code.toString()) },
                )
            }
            Box(modifier = Modifier.weight(1f))
            IconButton(onClick = { export(timestampedExportName("meshtastic_logcat")) }) {
                Icon(MeshtasticIcons.FileDownload, contentDescription = stringResource(Res.string.debug_logs_export))
            }
        }
        if (lines.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(Res.string.debug_logcat_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            SelectionContainer {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(lines) { line ->
                        Text(
                            text = line,
                            color = logcatLineColor(line),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}
