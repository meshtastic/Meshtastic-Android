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
package org.meshtastic.core.domain.usecase.settings

import org.meshtastic.core.model.getStringResFrom
import org.meshtastic.core.resources.UiText
import org.meshtastic.proto.Channel
import org.meshtastic.proto.DeviceConnectionStatus
import org.meshtastic.proto.DeviceMetadata
import org.meshtastic.proto.Routing
import org.meshtastic.proto.User
import org.meshtastic.sdk.AdminResult

/** The answer to one radio configuration request, as the settings screens consume it. */
sealed interface RadioResponseResult {
    data class Metadata(val metadata: DeviceMetadata) : RadioResponseResult

    data class ChannelResponse(val channel: Channel) : RadioResponseResult

    data class Owner(val user: User) : RadioResponseResult

    data class ConfigResponse(val config: org.meshtastic.proto.Config) : RadioResponseResult

    data class ModuleConfigResponse(val config: org.meshtastic.proto.ModuleConfig) : RadioResponseResult

    data class CannedMessages(val messages: String) : RadioResponseResult

    data class Ringtone(val ringtone: String) : RadioResponseResult

    data class ConnectionStatus(val status: DeviceConnectionStatus) : RadioResponseResult

    data class Error(val message: UiText, val routingError: Routing.Error? = null) : RadioResponseResult

    data object Success : RadioResponseResult
}

/**
 * Maps an SDK admin answer to a [RadioResponseResult]: [onSuccess] for a value, an [RadioResponseResult.Error] naming
 * the routing error for a refusal, and null for [AdminResult.Timeout] and [AdminResult.NodeUnreachable], which the
 * caller's own deadline and connection handling report.
 */
fun <T> AdminResult<T>.toRadioResponseResult(onSuccess: (T) -> RadioResponseResult): RadioResponseResult? =
    when (this) {
        is AdminResult.Success -> onSuccess(value)

        is AdminResult.Failed -> routingErrorResult(routingError)

        AdminResult.Unauthorized -> routingErrorResult(Routing.Error.ADMIN_PUBLIC_KEY_UNAUTHORIZED)

        AdminResult.SessionKeyExpired -> routingErrorResult(Routing.Error.ADMIN_BAD_SESSION_KEY)

        AdminResult.RateLimited -> routingErrorResult(Routing.Error.RATE_LIMIT_EXCEEDED)

        AdminResult.Timeout,
        AdminResult.NodeUnreachable,
        -> null
    }

private fun routingErrorResult(error: Routing.Error) =
    RadioResponseResult.Error(UiText.Resource(getStringResFrom(error.value)), error)
