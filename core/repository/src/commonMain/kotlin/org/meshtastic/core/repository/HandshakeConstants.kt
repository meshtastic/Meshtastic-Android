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
package org.meshtastic.core.repository

/**
 * Shared constants for the two-stage mesh handshake protocol.
 * - Stage 1 ([CONFIG_NONCE]): requests device config, module config, and channels.
 * - Stage 2 ([BATCH_NODE_INFO_NONCE], primary): requests the full node database with batched [NodeInfoBatch] delivery.
 * - Stage 2 ([NODE_INFO_NONCE], legacy): requests node info one-at-a-time; kept for firmware that pre-dates batching.
 *
 * Both [MeshConfigFlowManager] (consumer) and [MeshConnectionManager] (sender) reference these.
 */
object HandshakeConstants {
    /** Nonce sent in `want_config_id` to request config-only (Stage 1). */
    const val CONFIG_NONCE = 69420

    /** Nonce sent in `want_config_id` to request node info only — unbatched legacy (Stage 2). */
    const val NODE_INFO_NONCE = 69421

    // 69422 intentionally skipped — reserved for future use.

    /** Nonce sent in `want_config_id` to request node info only — batched (Stage 2, primary). */
    const val BATCH_NODE_INFO_NONCE = 69423
}
