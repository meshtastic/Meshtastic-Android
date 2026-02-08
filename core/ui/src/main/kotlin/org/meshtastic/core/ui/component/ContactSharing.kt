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
import android.util.Base64
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.core.net.toUri
import co.touchlab.kermit.Logger
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.model.util.CONTACT_SHARE_PATH
import org.meshtastic.core.model.util.CONTACT_URL_PREFIX
import org.meshtastic.core.model.util.MESHTASTIC_HOST
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.share_contact
import org.meshtastic.proto.SharedContact
import org.meshtastic.proto.User
import java.net.MalformedURLException

/**
 * Composable FloatingActionButton to initiate scanning a QR code for adding a contact. Handles camera permission
 * requests using Accompanist Permissions.
 *
 * @param modifier Modifier for this composable.
 */
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun AddContactFAB(
    sharedContact: SharedContact?,
    modifier: Modifier = Modifier,
    onResult: (Uri) -> Unit,
    onShareChannels: (() -> Unit)? = null,
    onDismissSharedContact: () -> Unit,
    isContactContext: Boolean = true,
) {
    sharedContact?.let { SharedContactImportDialog(sharedContact = it, onDismiss = onDismissSharedContact) }

    ImportFab(
        onImport = onResult,
        modifier = modifier,
        onShareChannels = onShareChannels,
        isContactContext = isContactContext,
    )
}

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

private const val BASE64FLAGS = Base64.URL_SAFE + Base64.NO_WRAP + Base64.NO_PADDING

@Suppress("MagicNumber")
@Throws(MalformedURLException::class)
fun Uri.toSharedContact(): SharedContact {
    val h = host ?: ""
    val p = path ?: ""
    val isCorrectHost =
        h.equals(MESHTASTIC_HOST, ignoreCase = true) || h.equals("www.$MESHTASTIC_HOST", ignoreCase = true)
    val isCorrectPath = p.equals(CONTACT_SHARE_PATH, ignoreCase = true) || p.equals("/v", ignoreCase = true)

    if (fragment.isNullOrBlank() || !isCorrectHost || !isCorrectPath) {
        throw MalformedURLException("Not a valid Meshtastic URL: ${toString().take(40)}")
    }
    return SharedContact.ADAPTER.decode(Base64.decode(fragment!!, BASE64FLAGS).toByteString())
}

/** Converts a [SharedContact] to its corresponding URI representation. */
fun SharedContact.getSharedContactUrl(): Uri {
    val bytes = SharedContact.ADAPTER.encode(this)
    val enc = Base64.encodeToString(bytes, BASE64FLAGS)
    return "$CONTACT_URL_PREFIX$enc".toUri()
}

/** Compares two [User] objects and returns a string detailing the differences. */
fun compareUsers(oldUser: User, newUser: User): String {
    val changes = mutableListOf<String>()

    if (oldUser.id != newUser.id) changes.add("id: ${oldUser.id} -> ${newUser.id}")
    if (oldUser.long_name != newUser.long_name) changes.add("long_name: ${oldUser.long_name} -> ${newUser.long_name}")
    if (oldUser.short_name != newUser.short_name) {
        changes.add("short_name: ${oldUser.short_name} -> ${newUser.short_name}")
    }
    if (oldUser.macaddr != newUser.macaddr) {
        changes.add("macaddr: ${oldUser.macaddr?.base64String()} -> ${newUser.macaddr?.base64String()}")
    }
    if (oldUser.hw_model != newUser.hw_model) changes.add("hw_model: ${oldUser.hw_model} -> ${newUser.hw_model}")
    if (oldUser.is_licensed != newUser.is_licensed) {
        changes.add("is_licensed: ${oldUser.is_licensed} -> ${newUser.is_licensed}")
    }
    if (oldUser.role != newUser.role) changes.add("role: ${oldUser.role} -> ${newUser.role}")
    if (oldUser.public_key != newUser.public_key) {
        changes.add("public_key: ${oldUser.public_key?.base64String()} -> ${newUser.public_key?.base64String()}")
    }

    return if (changes.isEmpty()) {
        "No changes detected."
    } else {
        "Changes:\n" + changes.joinToString("\n")
    }
}

/** Converts a [User] object to a string representation of its fields and values. */
fun userFieldsToString(user: User): String {
    val fieldLines = mutableListOf<String>()

    fieldLines.add("id: ${user.id}")
    fieldLines.add("long_name: ${user.long_name}")
    fieldLines.add("short_name: ${user.short_name}")
    fieldLines.add("macaddr: ${user.macaddr?.base64String()}")
    fieldLines.add("hw_model: ${user.hw_model}")
    fieldLines.add("is_licensed: ${user.is_licensed}")
    fieldLines.add("role: ${user.role}")
    fieldLines.add("public_key: ${user.public_key?.base64String()}")

    return fieldLines.joinToString("\n")
}

private fun ByteString.base64String(): String = Base64.encodeToString(this.toByteArray(), Base64.DEFAULT).trim()
