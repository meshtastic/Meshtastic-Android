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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for [DeviceLinkMatcher], grounded in the acceptance scenarios of the Meshtastic-Apple `010-device-mshto-links`
 * spec. Mirrors the as-built `DeviceLinksSection` matching (platformioTarget, not hwModelSlug).
 */
class DeviceLinkMatcherTest {

    private val marketplaceKeys = setOf("rokland", "hexaspot", "aliexpress", "amazon", "tindie", "muzi")

    private val deviceTargets =
        setOf("rak4631", "heltec-v3", "seeed_solar_node", "tbeam", "rak4631_nomadstar_meteor_pro")

    private fun link(shortCode: String, isVendor: Boolean = false, regions: List<String>? = null) = DeviceLink(
        shortCode = shortCode,
        originalUrl = "https://example.com/$shortCode",
        isVendor = isVendor,
        regions = regions,
    )

    private fun match(links: List<DeviceLink>, target: String, region: String = "US") =
        DeviceLinkMatcher.match(links, marketplaceKeys, deviceTargets, target, region).map { it.shortCode }

    @Test
    fun exactVendorMatchIsIncluded() {
        val result = match(listOf(link("heltec-v3", isVendor = true)), target = "heltec-v3")
        assertEquals(listOf("heltec-v3"), result)
    }

    @Test
    fun foreignVendorLinkIsExcluded() {
        // Scenario 5: rak4631_nomadstar_meteor_pro (a different device's target) must NOT show for rak4631.
        val result =
            match(
                listOf(link("rak4631", isVendor = true), link("rak4631_nomadstar_meteor_pro", isVendor = true)),
                target = "rak4631",
            )
        assertEquals(listOf("rak4631"), result)
    }

    @Test
    fun productVariantIsIncludedAndProminent() {
        val result = match(listOf(link("rak4631_epaper")), target = "rak4631")
        assertEquals(listOf("rak4631_epaper"), result)
    }

    @Test
    fun marketplaceLinkIsRegionFiltered() {
        val links = listOf(link("rokland-rak4631", regions = listOf("US", "CA")))
        assertEquals(listOf("rokland-rak4631"), match(links, target = "rak4631", region = "US"))
        assertEquals(emptyList(), match(links, target = "rak4631", region = "DE"))
    }

    @Test
    fun rakPrefixIsStrippedForMarketplaceVariantMatch() {
        // "rokland-4631" should match device "rak4631" via the rak-stripped variant "4631".
        val result = match(listOf(link("rokland-4631", regions = listOf("US"))), target = "rak4631", region = "US")
        assertEquals(listOf("rokland-4631"), result)
    }

    @Test
    fun worldwideMarketplaceShowsRegardlessOfRegion() {
        val links = listOf(link("rak4631_aliexpress", regions = emptyList()))
        assertEquals(listOf("rak4631_aliexpress"), match(links, target = "rak4631", region = "ZZ"))
    }

    @Test
    fun unrelatedLinksProduceEmptyResult() {
        val links =
            listOf(
                link("github"),
                link("heltec-v3", isVendor = true),
                link("rokland-heltec-v3", regions = listOf("US")),
            )
        assertEquals(emptyList(), match(links, target = "tbeam"))
    }

    @Test
    fun anotherDevicesTargetIsNotMatchedAsVariant() {
        // "rak4631_nomadstar_meteor_pro" prefix-matches "rak4631_" but is itself a device target → excluded.
        val result = match(listOf(link("rak4631_nomadstar_meteor_pro")), target = "rak4631")
        assertEquals(emptyList(), result)
    }

    @Test
    fun vendorAndVariantSortBeforeMarketplace() {
        val links =
            listOf(
                link("rak4631_aliexpress", regions = emptyList()),
                link("rak4631", isVendor = true),
                link("rokland-rak4631", regions = listOf("US")),
                link("rak4631_epaper"),
            )
        val result = match(links, target = "rak4631", region = "US")
        // Vendor + variant first (order among them preserved from input), marketplace links after.
        assertEquals(listOf("rak4631", "rak4631_epaper", "rak4631_aliexpress", "rokland-rak4631"), result)
    }

    @Test
    fun buildTargetVariantsStripsRakPrefix() {
        assertEquals(listOf("rak4631", "4631"), DeviceLinkMatcher.buildTargetVariants("rak4631"))
        assertEquals(listOf("heltec-v3"), DeviceLinkMatcher.buildTargetVariants("heltec-v3"))
        // Bare "rak" strips to empty and is not added.
        assertEquals(listOf("rak"), DeviceLinkMatcher.buildTargetVariants("rak"))
    }

    @Test
    fun isMarketplaceLinkDetectsPrefixAndSuffix() {
        assertTrue(DeviceLinkMatcher.isMarketplaceLink("rokland-rak4631", marketplaceKeys))
        assertTrue(DeviceLinkMatcher.isMarketplaceLink("heltec-v3_aliexpress", marketplaceKeys))
        assertFalse(DeviceLinkMatcher.isMarketplaceLink("heltec-v3", marketplaceKeys))
    }
}
