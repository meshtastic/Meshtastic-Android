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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import okio.ByteString.Companion.toByteString
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.effectiveLongName
import org.meshtastic.core.model.matchesSearch
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.cancel
import org.meshtastic.core.resources.clear
import org.meshtastic.core.resources.doc_no_results
import org.meshtastic.core.resources.node_filter_placeholder
import org.meshtastic.core.resources.nodes_empty_searching_hint
import org.meshtastic.core.resources.nodes_empty_searching_title
import org.meshtastic.core.resources.remote_admin_node_no_public_key
import org.meshtastic.core.resources.send
import org.meshtastic.core.ui.component.EmptyState
import org.meshtastic.core.ui.component.MeshtasticDialog
import org.meshtastic.core.ui.component.NodeChip
import org.meshtastic.core.ui.icon.Close
import org.meshtastic.core.ui.icon.Favorite
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Nodes
import org.meshtastic.core.ui.icon.Search
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.core.ui.theme.StatusColors.StatusYellow
import org.meshtastic.core.ui.util.formatAgo
import org.meshtastic.feature.settings.radio.RemoteNodeDbAction
import org.meshtastic.proto.User

private const val DISABLED_NODE_ALPHA = 0.5f

/**
 * Filters and orders local NodeDB candidates for a remote NodeDB management [RemoteNodeDbAction].
 *
 * Excludes [destNum] (a node cannot add/favorite itself), filters by [query] using [Node.matchesSearch], and when
 * [requiresPublicKey] is true places nodes with a usable public key before keyless/mismatch rows. Within selectable
 * peers, our own node ([myNodeNum]) comes first, then local favorites, then most recently heard.
 */
internal fun sortRemoteNodeDbCandidates(
    candidates: Collection<Node>,
    destNum: Int?,
    myNodeNum: Int?,
    query: String,
    requiresPublicKey: Boolean,
): List<Node> = candidates
    .asSequence()
    .filter { node -> node.num != destNum && node.matchesSearch(query) }
    .sortedWith(
        compareByDescending<Node> { node ->
            !requiresPublicKey || (node.hasPKC && !node.mismatchKey)
        }
            .thenByDescending { node -> node.num == myNodeNum }
            .thenByDescending { node -> node.isFavorite }
            .thenByDescending { node -> node.lastHeard },
    )
    .toList()

/** Bottom-sheet node picker + confirmation dialog for remote NodeDB administration commands. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteNodePickerSheet(
    action: RemoteNodeDbAction,
    destNode: Node?,
    myNodeNum: Int?,
    candidates: List<Node>,
    onDismiss: () -> Unit,
    onConfirm: (Node) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable(action) { mutableStateOf("") }
    var pendingNode by remember { mutableStateOf<Node?>(null) }

    val destLabel = destNode?.effectiveLongName?.ifEmpty { destNode.user.id }.orEmpty()

    pendingNode?.let { picked ->
        val pickedLabel = picked.effectiveLongName.ifEmpty { picked.user.id }
        val confirmText = stringResource(action.confirmMessage, pickedLabel, destLabel)
        if (action.isDestructive) {
            WarningDialog(
                title = "${stringResource(action.title)}?",
                text = { Text(text = confirmText) },
                onDismiss = { pendingNode = null },
                onConfirm = {
                    pendingNode = null
                    onConfirm(picked)
                },
            )
        } else {
            MeshtasticDialog(
                title = "${stringResource(action.title)}?",
                message = confirmText,
                confirmText = stringResource(Res.string.send),
                dismissText = stringResource(Res.string.cancel),
                onDismiss = { pendingNode = null },
                onConfirm = {
                    pendingNode = null
                    onConfirm(picked)
                },
            )
        }
    }

    val sheetState =
        rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        )
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier, sheetState = sheetState) {
        RemoteNodePickerContent(
            action = action,
            destNum = destNode?.num,
            myNodeNum = myNodeNum,
            candidates = candidates,
            query = query,
            onQueryChange = { query = it },
            onSelectNode = { pendingNode = it },
        )
    }
}

/** Stateless content for [RemoteNodePickerSheet], factored out for Compose UI tests and previews. */
@Composable
internal fun RemoteNodePickerContent(
    action: RemoteNodeDbAction,
    destNum: Int?,
    myNodeNum: Int?,
    candidates: List<Node>,
    query: String,
    onQueryChange: (String) -> Unit,
    onSelectNode: (Node) -> Unit,
    modifier: Modifier = Modifier,
) {
    val filtered =
        remember(candidates, destNum, myNodeNum, query, action.requiresPublicKey) {
            sortRemoteNodeDbCandidates(
                candidates = candidates,
                destNum = destNum,
                myNodeNum = myNodeNum,
                query = query,
                requiresPublicKey = action.requiresPublicKey,
            )
        }

    Column(modifier = modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = stringResource(action.title),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(action.summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                placeholder = { Text(stringResource(Res.string.node_filter_placeholder)) },
                leadingIcon = { Icon(MeshtasticIcons.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(MeshtasticIcons.Close, contentDescription = stringResource(Res.string.clear))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
        }

        HorizontalDivider(modifier = Modifier.padding(top = 4.dp))

        when {
            filtered.isEmpty() && query.isNotBlank() ->
                EmptyState(
                    icon = MeshtasticIcons.Search,
                    title = stringResource(Res.string.doc_no_results),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                )

            filtered.isEmpty() ->
                EmptyState(
                    icon = MeshtasticIcons.Nodes,
                    title = stringResource(Res.string.nodes_empty_searching_title),
                    supportingText = stringResource(Res.string.nodes_empty_searching_hint),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                )

            else ->
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                    items(filtered, key = { it.num }) { node ->
                        RemoteNodeCandidateRow(
                            node = node,
                            requiresPublicKey = action.requiresPublicKey,
                            onClick = { onSelectNode(node) },
                        )
                    }
                }
        }
    }
}

@Composable
private fun RemoteNodeCandidateRow(node: Node, requiresPublicKey: Boolean, onClick: () -> Unit) {
    val selectable = !requiresPublicKey || (node.hasPKC && !node.mismatchKey)
    val label = node.effectiveLongName.ifEmpty { node.user.id }

    ListItem(
        modifier =
        Modifier.fillMaxWidth().let { mod ->
            if (selectable) mod.clickable(onClick = onClick) else mod.alpha(DISABLED_NODE_ALPHA)
        },
        leadingContent = { NodeChip(node = node) },
        headlineContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (node.isFavorite) {
                    Icon(
                        imageVector = MeshtasticIcons.Favorite,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.StatusYellow,
                    )
                }
            }
        },
        supportingContent = {
            if (selectable) {
                Text(text = formatAgo(node.lastHeard))
            } else {
                Text(
                    text = stringResource(Res.string.remote_admin_node_no_public_key),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        trailingContent = { Spacer(modifier = Modifier) },
    )
}

@Preview(showBackground = true)
@Composable
private fun RemoteNodePickerContentPreview() {
    val key = ByteArray(Node.PUBLIC_KEY_SIZE) { 1 }.toByteString()
    val nodes =
        listOf(
            Node(
                num = 1,
                isFavorite = true,
                user =
                User.Builder()
                    .also { wb ->
                        wb.id = "!00000001"
                        wb.long_name = "Local Base"
                        wb.short_name = "BASE"
                        wb.public_key = key
                    }
                    .build(),
            ),
            Node(
                num = 2,
                user =
                User.Builder()
                    .also { wb ->
                        wb.id = "!00000002"
                        wb.long_name = "Keyless Node"
                        wb.short_name = "NOKEY"
                    }
                    .build(),
            ),
        )
    AppTheme {
        RemoteNodePickerContent(
            action = RemoteNodeDbAction.ADD_AND_FAVORITE,
            destNum = 99,
            myNodeNum = 1,
            candidates = nodes,
            query = "",
            onQueryChange = {},
            onSelectNode = {},
        )
    }
}
