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
package org.meshtastic.core.nfc

import android.app.Activity
import android.nfc.FormatException
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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

/**
 * Writes [url] (a Meshtastic shared-contact or channel-set URL) to the next NDEF tag tapped against the phone. Mirrors
 * [NfcScannerEffect]: keep it composed while a write is armed, dispose to disarm. [onResult] reports write success.
 */
@Composable
fun NfcWriterEffect(url: String, onResult: (Boolean) -> Unit, onNfcDisabled: (() -> Unit)? = null) {
    val context = LocalContext.current
    val activity = context as? Activity ?: return

    val nfcAdapter = remember { NfcAdapter.getDefaultAdapter(context) }
    val currentOnResult by rememberUpdatedState(onResult)
    val currentOnNfcDisabled by rememberUpdatedState(onNfcDisabled)

    DisposableEffect(nfcAdapter, url) {
        if (nfcAdapter == null) {
            // No NFC hardware: report failure so the armed write UI doesn't hang waiting for a tap.
            currentOnResult(false)
            onDispose {}
        } else if (!nfcAdapter.isEnabled) {
            currentOnNfcDisabled?.invoke()
            onDispose {}
        } else {
            val readerCallback = NfcAdapter.ReaderCallback { tag: Tag -> currentOnResult(writeNdefUrl(tag, url)) }

            val flags =
                (
                    NfcAdapter.FLAG_READER_NFC_A or
                        NfcAdapter.FLAG_READER_NFC_B or
                        NfcAdapter.FLAG_READER_NFC_F or
                        NfcAdapter.FLAG_READER_NFC_V
                    )

            nfcAdapter.enableReaderMode(activity, readerCallback, flags, null)

            onDispose { nfcAdapter.disableReaderMode(activity) }
        }
    }
}

private fun writeNdefUrl(tag: Tag, url: String): Boolean {
    val ndef = Ndef.get(tag)
    if (ndef == null) {
        Logger.w { "Tag does not support NDEF" }
        return false
    }
    val message = NdefMessage(NdefRecord.createUri(url))
    return try {
        ndef.connect()
        when {
            !ndef.isWritable -> {
                Logger.w { "NDEF tag is read-only" }
                false
            }

            ndef.maxSize < message.byteArrayLength -> {
                Logger.w { "NDEF tag too small: ${ndef.maxSize} < ${message.byteArrayLength}" }
                false
            }

            else -> {
                ndef.writeNdefMessage(message)
                true
            }
        }
    } catch (e: IOException) {
        Logger.w(e) { "Error writing NDEF tag" }
        false
    } catch (e: FormatException) {
        Logger.w(e) { "Malformed NDEF message" }
        false
    } finally {
        try {
            ndef.close()
        } catch (e: IOException) {
            Logger.w(e) { "Error closing NDEF" }
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
