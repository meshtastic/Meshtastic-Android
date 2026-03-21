/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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

package org.meshtastic.core.ui.component.preview
import org.meshtastic.core.ui.component.preview.*
import org.meshtastic.core.ui.component.preview.*

import kotlin.reflect.KClass

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.TYPE)
@Retention(AnnotationRetention.BINARY)
@Repeatable
@OptIn(ExperimentalMultiplatform::class)
@OptionalExpectation
expect annotation class Preview(
    val name: String = "",
    val group: String = "",
    val widthDp: Int = -1,
    val heightDp: Int = -1,
    val locale: String = "",
    val fontScale: Float = 1f,
    val showSystemUi: Boolean = false,
    val showBackground: Boolean = false,
    val backgroundColor: Long = 0,
    val uiMode: Int = 0,
    val device: String = "",
    val apiLevel: Int = -1,
)

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.TYPE)
@Retention(AnnotationRetention.BINARY)
@Repeatable
@OptIn(ExperimentalMultiplatform::class)
@OptionalExpectation
expect annotation class PreviewLightDark()

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.TYPE)
@Retention(AnnotationRetention.BINARY)
@Repeatable
@OptIn(ExperimentalMultiplatform::class)
@OptionalExpectation
expect annotation class PreviewScreenSizes()

@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.BINARY)
@OptIn(ExperimentalMultiplatform::class)
@OptionalExpectation
expect annotation class PreviewParameter(
    val provider: KClass<out PreviewParameterProvider<*>>,
    val limit: Int = Int.MAX_VALUE,
)

expect interface PreviewParameterProvider<T> {
    val values: Sequence<T>
}
