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
package org.meshtastic.feature.connections

/**
 * Sentinel value for "no device selected" that mirrors the one-char prefix used by every real
 * [org.meshtastic.feature.connections.model.DeviceListEntry]'s `fullAddress`. Persisted by `radioInterfaceService` so
 * the UI layer can distinguish "user explicitly disconnected" from "first launch".
 */
const val NO_DEVICE_SELECTED: String = "n"

/** One-char prefix marking a TCP/Network device's `fullAddress`. See [DeviceListEntry.Tcp]. */
const val TCP_DEVICE_PREFIX: String = "t"

/** One-char prefix / sentinel marking the mock (demo-mode) device's `fullAddress`. See [DeviceListEntry.Mock]. */
const val MOCK_DEVICE_PREFIX: String = "m"

/** One-char prefix marking a BLE device's `fullAddress`. See [DeviceListEntry.Ble]. */
const val BLE_DEVICE_PREFIX: String = "x"
