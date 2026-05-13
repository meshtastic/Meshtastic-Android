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
@file:Suppress("TooManyFunctions", "SwallowedException", "TooGenericExceptionCaught")

package org.meshtastic.core.model.util

import okio.ByteString
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.common.util.CommonUri
import org.meshtastic.proto.SharedContact
import org.meshtastic.proto.User

/**
 * Return a [SharedContact] that represents the contact encoded by the URL.
 *
 * @throws MalformedMeshtasticUrlException when not recognized as a valid Meshtastic URL
 */
@Throws(MalformedMeshtasticUrlException::class)
fun CommonUri.toSharedContact(): SharedContact {
    checkSharedContactUrl()
    val data = fragment!!.substringBefore('?')
    return decodeSharedContactData(data)
}

@Throws(MalformedMeshtasticUrlException::class)
private fun CommonUri.checkSharedContactUrl() {
    val h = host?.lowercase() ?: ""
    val isCorrectHost = h == MESHTASTIC_HOST || h == "www.$MESHTASTIC_HOST"
    val segments = pathSegments
    val isCorrectPath = segments.any { it.equals("v", ignoreCase = true) }

    val frag = fragment
    if (frag.isNullOrBlank() || !isCorrectHost || !isCorrectPath) {
        throw MalformedMeshtasticUrlException(
            "Not a valid Meshtastic URL: host=$h, segments=$segments, hasFragment=${!frag.isNullOrBlank()}",
        )
    }
}

@Suppress("ThrowsCount")
@Throws(MalformedMeshtasticUrlException::class)
private fun decodeSharedContactData(data: String): SharedContact {
    val decodedBytes =
        try {
            // We use a more lenient decoding for the input to handle variations from different clients
            val sanitized = data.replace('-', '+').replace('_', '/')
            sanitized.decodeBase64() ?: throw IllegalArgumentException("Invalid Base64 string")
        } catch (e: IllegalArgumentException) {
            throw MalformedMeshtasticUrlException(
                "Failed to Base64 decode SharedContact data ($data): ${e::class.simpleName}: ${e.message}",
            )
        }

    return try {
        SharedContact.ADAPTER.decode(decodedBytes)
    } catch (e: Exception) {
        throw MalformedMeshtasticUrlException(
            "Failed to proto decode SharedContact: ${e::class.simpleName}: ${e.message}",
        )
    }
}

/** Converts a [SharedContact] to its corresponding URI representation. */
fun SharedContact.getSharedContactUrl(): CommonUri {
    val bytes = SharedContact.ADAPTER.encode(this)
    val enc = bytes.toByteString().base64Url()
    return CommonUri.parse("$CONTACT_URL_PREFIX$enc")
}

/** Compares two [User] objects and returns a string detailing the differences. */
fun compareUsers(oldUser: User, newUser: User): String {
    val changes = mutableListOf<String>()

    if (oldUser.id != newUser.id) changes.add("id: ${oldUser.id} -> ${newUser.id}")
    if (oldUser.long_name != newUser.long_name) changes.add("long_name: ${oldUser.long_name} -> ${newUser.long_name}")
    if (oldUser.short_name != newUser.short_name) {
        changes.add("short_name: ${oldUser.short_name} -> ${newUser.short_name}")
    }
    @Suppress("DEPRECATION")
    if (oldUser.macaddr != newUser.macaddr) {
        changes.add("macaddr: ${oldUser.macaddr.base64String()} -> ${newUser.macaddr.base64String()}")
    }
    if (oldUser.hw_model != newUser.hw_model) changes.add("hw_model: ${oldUser.hw_model} -> ${newUser.hw_model}")
    if (oldUser.is_licensed != newUser.is_licensed) {
        changes.add("is_licensed: ${oldUser.is_licensed} -> ${newUser.is_licensed}")
    }
    if (oldUser.role != newUser.role) changes.add("role: ${oldUser.role} -> ${newUser.role}")
    if (oldUser.public_key != newUser.public_key) {
        changes.add("public_key: ${oldUser.public_key.base64String()} -> ${newUser.public_key.base64String()}")
    }

    return if (changes.isEmpty()) {
        "No changes detected."
    } else {
        "Changes:\n${changes.joinToString("\n")}"
    }
}

/** Converts a [User] object to a string representation of its fields and values. */
fun userFieldsToString(user: User): String {
    val fieldLines = mutableListOf<String>()

    fieldLines.add("id: ${user.id}")
    fieldLines.add("long_name: ${user.long_name}")
    fieldLines.add("short_name: ${user.short_name}")
    @Suppress("DEPRECATION")
    fieldLines.add("macaddr: ${user.macaddr.base64String()}")
    fieldLines.add("hw_model: ${user.hw_model}")
    fieldLines.add("is_licensed: ${user.is_licensed}")
    fieldLines.add("role: ${user.role}")
    fieldLines.add("public_key: ${user.public_key.base64String()}")

    return fieldLines.joinToString("\n")
}

private fun ByteString.base64String(): String = base64()
