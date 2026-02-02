/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.cancel
import org.meshtastic.core.strings.import_known_shared_contact_text
import org.meshtastic.core.strings.import_label
import org.meshtastic.core.strings.import_shared_contact
import org.meshtastic.core.strings.public_key_changed
import org.meshtastic.core.ui.component.SimpleAlertDialog
import org.meshtastic.core.ui.component.compareUsers
import org.meshtastic.core.ui.component.userFieldsToString
import org.meshtastic.proto.SharedContact
import org.meshtastic.proto.User

/** A dialog for importing a shared contact that was scanned from a QR code. */
@Composable
fun SharedContactDialog(
    sharedContact: SharedContact,
    onDismiss: () -> Unit,
    viewModel: SharedContactViewModel = hiltViewModel(),
) {
    val unfilteredNodes by viewModel.unfilteredNodes.collectAsStateWithLifecycle()

    val nodeNum = sharedContact.node_num
    val node = unfilteredNodes.find { it.num == nodeNum }

    SimpleAlertDialog(
        title = Res.string.import_shared_contact,
        text = {
            Column {
                if (node != null) {
                    Text(text = stringResource(Res.string.import_known_shared_contact_text))
                    if (
                        (node.user.public_key?.size ?: 0) > 0 && node.user.public_key != sharedContact.user?.public_key
                    ) {
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
        onConfirm = {
            viewModel.addSharedContact(sharedContact)
            onDismiss()
        },
    )
}
