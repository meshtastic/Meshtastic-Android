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
import com.lemonappdev.konsist.api.verify.assertFalse
import org.junit.Test

/**
 * Enforces the KMP framework-bleed rule from AGENTS.md: shared code in any `commonMain` source set must never depend on
 * the JVM (`java.*`) or Android (`android.*`) platform APIs — use the KMP equivalents (Okio, kotlinx-datetime,
 * atomicfu, Mutex, …) instead.
 *
 * Konsist scans every module's Kotlin source from disk, so this single test covers all 37 modules. It runs on the JVM
 * (Konsist is JVM-only) under the existing `allTests` baseline gate.
 */
class CommonMainFrameworkBoundaryTest {
    private val commonMainFiles = Konsist.scopeFromProject().files.filter { "/src/commonMain/" in it.path }

    @Test
    fun `commonMain declares no android imports`() {
        commonMainFiles.assertFalse { file -> file.imports.any { it.name.startsWith("android.") } }
    }

    @Test
    fun `commonMain declares no java imports`() {
        commonMainFiles.assertFalse { file -> file.imports.any { it.name.startsWith("java.") } }
    }
}
