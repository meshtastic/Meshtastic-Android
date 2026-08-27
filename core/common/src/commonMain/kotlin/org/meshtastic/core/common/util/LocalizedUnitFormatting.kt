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
package org.meshtastic.core.common.util

/**
 * CLDR-driven measure rendering: unit choice, conversion, digits, symbol, and spacing all come from the platform's ICU
 * data for the current locale, forced onto [system] via the `ms` Unicode keyword — the mechanism Android 16's
 * Measurement system setting itself uses. `usage("road")` is deliberately not used anywhere: CLDR road preferences snap
 * values (87 m renders as "90 m") and pick yards for the UK, which is wrong for GNSS-accuracy labels and surprising for
 * node distance.
 *
 * Each function takes the raw metric value the mesh transmits and returns null where the engine cannot answer — no ICU
 * units support on this platform or OS version, or a non-finite value — and the caller renders the hand-rolled fallback
 * instead, which keeps every unit the app ever shows identical across engine and fallback except for CLDR's own
 * precision choices.
 */
expect fun formatLengthLocalized(meters: Double, system: MeasurementSystem): String?

/**
 * See [formatLengthLocalized]; elevation pins the small unit (metres or feet) at every magnitude — 7,431 ft, never 1.4
 * mi — so only the rendering is CLDR's, not the unit choice.
 */
expect fun formatElevationLocalized(meters: Double, system: MeasurementSystem): String?

/** See [formatLengthLocalized]; input is m/s, CLDR renders km/h or mph. */
expect fun formatSpeedLocalized(metersPerSecond: Double, system: MeasurementSystem): String?

/** See [formatLengthLocalized]; input is mm under CLDR's rainfall usage, rendering mm or inches. */
expect fun formatRainfallLocalized(millimeters: Double, system: MeasurementSystem): String?
