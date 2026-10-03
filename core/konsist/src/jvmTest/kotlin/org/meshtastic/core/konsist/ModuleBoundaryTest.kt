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
package org.meshtastic.core.konsist

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Feature modules sit side by side on top of core: a feature imports no other feature, and core imports no feature.
 * Checked on imports rather than build files, so it sees the dependency code uses, not the one a build file declares.
 */
class ModuleBoundaryTest {

    /**
     * The MapLibre renderer draws feature:map's shared map model and map-terrain's elevation tiles. Coverage samples
     * the same elevation tiles and computes the estimate feature:map's Site Planner form describes.
     */
    private val allowedFeatureImports =
        mapOf("map-maplibre" to setOf("map", "map-terrain"), "coverage" to setOf("map", "map-terrain"))

    private val sourceFiles = Konsist.scopeFromProject().files.filterNot { it.isNestedAgentWorktree() }

    private val featureFiles = sourceFiles.mapNotNull { file -> file.moduleUnder("feature")?.let { file to it } }

    private val coreFiles = sourceFiles.filter { it.moduleUnder("core") != null }

    /** Every package a feature module declares, mapped to the modules that declare it. */
    private val featurePackageOwners: Map<String, Set<String>> =
        featureFiles
            .mapNotNull { (file, module) -> file.packagee?.name?.let { it to module } }
            .groupBy({ it.first }, { it.second })
            .mapValues { it.value.toSet() }

    private fun KoFileDeclaration.moduleUnder(group: String): String? =
        Regex("^/$group/([^/]+)/src/").find(scanPath)?.groupValues?.get(1)

    /** The feature modules that own [import], by the longest declared package it falls under. */
    private fun featureOwnersOf(import: String): Set<String> {
        var candidate = import
        while ('.' in candidate) {
            candidate = candidate.substringBeforeLast('.')
            featurePackageOwners[candidate]?.let {
                return it
            }
        }
        return emptySet()
    }

    private data class FeatureImport(val location: String, val from: String, val owners: Set<String>)

    private fun crossFeatureImports(): List<FeatureImport> = featureFiles.flatMap { (file, module) ->
        file.imports.mapNotNull { import ->
            val owners = featureOwnersOf(import.name)
            if (owners.isEmpty() || module in owners) {
                null
            } else {
                FeatureImport("${file.scanPath}: ${import.name}", module, owners)
            }
        }
    }

    @Test
    fun `the scan actually reaches feature and core sources`() {
        assertTrue(featureFiles.isNotEmpty(), emptyScanMessage("feature module scan"))
        assertTrue(coreFiles.isNotEmpty(), emptyScanMessage("core module scan"))
        assertTrue(
            featureFiles.any { (file, _) -> file.scanPath.startsWith("/feature/map-maplibre/src/commonMain/") },
            "expected feature/map-maplibre sources in scope; got ${featureFiles.size} files",
        )
    }

    @Test
    fun `the feature allowlist still matches real imports`() {
        val used = crossFeatureImports().groupBy({ it.from }, { it.owners }).mapValues { it.value.flatten().toSet() }

        allowedFeatureImports.forEach { (from, targets) ->
            assertTrue(
                used[from].orEmpty().containsAll(targets),
                "feature:$from no longer imports all of $targets (it imports ${used[from].orEmpty()}); " +
                    "shrink the allowlist so this rule keeps verifying something.",
            )
        }
    }

    @Test
    fun `no feature imports another feature outside the allowlist`() {
        val offenders =
            crossFeatureImports()
                .filterNot { import -> import.owners.all { it in allowedFeatureImports[import.from].orEmpty() } }
                .map { it.location }

        assertTrue(
            offenders.isEmpty(),
            "Feature modules share code through core modules, not through each other. Offending imports:\n" +
                offenders.joinToString("\n"),
        )
    }

    @Test
    fun `no core module imports a feature`() {
        val offenders = coreFiles.flatMap { file ->
            file.imports
                .map { it.name }
                .filter { it.startsWith("org.meshtastic.feature.") }
                .map { "${file.scanPath}: $it" }
        }

        assertTrue(
            offenders.isEmpty(),
            "Core modules sit below every feature and must not import one. Offending imports:\n" +
                offenders.joinToString("\n"),
        )
    }
}
