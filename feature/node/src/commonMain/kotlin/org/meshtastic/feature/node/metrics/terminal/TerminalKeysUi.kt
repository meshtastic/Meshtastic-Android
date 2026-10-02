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
package org.meshtastic.feature.node.metrics.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.remote_shell_key_alt
import org.meshtastic.core.resources.remote_shell_key_ctrl
import org.meshtastic.core.resources.remote_shell_key_down
import org.meshtastic.core.resources.remote_shell_key_end
import org.meshtastic.core.resources.remote_shell_key_escape
import org.meshtastic.core.resources.remote_shell_key_home
import org.meshtastic.core.resources.remote_shell_key_left
import org.meshtastic.core.resources.remote_shell_key_page_down
import org.meshtastic.core.resources.remote_shell_key_page_up
import org.meshtastic.core.resources.remote_shell_key_right
import org.meshtastic.core.resources.remote_shell_key_tab
import org.meshtastic.core.resources.remote_shell_key_up
import org.meshtastic.core.resources.remote_shell_modifier_locked
import org.meshtastic.core.resources.remote_shell_modifier_once
import org.meshtastic.core.ui.input.RemoteKey

private val KEY_HEIGHT = 48.dp
private val KEY_SPACING = 2.dp
private val KEY_LABEL_SIZE = 13.sp

private sealed interface ExtraKey {
    data class Special(val key: RemoteKey, val label: String, val description: StringResource) : ExtraKey

    data class Typed(val char: Char) : ExtraKey

    data object Ctrl : ExtraKey

    data object Alt : ExtraKey
}

/** Termux's default extra-keys layout: the two rows its users already have in their thumbs. */
private val EXTRA_KEY_ROWS: List<List<ExtraKey>> =
    listOf(
        listOf(
            ExtraKey.Special(RemoteKey.ESCAPE, "ESC", Res.string.remote_shell_key_escape),
            ExtraKey.Typed('/'),
            ExtraKey.Typed('-'),
            ExtraKey.Special(RemoteKey.HOME, "HOME", Res.string.remote_shell_key_home),
            ExtraKey.Special(RemoteKey.UP, "↑", Res.string.remote_shell_key_up),
            ExtraKey.Special(RemoteKey.END, "END", Res.string.remote_shell_key_end),
            ExtraKey.Special(RemoteKey.PAGE_UP, "PGUP", Res.string.remote_shell_key_page_up),
        ),
        listOf(
            ExtraKey.Special(RemoteKey.TAB, "TAB", Res.string.remote_shell_key_tab),
            ExtraKey.Ctrl,
            ExtraKey.Alt,
            ExtraKey.Special(RemoteKey.LEFT, "←", Res.string.remote_shell_key_left),
            ExtraKey.Special(RemoteKey.DOWN, "↓", Res.string.remote_shell_key_down),
            ExtraKey.Special(RemoteKey.RIGHT, "→", Res.string.remote_shell_key_right),
            ExtraKey.Special(RemoteKey.PAGE_DOWN, "PGDN", Res.string.remote_shell_key_page_down),
        ),
    )

/**
 * The extra-keys rows. CTRL and ALT are sticky: one tap applies to the next key, a second tap locks them, a third
 * releases.
 */
@Composable
internal fun ExtraKeysBar(
    modifiers: Modifiers,
    onKey: (RemoteKey) -> Unit,
    onChar: (Char) -> Unit,
    onToggleCtrl: () -> Unit,
    onToggleAlt: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = KEY_SPACING)) {
        EXTRA_KEY_ROWS.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEach { key ->
                    when (key) {
                        is ExtraKey.Special ->
                            KeyCap(label = key.label, description = stringResource(key.description)) { onKey(key.key) }

                        is ExtraKey.Typed ->
                            KeyCap(label = key.char.toString(), description = null) { onChar(key.char) }

                        ExtraKey.Ctrl ->
                            KeyCap(
                                label = "CTRL",
                                description = stringResource(Res.string.remote_shell_key_ctrl),
                                state = modifiers.ctrl,
                                onClick = onToggleCtrl,
                            )

                        ExtraKey.Alt ->
                            KeyCap(
                                label = "ALT",
                                description = stringResource(Res.string.remote_shell_key_alt),
                                state = modifiers.alt,
                                onClick = onToggleAlt,
                            )
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.KeyCap(label: String, description: String?, state: ModifierState? = null, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val (container, content) =
        when (state) {
            ModifierState.ONCE -> colors.primaryContainer to colors.onPrimaryContainer
            ModifierState.LOCKED -> colors.primary to colors.onPrimary
            else -> Color.Transparent to colors.primary
        }
    val stateText =
        when (state) {
            ModifierState.ONCE -> stringResource(Res.string.remote_shell_modifier_once)
            ModifierState.LOCKED -> stringResource(Res.string.remote_shell_modifier_locked)
            else -> null
        }
    Box(
        modifier =
        Modifier.weight(1f)
            .height(KEY_HEIGHT)
            .padding(KEY_SPACING)
            .background(container, MaterialTheme.shapes.small)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                description?.let { contentDescription = it }
                stateText?.let { stateDescription = it }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = content,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            fontSize = KEY_LABEL_SIZE,
            maxLines = 1,
        )
    }
}
