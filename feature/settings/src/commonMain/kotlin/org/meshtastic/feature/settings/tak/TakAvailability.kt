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
package org.meshtastic.feature.settings.tak

/**
 * Whether TAK/ATAK integration (the TAK module config screen and the local TAK server) is available on this platform.
 * False only on wasmJs: `core:takserver`'s production implementation binds an inbound TLS `SSLServerSocket` listener,
 * which a browser sandbox can never accept -- a permanent impossibility, not a deferred feature (same reasoning as
 * `core:service`'s `NoopTakServerIntegration`).
 */
expect val isTakSupportedOnPlatform: Boolean
