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
package org.meshtastic.core.model.util

/** The base domain for all Meshtastic URIs. */
const val MESHTASTIC_HOST = "meshtastic.org"

/** Path segment for Shared Contact URIs. */
const val CONTACT_SHARE_PATH = "/v/"

/** Full prefix for Shared Contact URIs: https://meshtastic.org/v/# */
const val CONTACT_URL_PREFIX = "https://$MESHTASTIC_HOST$CONTACT_SHARE_PATH#"

/** Path segment for Channel Set URIs. */
const val CHANNEL_SHARE_PATH = "/e/"

/** Full prefix for Channel Set URIs: https://meshtastic.org/e/ */
const val CHANNEL_URL_PREFIX = "https://$MESHTASTIC_HOST$CHANNEL_SHARE_PATH"
