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
@file:Suppress("detekt:ALL")

package org.meshtastic.core.ui.component

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.util.getSharedContactUrl
import org.meshtastic.core.model.util.toSharedContact
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.share_contact
import org.meshtastic.core.resources.share_contact_subject
import org.meshtastic.proto.SharedContact

/**
 * Displays a dialog with the contact's information as a QR code and URI.
 *
 * Sharing your own contact marks it manually verified (design#149 point 2): you hold your own radio's key, and a QR
 * shown in person is the in-person exchange. Relaying someone else's contact asserts nothing on their behalf.
 *
 * @param contact The node representing the contact to share. Null if no contact is selected.
 * @param isOwnContact True when [contact] is the connected radio.
 * @param onDismiss Callback invoked when the dialog is dismissed.
 */
@Composable
fun SharedContactDialog(contact: Node?, onDismiss: () -> Unit, isOwnContact: Boolean = false) {
    if (contact == null) return
    val contactToShare = contact.toSharedContact(isOwnContact)
    val uriString = contactToShare.getSharedContactUrl().toString()
    QrDialog(
        title = stringResource(Res.string.share_contact),
        uriString = uriString,
        onDismiss = onDismiss,
        subtitle = contact.user?.long_name,
        shareSubject = stringResource(Res.string.share_contact_subject),
    )
}

/**
 * Displays a dialog for importing a shared contact.
 *
 * @param sharedContact The [SharedContact] to import.
 * @param onDismiss Callback invoked when the dialog is dismissed.
 */
@Composable
fun SharedContactImportDialog(sharedContact: SharedContact, onDismiss: () -> Unit) {
    org.meshtastic.core.ui.share.SharedContactDialog(sharedContact = sharedContact, onDismiss = onDismiss)
}
