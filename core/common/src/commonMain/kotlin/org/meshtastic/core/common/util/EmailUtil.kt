/*
 * Copyright (c) 2026 Meshtastic LLC
 */
package org.meshtastic.core.common.util

expect fun sendEmailIntent(recipient: String, subject: String, content: String)
