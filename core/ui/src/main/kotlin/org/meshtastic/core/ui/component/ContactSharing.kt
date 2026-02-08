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
@file:Suppress("detekt:ALL")

package org.meshtastic.core.ui.component

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.compose.runtime.Composable
import co.touchlab.kermit.Logger
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.model.util.getSharedContactUrl
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.share_contact
import org.meshtastic.proto.SharedContact

/**
 * Displays a dialog with the contact's information as a QR code and URI.
 *
 * @param contact The node representing the contact to share. Null if no contact is selected.
 * @param onDismiss Callback invoked when the dialog is dismissed.
 */
@Composable
fun SharedContactDialog(contact: Node?, onDismiss: () -> Unit) {
    if (contact == null) return
    val contactToShare = SharedContact(user = contact.user, node_num = contact.num)
    val uri = contactToShare.getSharedContactUrl()
    QrDialog(title = stringResource(Res.string.share_contact), uri = uri, qrCode = uri.qrCode, onDismiss = onDismiss)
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

/** Bitmap representation of the Uri as a QR code, or null if generation fails. */
@Suppress("detekt:MagicNumber")
val Uri.qrCode: Bitmap?
    get() =
        try {
            val multiFormatWriter = MultiFormatWriter()
            val bitMatrix = multiFormatWriter.encode(this.toString(), BarcodeFormat.QR_CODE, 960, 960)
            bitMatrix.toBitmap()
        } catch (ex: WriterException) {
            Logger.e { "URL was too complex to render as barcode: ${ex.message}" }
            null
        }

@Suppress("detekt:MagicNumber")
private fun BitMatrix.toBitmap(): Bitmap {
    val width = width
    val height = height
    val pixels = IntArray(width * height)
    for (y in 0 until height) {
        val offset = y * width
        for (x in 0 until width) {
            pixels[offset + x] = if (get(x, y)) Color.BLACK else Color.WHITE
        }
    }
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    return bitmap
}
