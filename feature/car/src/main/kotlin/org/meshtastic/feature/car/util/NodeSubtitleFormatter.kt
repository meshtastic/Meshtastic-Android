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

import android.content.Context
import android.text.Spannable
import android.text.SpannableString
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarText
import androidx.car.app.model.ForegroundCarColorSpan
import org.meshtastic.core.common.util.DateFormatter
import org.meshtastic.feature.car.R
import org.meshtastic.feature.car.model.NodeUi
import org.meshtastic.feature.car.model.SignalQuality

/** Shared formatter for node subtitle text with signal coloring and responsive variants. */
object NodeSubtitleFormatter {

    fun format(context: Context, node: NodeUi): CarText {
        val signalLabel = signalLabel(context, node.signalQuality)
        val battery = node.batteryPercent?.let { " • $it%" } ?: ""
        val lastHeard =
            if (node.lastHeard != 0L) {
                " • ${DateFormatter.formatRelativeTime(node.lastHeard)}"
            } else {
                ""
            }
        val status = if (!node.isOnline) " • ${context.getString(R.string.car_status_offline)}" else ""
        val full = "$signalLabel$battery$lastHeard$status"
        val short = "$signalLabel$battery"

        val signalColor = signalColor(node.signalQuality)

        val fullSpannable = SpannableString(full)
        fullSpannable.setSpan(
            ForegroundCarColorSpan.create(signalColor),
            0,
            signalLabel.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
        )

        val shortSpannable = SpannableString(short)
        shortSpannable.setSpan(
            ForegroundCarColorSpan.create(signalColor),
            0,
            signalLabel.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
        )

        return CarText.Builder(fullSpannable).addVariant(shortSpannable).build()
    }

    fun signalLabel(context: Context, quality: SignalQuality): String = when (quality) {
        SignalQuality.EXCELLENT -> context.getString(R.string.car_signal_excellent)
        SignalQuality.GOOD -> context.getString(R.string.car_signal_good)
        SignalQuality.FAIR -> context.getString(R.string.car_signal_fair)
        SignalQuality.BAD -> context.getString(R.string.car_signal_bad)
        SignalQuality.NONE -> context.getString(R.string.car_signal_none)
    }

    fun signalColor(quality: SignalQuality): CarColor = when (quality) {
        SignalQuality.EXCELLENT -> CarColor.GREEN
        SignalQuality.GOOD -> CarColor.GREEN
        SignalQuality.FAIR -> CarColor.YELLOW
        SignalQuality.BAD -> CarColor.RED
        SignalQuality.NONE -> CarColor.SECONDARY
    }
}
