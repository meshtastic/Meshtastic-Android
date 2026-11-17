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
package org.meshtastic.core.ui.component

// jvmAndroidMain's actuals use `java.lang.Class` reflection (`declaringJavaClass.enumConstants`, `getField(...)`) to
// enumerate an enum's entries and check for `@Deprecated` from just an instance, with no reified type parameter to
// call `enumValues<T>()`/`enumEntries<T>()` instead. Kotlin/Wasm has no `java.lang.Class`-equivalent reflection
// surface at all (same gap iOS's Kotlin/Native actual has, which is why it takes the identical fallback below) — and
// changing `DropDownPreference`'s public signature to take a reified type parameter instead of an instance is a
// commonMain API change well outside this pass's scope. Matches iOS exactly: `DropDownPreference`'s enum-instance
// overload renders with no selectable items on this target, same as on iOS today — a pre-existing gap this pass
// inherits rather than introduces. Callers who need reliable enum options in a dropdown can use the `List<Pair<T,
// String>>` or `List<DropDownItem<T>>` overloads instead, which don't call through this expect at all.
internal actual fun <T : Enum<T>> enumEntriesOf(selectedItem: T): List<T> = emptyList()

internal actual fun Enum<*>.isDeprecatedEnumEntry(): Boolean = false
