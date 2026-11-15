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
package org.meshtastic.feature.widget

import android.app.UiModeManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.datastore.preferences.core.Preferences
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.effectiveLongName
import org.meshtastic.core.repository.UiPrefs
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.doc_no_results
import org.meshtastic.core.resources.node_filter_placeholder
import org.meshtastic.core.resources.save
import org.meshtastic.core.resources.widget_telemetry_change_node
import org.meshtastic.core.resources.widget_telemetry_choose
import org.meshtastic.core.resources.widget_telemetry_no_nodes_hint
import org.meshtastic.core.resources.widget_telemetry_no_nodes_title
import org.meshtastic.core.resources.widget_telemetry_readings_hint
import org.meshtastic.core.resources.widget_telemetry_title
import org.meshtastic.core.ui.component.EmptyState
import org.meshtastic.core.ui.component.MeshtasticSearchBar
import org.meshtastic.core.ui.component.NodeChip
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Nodes
import org.meshtastic.core.ui.icon.Search
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.core.ui.theme.MODE_DYNAMIC

/**
 * Configures one [TelemetryWidget]: the node, and the readings to hide. The launcher opens it when the widget is placed
 * or reconfigured, and the widget opens it when its node has gone. A new widget starts on our own node when it reports
 * telemetry, with every reading shown.
 */
class TelemetryConfigActivity :
    ComponentActivity(),
    KoinComponent {

    private val dataSource: TelemetryWidgetDataSource by inject()
    private val uiPrefs: UiPrefs by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Backing out must cancel placement, so the result is CANCELED until the user saves.
        setResult(RESULT_CANCELED)

        val appWidgetId =
            intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Exported for the launcher, so only ids bound to our own telemetry provider are honored.
        val provider = AppWidgetManager.getInstance(this).getAppWidgetInfo(appWidgetId)?.provider
        if (provider != ComponentName(this, TelemetryWidgetReceiver::class.java)) {
            finish()
            return
        }

        WindowCompat.enableEdgeToEdge(window)
        setContent {
            val theme by uiPrefs.theme.collectAsState()
            // UiPrefs.theme holds AppCompatDelegate night modes, whose values UiModeManager shares.
            val dark =
                when (theme) {
                    UiModeManager.MODE_NIGHT_YES -> true
                    UiModeManager.MODE_NIGHT_NO -> false
                    else -> isSystemInDarkTheme()
                }
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            AppTheme(dynamicColor = theme == MODE_DYNAMIC, darkTheme = dark) {
                // One snapshot: a live list re-sorts under the user's finger on a busy mesh.
                val setup by produceState<TelemetrySetup?>(null) { value = loadSetup(appWidgetId) }
                setup?.let {
                    TelemetryConfigScreen(it, dataSource, onSave = { node, hidden -> save(appWidgetId, node, hidden) })
                }
            }
        }
    }

    private suspend fun loadSetup(appWidgetId: Int): TelemetrySetup {
        // Read from the database directly; an empty one (no radio's DB open) shows the empty state, not a wait.
        val inputs = dataSource.currentInputs()
        // A widget id Glance does not know yet (first placement) has no settings to start from.
        val prefs =
            try {
                val glanceId = GlanceAppWidgetManager(this).getGlanceIdBy(appWidgetId)
                getAppWidgetState<Preferences>(this, PreferencesGlanceStateDefinition, glanceId)
            } catch (e: IllegalArgumentException) {
                Logger.d(e) { "Telemetry widget $appWidgetId has no Glance state yet" }
                null
            }
        val current = prefs?.get(TELEMETRY_NODE_NUM_KEY)?.let { inputs.nodes[it] }
        return TelemetrySetup(
            inputs = inputs,
            initialNode = current ?: defaultTelemetryNode(inputs.nodes.values, inputs.ourNodeNum),
            initialHidden = if (current != null) prefs.readingSet(TELEMETRY_HIDDEN_KEY) else emptySet(),
            seen = if (current != null) prefs.readingSet(TELEMETRY_SEEN_KEY) else emptySet(),
        )
    }

    private fun save(appWidgetId: Int, nodeNum: Int, hidden: Set<TelemetryReading>) {
        lifecycleScope.launch {
            try {
                val glanceId = GlanceAppWidgetManager(this@TelemetryConfigActivity).getGlanceIdBy(appWidgetId)
                updateAppWidgetState(this@TelemetryConfigActivity, glanceId) {
                    it.storeTelemetrySettings(nodeNum, hidden)
                }
                TelemetryWidget().update(this@TelemetryConfigActivity, glanceId)
                setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
            } catch (e: IllegalArgumentException) {
                Logger.w(e) { "Telemetry widget $appWidgetId is not a Glance widget; leaving it unconfigured" }
            }
            finish()
        }
    }
}

internal data class TelemetrySetup(
    val inputs: TelemetryWidgetInputs,
    val initialNode: Node?,
    val initialHidden: Set<TelemetryReading>,
    /** What the instance has seen its node report, so a reading it shows with a dash can be hidden too. */
    val seen: Set<TelemetryReading>,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TelemetryConfigScreen(
    setup: TelemetrySetup,
    dataSource: TelemetryWidgetDataSource,
    onSave: (nodeNum: Int, hidden: Set<TelemetryReading>) -> Unit,
) {
    var nodeNum by rememberSaveable { mutableStateOf(setup.initialNode?.num) }
    var picking by rememberSaveable { mutableStateOf(false) }
    var hiddenIds by rememberSaveable { mutableStateOf(setup.initialHidden.map { it.id }.toSet()) }
    val node = nodeNum?.let { setup.inputs.nodes[it] }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(Res.string.widget_telemetry_title)) }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (node == null || picking) {
                NodeList(
                    nodes = setup.inputs.nodes.values,
                    ourNodeNum = setup.inputs.ourNodeNum,
                    onPick = { picked ->
                        if (picked.num != nodeNum) hiddenIds = emptySet()
                        nodeNum = picked.num
                        picking = false
                    },
                )
            } else {
                val history by produceState(emptyList(), node.num) { value = dataSource.currentHistory(node.num) }
                val state =
                    telemetryWidgetState(
                        node.num,
                        emptySet(),
                        if (node.num == setup.initialNode?.num) setup.seen else emptySet(),
                        setup.inputs,
                        history,
                    )
                        as? TelemetryWidgetState.Showing
                ListItem(
                    leadingContent = { NodeChip(node = node) },
                    headlineContent = { Text(node.effectiveLongName.ifEmpty { node.user.id }, maxLines = 1) },
                    trailingContent = {
                        TextButton(onClick = { picking = true }) {
                            Text(stringResource(Res.string.widget_telemetry_change_node))
                        }
                    },
                )
                Text(
                    stringResource(Res.string.widget_telemetry_readings_hint),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                ReadingList(
                    state = state,
                    hiddenIds = hiddenIds,
                    onToggle = { id, shown -> hiddenIds = if (shown) hiddenIds - id else hiddenIds + id },
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = { onSave(node.num, hiddenIds.mapNotNull(TelemetryReading::fromId).toSet()) },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) {
                    Text(stringResource(Res.string.save))
                }
            }
        }
    }
}

/** Every reading the node has reported, each with a switch; off hides it from the widget. */
@Composable
private fun ReadingList(
    state: TelemetryWidgetState.Showing?,
    hiddenIds: Set<String>,
    onToggle: (id: String, shown: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier) {
        if (state == null) return@LazyColumn
        items(state.readings, key = { it.id }) { reading ->
            val tile = telemetryTile(reading, state)
            val shown = reading.id !in hiddenIds
            ListItem(
                modifier =
                Modifier.clickable {
                    onToggle(reading.id, !shown)
                },
                leadingContent = {
                    Icon(
                        painterResource(tile.icon),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                },
                headlineContent = { Text(tile.label) },
                supportingContent = { Text(tile.value ?: MISSING_VALUE) },
                trailingContent = {
                    Switch(
                        checked = shown,
                        onCheckedChange = { on ->
                            onToggle(reading.id, on)
                        },
                    )
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColumnScope.NodeList(nodes: Collection<Node>, ourNodeNum: Int?, onPick: (Node) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val all = remember(nodes, ourNodeNum) { telemetryCandidates(nodes, ourNodeNum) }
    val matches = remember(nodes, ourNodeNum, query) { telemetryCandidates(nodes, ourNodeNum, query) }
    if (all.isEmpty()) {
        EmptyState(
            icon = MeshtasticIcons.Nodes,
            title = stringResource(Res.string.widget_telemetry_no_nodes_title),
            supportingText = stringResource(Res.string.widget_telemetry_no_nodes_hint),
            modifier = Modifier.fillMaxSize(),
        )
        return
    }
    MeshtasticSearchBar(
        query = query,
        onQueryChange = { query = it },
        placeholder = stringResource(Res.string.node_filter_placeholder),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        expandedContent = {
            if (matches.isEmpty()) {
                EmptyState(icon = MeshtasticIcons.Search, title = stringResource(Res.string.doc_no_results))
            } else {
                CandidateList(matches, onPick)
            }
        },
    )
    Text(
        stringResource(Res.string.widget_telemetry_choose),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
    CandidateList(all, onPick)
}
