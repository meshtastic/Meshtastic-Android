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
package org.meshtastic.feature.messaging.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.common.util.DateFormatter
import org.meshtastic.core.common.util.RelativeDay
import org.meshtastic.core.common.util.relativeDayOf
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.date_separator_today
import org.meshtastic.core.resources.date_separator_yesterday
import org.meshtastic.core.ui.theme.AppTheme

/**
 * Day boundary marker between message runs — "Today", "Yesterday", or the localized date.
 *
 * Scrolling back through a channel otherwise runs Tuesday into last month with nothing between them but a clock time,
 * because the per-message timestamp carries no date until full timestamps are switched on.
 */
@Composable
internal fun DateSeparator(timestampMillis: Long, modifier: Modifier = Modifier) {
    val label =
        when (relativeDayOf(timestampMillis)) {
            RelativeDay.Today -> stringResource(Res.string.date_separator_today)
            RelativeDay.Yesterday -> stringResource(Res.string.date_separator_yesterday)
            RelativeDay.Older -> DateFormatter.formatDate(timestampMillis)
        }
    Box(modifier = modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        // A pill rather than the unread divider's rules, so the two never read as the same marker when both land
        // between the same pair of messages.
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant) {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun DateSeparatorPreview() {
    AppTheme { DateSeparator(timestampMillis = 0L) }
}
