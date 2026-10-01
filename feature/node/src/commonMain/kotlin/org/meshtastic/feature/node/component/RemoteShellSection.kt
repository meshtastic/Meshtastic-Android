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
package org.meshtastic.feature.node.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.SessionStatus
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.establishing_session
import org.meshtastic.core.resources.ic_terminal
import org.meshtastic.core.resources.remote_shell
import org.meshtastic.core.resources.remote_shell_open
import org.meshtastic.core.resources.remote_shell_requires_session
import org.meshtastic.core.ui.component.BasicListItem
import org.meshtastic.feature.node.model.NodeDetailAction

/**
 * A shell session on its own, rather than buried under Administration.
 *
 * The node gates the shell on its `security.admin_key` list — the same list the remote-admin passkey exchange goes
 * through — so this needs the same session check. It runs that check itself instead of leaving the session to be
 * established as a side effect of visiting the remote-admin screen: the row stays enabled, tapping it establishes the
 * session if needed and then opens the terminal.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RemoteShellSection(
    node: Node,
    sessionStatus: SessionStatus,
    isEnsuringSession: Boolean,
    onAction: (NodeDetailAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!node.capabilities.supportsRemoteShell) return

    SectionCard(title = Res.string.remote_shell, modifier = modifier) {
        Column {
            BasicListItem(
                text = stringResource(Res.string.remote_shell_open),
                leadingIcon = vectorResource(Res.drawable.ic_terminal),
                supportingText =
                if (sessionStatus is SessionStatus.Active) {
                    null
                } else {
                    stringResource(Res.string.remote_shell_requires_session)
                },
                enabled = !isEnsuringSession,
                onClick = { onAction(NodeDetailAction.OpenRemoteShell(node.num)) },
            )
            AnimatedVisibility(visible = isEnsuringSession) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        text = stringResource(Res.string.establishing_session),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}
