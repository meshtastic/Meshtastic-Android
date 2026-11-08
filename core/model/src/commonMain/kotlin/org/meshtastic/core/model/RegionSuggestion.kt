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

import org.meshtastic.proto.Config.LoRaConfig.RegionCode

private val EU_868_COUNTRIES =
    setOf(
        "AT",
        "BE",
        "BG",
        "HR",
        "CY",
        "CZ",
        "DK",
        "EE",
        "FI",
        "FR",
        "DE",
        "GR",
        "HU",
        "IE",
        "IT",
        "LV",
        "LT",
        "LU",
        "MT",
        "NL",
        "PL",
        "PT",
        "RO",
        "SK",
        "SI",
        "ES",
        "SE",
        "GB",
        "CH",
        "NO",
        "IS",
        "LI",
        "AL",
        "BA",
        "ME",
        "MK",
        "RS",
        "XK",
        "MD",
        "TR",
        "ZA",
    )

private val REGION_BY_COUNTRY: Map<String, RegionCode> = buildMap {
    listOf("US", "CA", "MX").forEach { put(it, RegionCode.US) }
    put("BR", RegionCode.BR_902)
    put("CN", RegionCode.CN)
    put("JP", RegionCode.JP)
    put("AU", RegionCode.ANZ)
    put("NZ", RegionCode.ANZ)
    put("KR", RegionCode.KR)
    put("TW", RegionCode.TW)
    put("RU", RegionCode.RU)
    put("IN", RegionCode.IN)
    put("TH", RegionCode.TH)
    put("MY", RegionCode.MY_919)
    put("SG", RegionCode.SG_923)
    put("PH", RegionCode.PH_915)
    put("KZ", RegionCode.KZ_863)
    put("NP", RegionCode.NP_865)
    EU_868_COUNTRIES.forEach { put(it, RegionCode.EU_868) }
}

/**
 * The LoRa region to suggest for an ISO 3166-1 alpha-2 [countryCode], or null when the country has no clear answer. A
 * starting point the user confirms, never a value written on its own. The same table as the Apple app's.
 */
fun suggestedRegionForCountry(countryCode: String): RegionCode? = REGION_BY_COUNTRY[countryCode.uppercase()]
