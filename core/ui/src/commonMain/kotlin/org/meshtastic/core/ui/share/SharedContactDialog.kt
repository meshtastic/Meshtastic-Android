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
package org.meshtastic.core.ui.share

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.util.compareUsers
import org.meshtastic.core.model.util.userFieldsToString
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.cancel
import org.meshtastic.core.resources.import_known_shared_contact_text
import org.meshtastic.core.resources.import_label
import org.meshtastic.core.resources.import_shared_contact
import org.meshtastic.core.resources.public_key_changed
import org.meshtastic.core.ui.component.MeshtasticDialog
import org.meshtastic.core.ui.component.NodeChip
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.proto.SharedContact
import org.meshtastic.proto.User

/** A dialog for importing a shared contact that was scanned from a QR code. */
@Composable
fun SharedContactDialog(
    sharedContact: SharedContact,
    onDismiss: () -> Unit,
    viewModel: SharedContactViewModel = koinViewModel(),
) {
    val unfilteredNodes by viewModel.unfilteredNodes.collectAsStateWithLifecycle()

    val node = unfilteredNodes.find { it.num == sharedContact.node_num }

    SharedContactDialogContent(
        sharedContact = sharedContact,
        node = node,
        onDismiss = onDismiss,
        onImport = {
            viewModel.addSharedContact(sharedContact)
            onDismiss()
        },
    )
}

/**
 * Stateless content of [SharedContactDialog]. [node] is the matching node already in the local database, or null when
 * the contact is unknown.
 */
@Composable
fun SharedContactDialogContent(
    sharedContact: SharedContact,
    node: Node?,
    onDismiss: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MeshtasticDialog(
        modifier = modifier,
        titleRes = Res.string.import_shared_contact,
        text = {
            Column {
                // Node identity badge: the same node identity chip used across the app (Design Standards §1).
                val chipNode = node ?: Node(num = sharedContact.node_num, user = sharedContact.user ?: User())
                Box(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), contentAlignment = Alignment.Center) {
                    NodeChip(node = chipNode)
                }
                if (node != null) {
                    Text(text = stringResource(Res.string.import_known_shared_contact_text))
                    if ((node.user.public_key.size) > 0 && node.user.public_key != sharedContact.user?.public_key) {
                        Text(
                            text = stringResource(Res.string.public_key_changed),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    HorizontalDivider()
                    Text(text = compareUsers(node.user, sharedContact.user ?: User()))
                } else {
                    Text(text = userFieldsToString(sharedContact.user ?: User()))
                }
            }
        },
        dismissText = stringResource(Res.string.cancel),
        onDismiss = onDismiss,
        confirmText = stringResource(Res.string.import_label),
        onConfirm = onImport,
    )
}

// Public because the screenshot-tests module renders this preview as a golden test.
@Suppress("MagicNumber", "PreviewPublic")
@Preview(showBackground = true, name = "Shared Contact Import Alert")
@Composable
fun PreviewSharedContactImportAlert() {
    AppTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            SharedContactDialogContent(
                sharedContact =
                SharedContact(
                    node_num = 13444,
                    user = User(id = "!00003484", long_name = "John Doe", short_name = "JD"),
                ),
                node = null,
                onDismiss = {},
                onImport = {},
            )
        }
    }
}
