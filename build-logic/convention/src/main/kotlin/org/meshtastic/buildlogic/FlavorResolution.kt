/*
 * Copyright (c) 2025 Meshtastic LLC
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

package org.meshtastic.buildlogic

import org.gradle.api.Project
import org.gradle.api.attributes.Attribute

private const val MARKETPLACE_ATTRIBUTE_NAME = "com.android.build.api.attributes.ProductFlavor:marketplace"

internal fun Project.configureAndroidMarketplaceFallback() {
    val defaultMarketplace =
        providers
            .gradleProperty("meshtastic.defaultMarketplace")
            .orElse(MeshtasticFlavor.entries.first { it.default }.name)
            .get()

    val marketplaceAttr = Attribute.of(MARKETPLACE_ATTRIBUTE_NAME, String::class.java)

    configurations.configureEach {
        if (!isCanBeResolved || isCanBeConsumed) return@configureEach
        if (!name.contains("android", ignoreCase = true)) return@configureEach
        if (attributes.getAttribute(marketplaceAttr) != null) return@configureEach

        // Prefer explicit flavor from configuration name; otherwise use configurable default.
        val inferredMarketplace =
            when {
                name.contains(MeshtasticFlavor.fdroid.name, ignoreCase = true) -> MeshtasticFlavor.fdroid.name
                name.contains(MeshtasticFlavor.google.name, ignoreCase = true) -> MeshtasticFlavor.google.name
                else -> defaultMarketplace
            }

        attributes.attribute(marketplaceAttr, inferredMarketplace)
    }
}
