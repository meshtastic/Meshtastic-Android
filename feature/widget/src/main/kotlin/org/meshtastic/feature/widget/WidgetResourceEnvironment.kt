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
package org.meshtastic.feature.widget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.glance.LocalContext

/**
 * Makes Compose Multiplatform resources usable inside a Glance composition.
 *
 * Glance runs its own composition with its own set of composition locals ([androidx.glance.LocalContext],
 * [androidx.glance.LocalSize], ...). It deliberately does *not* provide the `androidx.compose.ui` locals, because a
 * Glance tree is translated to `RemoteViews` rather than laid out and drawn by Compose UI.
 *
 * Compose Multiplatform's `stringResource` does not know that. On Android it reads
 * [androidx.compose.ui.platform.LocalContext] to find the asset loader, plus [LocalConfiguration] to pick the locale
 * and [LocalDensity] to pick density-qualified resources. None of those are present in a Glance composition, so any
 * `stringResource` call inside a widget throws `CompositionLocal ... not present` unless we bridge them across.
 *
 * That is what this composable does, and it is the only reason it exists. Wrapping widget content in it is therefore
 * mandatory, not optional: the widget is fully translated via Crowdin (`core/resources`), so essentially every visible
 * string depends on this bridge.
 *
 * Two consequences worth knowing before changing this:
 * - The bridge is a workaround, not a documented Glance pattern. If Compose Multiplatform starts reading another
 *   composition local when resolving resources, every string in the widget begins throwing at runtime. That failure is
 *   caught by [LocalStatsWidget.onCompositionError] rather than crashing the app, and `LocalStatsWidgetContentTest`
 *   fails in CI if resolution breaks.
 * - [LocalDensity] is derived from the display metrics rather than from Glance's `LocalSize`. Glance sizing is
 *   expressed in `dp` and converted during translation, so density only affects which resource qualifier is chosen here
 *   — it must not be used to compute widget layout.
 */
@Composable
internal fun WidgetResourceEnvironment(content: @Composable () -> Unit) {
    val context = LocalContext.current
    CompositionLocalProvider(
        androidx.compose.ui.platform.LocalContext provides context,
        LocalConfiguration provides context.resources.configuration,
        LocalDensity provides Density(context.resources.displayMetrics.density),
        content = content,
    )
}
