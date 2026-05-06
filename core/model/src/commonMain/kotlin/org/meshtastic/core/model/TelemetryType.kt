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
package org.meshtastic.core.model

/**
 * Typed enum for telemetry request categories.
 *
 * Ordinal values align with SDK telemetry dispatch ordering:
 * 0=Device, 1=Environment, 2=AirQuality, 3=Power, 4=LocalStats, 5=Health, 6=Host, 7=TrafficManagement.
 */
enum class TelemetryType {
    DEVICE,
    ENVIRONMENT,
    AIR_QUALITY,
    POWER,
    LOCAL_STATS,
    HEALTH,
    HOST,
    TRAFFIC_MANAGEMENT,
}
