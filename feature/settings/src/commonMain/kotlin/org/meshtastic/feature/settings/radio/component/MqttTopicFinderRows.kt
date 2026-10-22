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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.meshtastic.core.common.util.LocaleUnitsProvider
import org.meshtastic.core.common.util.MeasurementSystem
import org.meshtastic.core.model.util.toDistanceString
import org.meshtastic.core.network.mqtt.MqttRootTopic
import org.meshtastic.core.network.mqtt.MqttTopicDiscovery
import org.meshtastic.core.network.mqtt.MqttTopicSampler
import org.meshtastic.core.network.mqtt.RankedRoot
import org.meshtastic.core.network.mqtt.TopicPlan
import org.meshtastic.core.network.mqtt.TopicSuggestion
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.mqtt_root_problem_empty
import org.meshtastic.core.resources.mqtt_root_problem_empty_segment
import org.meshtastic.core.resources.mqtt_root_problem_ends_with_version
import org.meshtastic.core.resources.mqtt_root_problem_leading_slash
import org.meshtastic.core.resources.mqtt_root_problem_non_ascii
import org.meshtastic.core.resources.mqtt_root_problem_too_long
import org.meshtastic.core.resources.mqtt_root_problem_trailing_slash
import org.meshtastic.core.resources.mqtt_root_problem_whitespace
import org.meshtastic.core.resources.mqtt_root_problem_wildcard
import org.meshtastic.core.resources.mqtt_topic_finder_custom
import org.meshtastic.core.resources.mqtt_topic_finder_custom_example
import org.meshtastic.core.resources.mqtt_topic_finder_custom_hint
import org.meshtastic.core.resources.mqtt_topic_finder_failed_closed
import org.meshtastic.core.resources.mqtt_topic_finder_failed_connect
import org.meshtastic.core.resources.mqtt_topic_finder_failed_error
import org.meshtastic.core.resources.mqtt_topic_finder_failed_filters
import org.meshtastic.core.resources.mqtt_topic_finder_level_city
import org.meshtastic.core.resources.mqtt_topic_finder_level_community
import org.meshtastic.core.resources.mqtt_topic_finder_level_country
import org.meshtastic.core.resources.mqtt_topic_finder_level_region
import org.meshtastic.core.resources.mqtt_topic_finder_level_subdivision
import org.meshtastic.core.resources.mqtt_topic_finder_nearby
import org.meshtastic.core.resources.mqtt_topic_finder_nearest
import org.meshtastic.core.resources.mqtt_topic_finder_nodes
import org.meshtastic.core.resources.mqtt_topic_finder_use
import org.meshtastic.core.ui.icon.Check
import org.meshtastic.core.ui.icon.MeshtasticIcons

private const val MAX_CHANNELS_SHOWN = 3

@Composable
internal fun subtitle(root: RankedRoot): String? {
    val parts = mutableListOf<String>()
    if (root.tier == TopicPlan.TIER_NEARBY) parts += stringResource(Res.string.mqtt_topic_finder_nearby)
    val channels = root.activity.channels.sorted()
    if (channels.isNotEmpty()) {
        parts +=
            channels.take(MAX_CHANNELS_SHOWN).joinToString(", ") + if (channels.size > MAX_CHANNELS_SHOWN) "…" else ""
    }
    root.distanceMeters?.let { meters ->
        val system =
            if (LocalInspectionMode.current) {
                // Previews and screenshot tests render with no Koin application.
                MeasurementSystem.METRIC
            } else {
                koinInject<LocaleUnitsProvider>().measurementSystem.collectAsStateWithLifecycle().value
            }
        parts += stringResource(Res.string.mqtt_topic_finder_nearest, meters.toInt().toDistanceString(system))
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

@Composable
internal fun TopicRow(
    topic: String,
    title: String?,
    subtitle: String?,
    activity: MqttTopicDiscovery.Activity?,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(topic, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
            title?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val count = activity?.nodeCount ?: 0
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (activity?.truncated == true) "$count+" else "$count",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color =
                if (count > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(Res.string.mqtt_topic_finder_nodes),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (isCurrent) {
            Icon(MeshtasticIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
internal fun CustomTopic(onPick: (String) -> Unit) {
    var custom by remember { mutableStateOf("") }
    val sanitized = MqttRootTopic.sanitized(custom)
    val problem = MqttRootTopic.problem(sanitized)
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionHeader(Res.string.mqtt_topic_finder_custom, padded = false)
        OutlinedTextField(
            value = custom,
            onValueChange = { custom = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = {
                Text(stringResource(Res.string.mqtt_topic_finder_custom_example), fontFamily = FontFamily.Monospace)
            },
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            isError = custom.isNotEmpty() && problem != null,
            supportingText = {
                Text(
                    if (custom.isNotEmpty() && problem != null) {
                        stringResource(problem.message())
                    } else {
                        stringResource(Res.string.mqtt_topic_finder_custom_hint)
                    },
                )
            },
            keyboardOptions =
            KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Ascii,
            ),
        )
        TextButton(onClick = { onPick(sanitized) }, enabled = custom.isNotEmpty() && problem == null) {
            Text(stringResource(Res.string.mqtt_topic_finder_use))
        }
    }
}

@Composable
internal fun SectionHeader(text: StringResource, padded: Boolean = true) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = if (padded) Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp) else Modifier,
    )
}

@Composable
internal fun SectionFooter(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

internal fun TopicSuggestion.Kind.label(): StringResource = when (this) {
    TopicSuggestion.Kind.REGION -> Res.string.mqtt_topic_finder_level_region
    TopicSuggestion.Kind.COUNTRY -> Res.string.mqtt_topic_finder_level_country
    TopicSuggestion.Kind.SUBDIVISION -> Res.string.mqtt_topic_finder_level_subdivision
    TopicSuggestion.Kind.CITY -> Res.string.mqtt_topic_finder_level_city
    TopicSuggestion.Kind.COMMUNITY -> Res.string.mqtt_topic_finder_level_community
}

internal fun MqttTopicSampler.Failure.message(): StringResource = when (this) {
    MqttTopicSampler.Failure.CONNECT -> Res.string.mqtt_topic_finder_failed_connect
    MqttTopicSampler.Failure.FILTERS_REFUSED -> Res.string.mqtt_topic_finder_failed_filters
    MqttTopicSampler.Failure.CLOSED -> Res.string.mqtt_topic_finder_failed_closed
    MqttTopicSampler.Failure.ERROR -> Res.string.mqtt_topic_finder_failed_error
}

internal fun MqttRootTopic.Problem.message(): StringResource = when (this) {
    MqttRootTopic.Problem.EMPTY -> Res.string.mqtt_root_problem_empty
    MqttRootTopic.Problem.WHITESPACE -> Res.string.mqtt_root_problem_whitespace
    MqttRootTopic.Problem.NON_ASCII -> Res.string.mqtt_root_problem_non_ascii
    MqttRootTopic.Problem.WILDCARD -> Res.string.mqtt_root_problem_wildcard
    MqttRootTopic.Problem.LEADING_SLASH -> Res.string.mqtt_root_problem_leading_slash
    MqttRootTopic.Problem.TRAILING_SLASH -> Res.string.mqtt_root_problem_trailing_slash
    MqttRootTopic.Problem.EMPTY_SEGMENT -> Res.string.mqtt_root_problem_empty_segment
    MqttRootTopic.Problem.ENDS_WITH_VERSION -> Res.string.mqtt_root_problem_ends_with_version
    MqttRootTopic.Problem.TOO_LONG -> Res.string.mqtt_root_problem_too_long
}
