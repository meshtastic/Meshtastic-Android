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
package org.meshtastic.feature.settings.radio.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.meshtastic.core.network.mqtt.LatLon
import org.meshtastic.core.network.mqtt.MqttTopicSampler
import org.meshtastic.core.network.mqtt.TopicPlan
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.mqtt_topic_finder
import org.meshtastic.core.resources.mqtt_topic_finder_active
import org.meshtastic.core.resources.mqtt_topic_finder_connecting
import org.meshtastic.core.resources.mqtt_topic_finder_counts
import org.meshtastic.core.resources.mqtt_topic_finder_finished
import org.meshtastic.core.resources.mqtt_topic_finder_footer
import org.meshtastic.core.resources.mqtt_topic_finder_geocode_failed
import org.meshtastic.core.resources.mqtt_topic_finder_listening
import org.meshtastic.core.resources.mqtt_topic_finder_near_you
import org.meshtastic.core.resources.mqtt_topic_finder_no_location
import org.meshtastic.core.resources.mqtt_topic_finder_no_region
import org.meshtastic.core.resources.mqtt_topic_finder_nothing_heard
import org.meshtastic.core.resources.mqtt_topic_finder_nothing_yet
import org.meshtastic.core.resources.mqtt_topic_finder_only_above
import org.meshtastic.core.resources.mqtt_topic_finder_sample_again
import org.meshtastic.core.resources.mqtt_topic_finder_subscribing
import org.meshtastic.proto.ModuleConfig

private const val MAX_ACTIVE_ROWS = 30
private const val DEFAULT_BROKER = "mqtt.meshtastic.org"

/**
 * Listens to the broker for a minute and lists the root topics heard near the user: the geocoded ladder and community
 * roots with live node counts, then every other root ranked by how near it is likely to be, then a typed one. Picking a
 * row hands the topic back; nothing reaches the radio until the form is saved.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MqttTopicFinderSheet(
    /** Changes each time the sheet is opened, so a reopened sheet samples again. */
    session: Int,
    config: ModuleConfig.MQTTConfig,
    regionName: String?,
    radioPosition: LatLon?,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val viewModel = koinViewModel<MqttTopicFinderViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    SideEffect { viewModel.open(session, config, regionName, radioPosition) }
    val pick: (String) -> Unit = { topic ->
        viewModel.stop()
        onPick(topic)
    }
    val dismiss = {
        viewModel.stop()
        onDismiss()
    }
    val sheetState =
        rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        )
    ModalBottomSheet(onDismissRequest = dismiss, sheetState = sheetState) {
        MqttTopicFinderContent(
            state = state,
            host = config.address.ifEmpty { DEFAULT_BROKER },
            current = config.root,
            onPick = pick,
            onRestart = { viewModel.start(config, regionName, radioPosition) },
        )
    }
}

@Composable
internal fun MqttTopicFinderContent(
    state: MqttTopicFinderViewModel.UiState,
    host: String,
    current: String,
    onPick: (String) -> Unit,
    onRestart: () -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item {
            Text(
                text = stringResource(Res.string.mqtt_topic_finder),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        item { StatusBlock(state = state, host = host, onRestart = onRestart) }
        val plan = state.plan
        if (state.regionName != null && plan != null) {
            suggestionRows(plan, state, current, onPick)
            activeRows(plan, state, current, onPick)
        }
        item { CustomTopic(onPick) }
    }
}

@Composable
private fun StatusBlock(state: MqttTopicFinderViewModel.UiState, host: String, onRestart: () -> Unit) {
    val sample = state.sample
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (state.regionName == null) {
            Text(
                stringResource(Res.string.mqtt_topic_finder_no_region),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            when (val phase = sample.phase) {
                MqttTopicSampler.Phase.Idle -> Unit

                MqttTopicSampler.Phase.Connecting -> Busy(stringResource(Res.string.mqtt_topic_finder_connecting))

                MqttTopicSampler.Phase.Subscribing -> Busy(stringResource(Res.string.mqtt_topic_finder_subscribing))

                is MqttTopicSampler.Phase.Sampling -> {
                    val total = MqttTopicSampler.DEFAULT_DURATION.inWholeSeconds.toFloat()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(Res.string.mqtt_topic_finder_listening), modifier = Modifier.weight(1f))
                        Text(clock(phase.secondsRemaining), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    LinearProgressIndicator(
                        progress = { (1f - phase.secondsRemaining / total).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                MqttTopicSampler.Phase.Finished -> {
                    val topics = sample.activity.size
                    Text(stringResource(Res.string.mqtt_topic_finder_finished, sample.messageCount, topics))
                    SampleAgain(onRestart)
                }

                is MqttTopicSampler.Phase.Failed -> {
                    Text(stringResource(phase.failure.message()), color = MaterialTheme.colorScheme.error)
                    SampleAgain(onRestart)
                }
            }
        }
        Text(
            text = stringResource(Res.string.mqtt_topic_finder_footer, host),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val SECONDS_PER_MINUTE = 60

private fun clock(seconds: Int): String =
    "${seconds / SECONDS_PER_MINUTE}:${(seconds % SECONDS_PER_MINUTE).toString().padStart(2, '0')}"

@Composable
private fun SampleAgain(onRestart: () -> Unit) {
    TextButton(onClick = onRestart) { Text(stringResource(Res.string.mqtt_topic_finder_sample_again)) }
}

@Composable
private fun Busy(label: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label)
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
}

private fun LazyListScope.suggestionRows(
    plan: TopicPlan,
    state: MqttTopicFinderViewModel.UiState,
    current: String,
    onPick: (String) -> Unit,
) {
    item { SectionHeader(Res.string.mqtt_topic_finder_near_you) }
    items(plan.suggestions, key = { "suggestion:${it.topic}" }) { suggestion ->
        val live = TopicPlan.activityFor(suggestion.topic, state.sample.activity)
        val topic = live?.first ?: suggestion.topic
        TopicRow(
            topic = topic,
            title = suggestion.placeName,
            subtitle = stringResource(suggestion.kind.label()),
            activity = live?.second,
            isCurrent = topic == current,
            onClick = { onPick(topic) },
        )
    }
    item {
        val footer =
            when {
                state.location == null && !state.locating -> Res.string.mqtt_topic_finder_no_location
                state.geocodeFailed -> Res.string.mqtt_topic_finder_geocode_failed
                else -> Res.string.mqtt_topic_finder_counts
            }
        SectionFooter(stringResource(footer))
    }
}

private fun LazyListScope.activeRows(
    plan: TopicPlan,
    state: MqttTopicFinderViewModel.UiState,
    current: String,
    onPick: (String) -> Unit,
) {
    val ranked = plan.ranked(state.sample.activity, state.location).take(MAX_ACTIVE_ROWS)
    item { SectionHeader(Res.string.mqtt_topic_finder_active) }
    if (ranked.isEmpty()) {
        item {
            val text =
                when {
                    state.sample.phase == MqttTopicSampler.Phase.Finished && state.sample.activity.isEmpty() ->
                        Res.string.mqtt_topic_finder_nothing_heard

                    state.sample.phase == MqttTopicSampler.Phase.Finished -> Res.string.mqtt_topic_finder_only_above

                    state.sample.isRunning -> Res.string.mqtt_topic_finder_nothing_yet

                    else -> null
                }
            text?.let { SectionFooter(stringResource(it)) }
        }
    } else {
        items(ranked, key = { "root:${it.root}" }) { root ->
            TopicRow(
                topic = root.root,
                title = null,
                subtitle = subtitle(root),
                activity = root.activity,
                isCurrent = root.root == current,
                onClick = { onPick(root.root) },
            )
        }
    }
}
