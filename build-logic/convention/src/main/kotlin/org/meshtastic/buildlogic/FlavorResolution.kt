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
package org.meshtastic.buildlogic

import com.android.build.api.attributes.ProductFlavorAttr
import org.gradle.api.Project
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeDisambiguationRule
import org.gradle.api.attributes.MultipleCandidatesDetails
import javax.inject.Inject

private const val LEGACY_MARKETPLACE_ATTRIBUTE_NAME = "marketplace"

/**
 * Registers [AttributeDisambiguationRule]s so Gradle can pick a default product flavor when a consumer configuration
 * (e.g. `androidHostTestRuntimeClasspath` from a KMP module) does not carry the marketplace flavor attribute, but the
 * producer (e.g. `core:barcode`) publishes multiple flavor variants.
 *
 * This replaces the previous `afterEvaluate { configurations.configureEach { … } }` approach that stamped attributes on
 * every resolvable Android configuration. Disambiguation rules fire during dependency resolution — not configuration
 * time — so they are immune to KGP's lazy configuration creation order and fully compatible with Configuration Cache,
 * Isolated Projects, and future Gradle/KGP changes.
 *
 * The default flavor is configurable via the `meshtastic.defaultMarketplace` Gradle property (defaults to the
 * [MeshtasticFlavor] entry marked `default = true`, which is `google`).
 */
internal fun Project.configureAndroidMarketplaceFallback() {
    val defaultMarketplace =
        providers
            .gradleProperty("meshtastic.defaultMarketplace")
            .orElse(MeshtasticFlavor.entries.first { it.default }.name)
            .get()

    // AGP publishes the typed ProductFlavorAttr on flavored variant configurations.
    val marketplaceAttr = ProductFlavorAttr.of(MeshtasticFlavor.fdroid.dimension.name)
    dependencies.attributesSchema.attribute(marketplaceAttr) {
        disambiguationRules.add(ProductFlavorDisambiguationRule::class.java) { params(defaultMarketplace) }
    }

    // Some AGP versions also publish a plain String "marketplace" attribute.
    val legacyMarketplaceAttr = Attribute.of(LEGACY_MARKETPLACE_ATTRIBUTE_NAME, String::class.java)
    dependencies.attributesSchema.attribute(legacyMarketplaceAttr) {
        disambiguationRules.add(StringDisambiguationRule::class.java) { params(defaultMarketplace) }
    }
}

/**
 * Selects the default marketplace flavor when Gradle encounters ambiguous [ProductFlavorAttr] candidates during
 * variant-aware dependency resolution.
 */
internal abstract class ProductFlavorDisambiguationRule @Inject constructor(private val defaultFlavor: String) :
    AttributeDisambiguationRule<ProductFlavorAttr> {
    override fun execute(details: MultipleCandidatesDetails<ProductFlavorAttr>) {
        details.candidateValues.find { it.name == defaultFlavor }?.let { details.closestMatch(it) }
    }
}

/** Selects the default marketplace for the legacy plain-String "marketplace" attribute. */
internal abstract class StringDisambiguationRule @Inject constructor(private val defaultFlavor: String) :
    AttributeDisambiguationRule<String> {
    override fun execute(details: MultipleCandidatesDetails<String>) {
        details.candidateValues.find { it == defaultFlavor }?.let { details.closestMatch(it) }
    }
}
