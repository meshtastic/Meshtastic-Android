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

import com.lemonappdev.konsist.api.declaration.KoFileDeclaration

/** Makes project-relative path rules independent of the host filesystem separator. */
internal fun String.normalizedProjectPath(): String = replace('\\', '/')

/**
 * Path relative to the checkout being scanned, e.g. `/core/ble/src/commonMain/kotlin/…`.
 *
 * Every path rule here must key off this rather than the absolute [KoFileDeclaration.path]: agent sessions run from
 * worktrees under `<main checkout>/.claude/worktrees/<name>`, so an absolute path carries `.claude` for every file in
 * the checkout.
 */
internal val KoFileDeclaration.scanPath: String
    get() = projectPath.normalizedProjectPath()

/**
 * A file belonging to an agent worktree nested inside the checkout being scanned.
 *
 * `scopeFromProject` sweeps `<root>/.claude/worktrees/` too, and stale copies there resurface long-fixed lines as
 * phantom offenders. This matches only at the root of the scanned checkout, so a scan that itself runs from a worktree
 * still sees its own sources.
 */
internal fun KoFileDeclaration.isNestedAgentWorktree(): Boolean = scanPath.removePrefix("/").startsWith(".claude/")

/**
 * Failure message for a scope that matched nothing.
 *
 * Names the historical cause up front: a too-broad `.claude` exclusion silently emptied the whole scan for worktree
 * sessions, and the empty result is the only symptom.
 */
internal fun emptyScanMessage(scopeDescription: String): String =
    "$scopeDescription matched no files at all, so this rule verified nothing. " +
        "Check the path filters in ProjectPath.kt — an exclusion that matches the whole checkout (rather than a " +
        "directory relative to its root) empties the scan without any other symptom."
