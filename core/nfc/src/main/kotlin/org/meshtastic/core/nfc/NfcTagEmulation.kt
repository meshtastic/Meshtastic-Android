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

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

/**
 * The share URL this phone is currently offering to NFC readers, as an encoded NDEF message.
 *
 * Card emulation is served by the system, in a service this app does not own the lifetime of, so the armed payload
 * lives here rather than in the service. Nothing is offered unless a share surface has armed it: a reader that taps an
 * unarmed phone gets no tag at all. Channel URLs carry channel PSKs, so "armed only while the share dialog is open" is
 * the privacy boundary, the same exposure as the QR code being on screen.
 */
object NfcTagEmulation {

    @Volatile private var armed: ByteArray? = null

    /** The encoded NDEF message to serve, or null when nothing is armed. */
    fun armedNdefMessage(): ByteArray? = armed

    fun arm(url: String) {
        armed = NdefMessage(NdefRecord.createUri(url)).toByteArray()
    }

    fun disarm() {
        armed = null
    }
}

/**
 * Offers [url] to NFC readers for as long as this effect stays composed.
 *
 * The effect does not police the foreground itself. Callers compose it only while the share surface is actually on
 * screen and resumed; `MainActivity` holds that gate, because a dialog left open behind a home-press stays composed and
 * arming through that would offer a channel PSK to any reader while the phone sits in a pocket.
 */
@Composable
fun NfcEmulatorEffect(url: String) {
    DisposableEffect(url) {
        NfcTagEmulation.arm(url)
        onDispose { NfcTagEmulation.disarm() }
    }
}
