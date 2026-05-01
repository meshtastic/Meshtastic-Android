/*
 * Copyright (c) 2026 Meshtastic LLC
 */
package org.meshtastic.core.common.util

import android.content.Intent
import org.meshtastic.core.common.ContextServices

actual fun sendEmailIntent(recipient: String, subject: String, content: String) {
    val context = ContextServices.app
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "message/rfc822"
        putExtra(Intent.EXTRA_EMAIL, arrayOf(recipient))
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, content)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(Intent.createChooser(intent, "Kies een Email App").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    })
}
