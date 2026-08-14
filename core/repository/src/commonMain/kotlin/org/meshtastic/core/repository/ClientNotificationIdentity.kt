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
package org.meshtastic.core.repository

import org.meshtastic.proto.ClientNotification

/**
 * Stable platform-notification identity shared by posting and cancellation paths.
 *
 * Deliberately excludes `reply_id` and `time`: both change on every firmware reply, so hashing the full notification
 * (e.g. via `toString()`) mints a fresh id per occurrence and defeats coalescing — a repeated advisory would stack up
 * as a new tray entry every time instead of updating the same one.
 *
 * Hashes only strings and the enum's wire value, never the enum object: enum hashCodes are identity-based, and the tray
 * outlives the process — a repost after a service restart must land on the same id.
 */
fun ClientNotification.notificationId(): Int = listOf(message, level.value, payloadVariantKey()).hashCode()

private fun ClientNotification.payloadVariantKey(): String = when {
    key_verification_number_inform != null -> "key_verification_number_inform"
    key_verification_number_request != null -> "key_verification_number_request"
    key_verification_final != null -> "key_verification_final"
    duplicated_public_key != null -> "duplicated_public_key"
    low_entropy_key != null -> "low_entropy_key"
    else -> "generic"
}
