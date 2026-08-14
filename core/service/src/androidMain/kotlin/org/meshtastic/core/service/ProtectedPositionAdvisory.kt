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
package org.meshtastic.core.service

import org.meshtastic.core.model.util.isOtaStatusNotification
import org.meshtastic.proto.ClientNotification
import org.meshtastic.proto.LogRecord

internal const val PROTECTED_POSITION_ADVISORY_MESSAGE = "Location sharing is disabled on this channel"

/**
 * Wire currently generates each ClientNotification oneof member as a nullable property rather than exposing a sealed
 * discriminator. Keep this complete gate aligned with the five payload variants in the pinned protobuf schema.
 */
internal fun ClientNotification.isProtectedPositionAdvisory(): Boolean =
    message == PROTECTED_POSITION_ADVISORY_MESSAGE &&
        level == LogRecord.Level.WARNING &&
        reply_id?.let { it != 0 } == true &&
        key_verification_number_inform == null &&
        key_verification_number_request == null &&
        key_verification_final == null &&
        duplicated_public_key == null &&
        low_entropy_key == null &&
        !isOtaStatusNotification()
