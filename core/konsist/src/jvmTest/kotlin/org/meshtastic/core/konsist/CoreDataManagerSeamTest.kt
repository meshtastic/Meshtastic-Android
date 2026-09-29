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
 * In `core:data`, the mesh engine (`manager`) and the storage side (`repository`, `datasource`) reach each other only
 * through the `core:repository` interfaces that Koin binds, never by naming each other's packages. That keeps `manager`
 * separable into its own module. Production code only; a test may wire both halves.
 */
class CoreDataManagerSeamTest {

    private val productionFiles =
        Konsist.scopeFromProject().files.filterNot { it.isNestedAgentWorktree() }.filterNot { it.isTestSource }

    private val managerPackage = "org.meshtastic.core.data.manager"

    private val storagePackages = listOf("org.meshtastic.core.data.repository", "org.meshtastic.core.data.datasource")

    private fun KoFileDeclaration.isIn(packageName: String): Boolean =
        packagee?.name?.let { it == packageName || it.startsWith("$packageName.") } == true

    private val managerFiles = productionFiles.filter { it.isIn(managerPackage) }

    private val storageFiles = productionFiles.filter { file -> storagePackages.any { file.isIn(it) } }

    /** Every line of [files] that names one of [packages], as an import or written out in full. */
    private fun references(files: List<KoFileDeclaration>, packages: List<String>): List<String> {
        val named = Regex(packages.joinToString("|") { Regex.escape(it) + """\b""" })
        return files.flatMap { file ->
            file.text.lines().withIndex().mapNotNull { (index, line) ->
                if (named.containsMatchIn(line)) "${file.scanPath}:${index + 1}: ${line.trim()}" else null
            }
        }
    }

    @Test
    fun `the scan actually reaches core data manager and storage sources`() {
        assertTrue(managerFiles.isNotEmpty(), emptyScanMessage("core:data manager scan"))
        assertTrue(storageFiles.isNotEmpty(), emptyScanMessage("core:data repository and datasource scan"))
        assertTrue(
            managerFiles.any { it.scanPath.endsWith("/core/data/manager/FromRadioPacketHandlerImpl.kt") },
            "expected core/data manager sources in scope; got ${managerFiles.size} files",
        )
        assertTrue(
            storageFiles.any { it.scanPath.endsWith("/core/data/repository/NodeRepositoryImpl.kt") },
            "expected core/data repository sources in scope; got ${storageFiles.size} files",
        )
    }

    @Test
    fun `manager never names repository or datasource`() {
        val offenders = references(managerFiles, storagePackages)

        assertTrue(
            offenders.isEmpty(),
            "core.data.manager reaches storage through the core:repository interfaces, not core.data.repository or " +
                "core.data.datasource. Offending lines:\n" +
                offenders.joinToString("\n"),
        )
    }

    @Test
    fun `repository and datasource never name manager`() {
        val offenders = references(storageFiles, listOf(managerPackage))

        assertTrue(
            offenders.isEmpty(),
            "core.data.repository and core.data.datasource must not depend on core.data.manager; share through " +
                "core:repository or core:model instead. Offending lines:\n" +
                offenders.joinToString("\n"),
        )
    }
}
