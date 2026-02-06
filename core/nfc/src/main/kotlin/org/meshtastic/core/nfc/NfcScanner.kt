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
package org.meshtastic.core.nfc

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import co.touchlab.kermit.Logger
import java.io.IOException

@Composable
fun NfcScannerEffect(onResult: (String?) -> Unit, onNfcDisabled: (() -> Unit)? = null) {
    val context = LocalContext.current
    val activity = context as? Activity ?: return

    val nfcAdapter = remember { NfcAdapter.getDefaultAdapter(context) }

    DisposableEffect(nfcAdapter) {
        if (nfcAdapter == null) {
            onDispose {}
        } else if (!nfcAdapter.isEnabled) {
            onNfcDisabled?.invoke()
            onDispose {}
        } else {
            val readerCallback = NfcAdapter.ReaderCallback { tag: Tag -> handleNfcTag(tag, onResult) }

            val flags =
                (
                    NfcAdapter.FLAG_READER_NFC_A or
                        NfcAdapter.FLAG_READER_NFC_B or
                        NfcAdapter.FLAG_READER_NFC_F or
                        NfcAdapter.FLAG_READER_NFC_V or
                        NfcAdapter.FLAG_READER_NFC_BARCODE
                    )

            nfcAdapter.enableReaderMode(activity, readerCallback, flags, null)

            onDispose { nfcAdapter.disableReaderMode(activity) }
        }
    }
}

private fun handleNfcTag(tag: Tag, onResult: (String?) -> Unit) {
    val ndef = Ndef.get(tag) ?: return
    try {
        ndef.connect()
        val ndefMessage = ndef.ndefMessage ?: return
        for (record in ndefMessage.records) {
            val payload = record.toUri()?.toString()
            if (payload != null) {
                onResult(payload)
                break
            }
        }
    } catch (e: IOException) {
        Logger.w(e) { "Error reading NDEF tag" }
    } finally {
        try {
            ndef.close()
        } catch (e: IOException) {
            Logger.w(e) { "Error closing NDEF" }
        }
    }
}
