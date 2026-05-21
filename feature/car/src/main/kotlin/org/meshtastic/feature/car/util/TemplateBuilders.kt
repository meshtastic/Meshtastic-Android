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

package org.meshtastic.feature.car.util

import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.Row
import androidx.core.graphics.drawable.IconCompat

/**
 * Helper extensions for building CAL templates with less boilerplate.
 */

fun buildHeader(title: String, startAction: Action? = null): Header {
    return Header.Builder().apply {
        setTitle(title)
        startAction?.let { setStartHeaderAction(it) }
    }.build()
}

fun buildItemList(block: ItemList.Builder.() -> Unit): ItemList {
    return ItemList.Builder().apply(block).build()
}

fun buildRow(
    title: String,
    text: String? = null,
    onClickListener: (() -> Unit)? = null,
): Row {
    return Row.Builder().apply {
        setTitle(title)
        text?.let { addText(it) }
        onClickListener?.let { setOnClickListener(it) }
    }.build()
}

fun buildCarIcon(iconCompat: IconCompat, tint: CarColor? = null): CarIcon {
    return CarIcon.Builder(iconCompat).apply {
        tint?.let { setTint(it) }
    }.build()
}
