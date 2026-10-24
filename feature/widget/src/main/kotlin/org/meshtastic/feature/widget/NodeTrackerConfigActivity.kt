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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.lifecycle.lifecycleScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.effectiveLongName
import org.meshtastic.core.repository.ConnectionStateProvider
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.UiPrefs
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.doc_no_results
import org.meshtastic.core.resources.node_filter_placeholder
import org.meshtastic.core.resources.nodes_empty_disconnected_hint
import org.meshtastic.core.resources.nodes_empty_disconnected_title
import org.meshtastic.core.resources.nodes_empty_searching_hint
import org.meshtastic.core.resources.nodes_empty_searching_title
import org.meshtastic.core.resources.widget_node_tracker_choose
import org.meshtastic.core.ui.component.EmptyState
import org.meshtastic.core.ui.component.MeshtasticSearchBar
import org.meshtastic.core.ui.component.NodeChip
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.NoDevice
import org.meshtastic.core.ui.icon.Nodes
import org.meshtastic.core.ui.icon.Search
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.core.ui.theme.MODE_DYNAMIC
import org.meshtastic.core.ui.util.formatAgo

/**
 * Picks the node a [NodeTrackerWidget] instance tracks. The launcher opens it when the widget is placed or
 * reconfigured, and the widget opens it when its node has gone.
 */
class NodeTrackerConfigActivity :
    ComponentActivity(),
    KoinComponent {

    private val nodeRepository: NodeRepository by inject()
    private val connectionStateProvider: ConnectionStateProvider by inject()
    private val uiPrefs: UiPrefs by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Backing out must cancel placement, so the result is CANCELED until a node is picked.
        setResult(RESULT_CANCELED)

        val appWidgetId =
            intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Exported for the launcher, so only ids bound to our own tracker provider are honored.
        val provider = AppWidgetManager.getInstance(this).getAppWidgetInfo(appWidgetId)?.provider
        if (provider != ComponentName(this, NodeTrackerWidgetReceiver::class.java)) {
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
                // One snapshot: a live list re-sorts by last heard under the user's finger on a busy mesh. Read the DB
                // directly, since the repository's flow opens on an empty map; an empty DB waits for its first nodes.
                val nodes by
                    produceState<Map<Int, Node>?>(null) {
                        value = nodeRepository.getNodeDbSnapshot()
                        if (value.isNullOrEmpty()) value = nodeRepository.nodeDBbyNum.first { it.isNotEmpty() }
                    }
                val ourNodeNum = nodeRepository.myNodeInfo.collectAsState().value?.myNodeNum
                val connectionState by connectionStateProvider.connectionState.collectAsState()
                NodeTrackerPicker(
                    nodes = nodes?.values,
                    ourNodeNum = ourNodeNum,
                    connected = connectionState is ConnectionState.Connected,
                    onPick = { node -> track(appWidgetId, node.num) },
                )
            }
        }
    }

    private fun track(appWidgetId: Int, nodeNum: Int) {
        lifecycleScope.launch {
            try {
                val glanceId = GlanceAppWidgetManager(this@NodeTrackerConfigActivity).getGlanceIdBy(appWidgetId)
                updateAppWidgetState(this@NodeTrackerConfigActivity, glanceId) { it[TRACKED_NODE_NUM_KEY] = nodeNum }
                NodeTrackerWidget().update(this@NodeTrackerConfigActivity, glanceId)
                setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
            } catch (e: IllegalArgumentException) {
                Logger.w(e) { "Node Tracker widget $appWidgetId is not a Glance widget; leaving it unconfigured" }
            }
            finish()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NodeTrackerPicker(nodes: Collection<Node>?, ourNodeNum: Int?, connected: Boolean, onPick: (Node) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val all = remember(nodes, ourNodeNum) { trackerCandidates(nodes.orEmpty(), "", ourNodeNum) }
    val matches = remember(nodes, ourNodeNum, query) { trackerCandidates(nodes.orEmpty(), query, ourNodeNum) }
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(Res.string.widget_node_tracker_choose)) }) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
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
            when {
                // Still reading the snapshot; an empty state here would flash before the list.
                nodes == null -> Unit

                all.isNotEmpty() -> CandidateList(all, onPick)

                connected ->
                    EmptyState(
                        icon = MeshtasticIcons.Nodes,
                        title = stringResource(Res.string.nodes_empty_searching_title),
                        supportingText = stringResource(Res.string.nodes_empty_searching_hint),
                        modifier = Modifier.fillMaxSize(),
                    )

                else ->
                    EmptyState(
                        icon = MeshtasticIcons.NoDevice,
                        title = stringResource(Res.string.nodes_empty_disconnected_title),
                        supportingText = stringResource(Res.string.nodes_empty_disconnected_hint),
                        modifier = Modifier.fillMaxSize(),
                    )
            }
        }
    }
}

@Composable
private fun ColumnScope.CandidateList(nodes: List<Node>, onPick: (Node) -> Unit) {
    LazyColumn(modifier = Modifier.weight(1f)) {
        items(nodes, key = { it.num }) { node ->
            ListItem(
                modifier = Modifier.clickable { onPick(node) },
                leadingContent = { NodeChip(node = node) },
                headlineContent = {
                    Text(
                        node.effectiveLongName.ifEmpty { node.user.id },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                supportingContent = { Text(formatAgo(node.lastHeard)) },
            )
        }
    }
}
