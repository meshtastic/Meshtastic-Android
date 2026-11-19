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
package org.meshtastic.core.ble

/**
 * Classification of a BLE-layer exception for the transport layer to act on.
 *
 * @property isPermanent `true` if the condition cannot resolve without explicit user re-selection of the device.
 *   Currently always `false` — all known BLE exceptions can resolve without user intervention (BT toggling, permission
 *   grants, transient GATT errors). Reserved for future use.
 * @property gattStatus the platform GATT status code when available (Android-specific).
 * @property message a human-readable description of the failure.
 */
data class BleExceptionInfo(val isPermanent: Boolean, val gattStatus: Int? = null, val message: String)
