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
package org.meshtastic.core.data.repository

import org.meshtastic.core.model.DeviceLink

/**
 * Pure matching logic for associating msh.to [DeviceLink]s with a device's `platformioTarget`. Ported from the
 * Meshtastic-Apple `DeviceLinksSection` (multi-tier matching: exact vendor, product variant, marketplace), so the two
 * platforms surface the same links.
 */
object DeviceLinkMatcher {

    /**
     * Links relevant to [target], region-filtered and sorted with vendor/variant links first.
     *
     * @param links all imported links.
     * @param marketplaceKeys known marketplace identifiers (from `marketplaces.json`).
     * @param deviceTargets all known device `platformioTarget`s — used to exclude other devices' links.
     * @param target the viewed device's `platformioTarget`.
     * @param region the user's ISO 3166-1 alpha-2 region for marketplace filtering.
     */
    fun match(
        links: List<DeviceLink>,
        marketplaceKeys: Set<String>,
        deviceTargets: Set<String>,
        target: String,
        region: String,
    ): List<DeviceLink> {
        val variants = buildTargetVariants(target)
        return links
            .filter { link -> matches(link, marketplaceKeys, deviceTargets, target, variants, region) }
            .sortedByDescending { it.isVendor || !isMarketplaceLink(it.shortCode, marketplaceKeys) }
    }

    @Suppress("ReturnCount")
    private fun matches(
        link: DeviceLink,
        marketplaceKeys: Set<String>,
        deviceTargets: Set<String>,
        target: String,
        variants: List<String>,
        region: String,
    ): Boolean {
        val code = link.shortCode

        // Exact vendor match always wins.
        if (code == target) return true

        // A vendor link for a different device is never shown here.
        if (link.isVendor && code != target) return false

        // Variant/marketplace-suffix: "<target>-..." or "<target>_...".
        val matchesPrefix = variants.any { code.startsWith("${it}_") || code.startsWith("$it-") }

        // Known marketplace prefix: "<marketplace>-<target>" or "<marketplace>_<target>".
        val matchesMarketplacePrefix =
            variants.any { variant -> marketplaceKeys.any { mp -> code == "$mp-$variant" || code == "${mp}_$variant" } }

        if (!matchesPrefix && !matchesMarketplacePrefix) return false

        // A prefix hit that is itself a different device's target belongs to that device, not this one.
        if (matchesPrefix && code in deviceTargets && code != target) return false

        // Region filter: null regions = vendor/variant (always), empty = worldwide, else must include the region.
        val regions = link.regions ?: return true
        if (regions.isEmpty()) return true
        return region in regions
    }

    /** True when [code] carries a known marketplace prefix or suffix. */
    fun isMarketplaceLink(code: String, marketplaceKeys: Set<String>): Boolean =
        marketplaceKeyFor(code, marketplaceKeys) != null

    /**
     * The marketplace identifier [code] belongs to (as a delimiter-bounded prefix `mp-`/`mp_` or suffix `-mp`/`_mp`),
     * or `null` if none. This is the single source of truth for "is this a marketplace link" — used for import-time
     * region tagging, sort ordering, and UI prominence — so the classifications never disagree. Delimiter bounds avoid
     * mis-tagging codes that merely begin with a marketplace name (e.g. `muziworks` is NOT `muzi`).
     */
    fun marketplaceKeyFor(code: String, marketplaceKeys: Set<String>): String? = marketplaceKeys.firstOrNull { mp ->
        code.startsWith("$mp-") || code.startsWith("${mp}_") || code.endsWith("-$mp") || code.endsWith("_$mp")
    }

    /**
     * Alternate target strings for matching. Strips a leading `rak` (e.g. `rak4631` → `4631`) to absorb msh.to naming
     * inconsistencies like `rokland-4631`.
     */
    fun buildTargetVariants(target: String): List<String> {
        val variants = mutableListOf(target)
        if (target.startsWith("rak")) {
            val stripped = target.removePrefix("rak")
            if (stripped.isNotEmpty()) variants.add(stripped)
        }
        return variants
    }
}
