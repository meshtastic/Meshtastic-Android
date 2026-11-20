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
package org.meshtastic.core.service

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.selects.select
import org.meshtastic.core.model.Channel
import org.meshtastic.core.model.Position
import org.meshtastic.core.model.util.onWirePrecisionBits
import org.meshtastic.core.repository.Location
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config

/** Fixes in a row inside one cell before the phone's GPS sleeps: two intervals of stillness. */
internal const val STILL_FIXES = 3

private const val FULL_PRECISION = 32

private const val ALL_BITS = 0xFFFFFFFFL

/**
 * The precision, in bits, of the cell the radio judges a still position in (`PositionModule::runOnce`): the first
 * channel that shares a position, at the precision it goes out at, or a tracker's own precision. 0 means there is no
 * cell and the phone never sleeps: a LOST_AND_FOUND node, no channel that shares a position, full precision, or a
 * primary with no module settings, which firmware before 2.8 sends at full precision.
 */
internal fun stillnessCellBits(channels: List<ChannelSettings>, role: Config.DeviceConfig.Role?): Int {
    val primary = channels.firstOrNull()
    val bits =
        if (role == Config.DeviceConfig.Role.LOST_AND_FOUND || primary?.module_settings == null) {
            0
        } else {
            channels.withIndex().firstNotNullOfOrNull { (index, settings) ->
                val own = settings.module_settings?.position_precision ?: 0
                val onWire = onWirePrecisionBits(own, Channel.usesPublicKey(settings, primary.takeIf { index > 0 }))
                val tracker = role == Config.DeviceConfig.Role.TRACKER || role == Config.DeviceConfig.Role.TAK_TRACKER
                (if (tracker) own else onWire).takeIf { onWire != 0 }
            } ?: 0
        }
    return if (bits >= FULL_PRECISION) 0 else bits
}

/** Whether two points share a cell of [bits], as firmware masks a position to its precision. */
internal fun sameCell(a: Location, b: Location, bits: Int): Boolean {
    val mask = (ALL_BITS shl (FULL_PRECISION - bits)).toInt()
    return (Position.degI(a.latitude) and mask) == (Position.degI(b.latitude) and mask) &&
        (Position.degI(a.longitude) and mask) == (Position.degI(b.longitude) and mask)
}

/**
 * Feed the radio the phone's fixes, and let the phone's GPS rest while it is still: once [STILL_FIXES] fixes in a row
 * stay in the radio's stillness cell ([cellBits]), stop collecting, which removes the location request, and wait for
 * [awaitMotion] or a change of cell before collecting again. Firmware keeps broadcasting the last position it was
 * given, so a sleep loses nothing the mesh would see. Only fixes [send] reports the radio took count toward the
 * stillness, so a rejected one keeps the feed open. With no cell, or when [awaitMotion] reports no sensor, it never
 * stops. Returns when [fixes] ends.
 */
internal suspend fun feedWhileMoving(
    fixes: () -> Flow<Location>,
    cellBits: StateFlow<Int>,
    awaitMotion: suspend () -> Boolean,
    send: suspend (Location) -> Boolean,
) {
    var canSleep = true
    while (true) {
        var anchor: Location? = null
        var still = 0
        var countedBits = cellBits.value
        var asleep = false
        fixes()
            .takeWhile { fix ->
                if (!send(fix)) return@takeWhile true
                val bits = cellBits.value
                if (bits != countedBits) {
                    anchor = null
                    countedBits = bits
                }
                val inCell = bits > 0 && anchor?.let { sameCell(it, fix, bits) } == true
                still = if (inCell) still + 1 else 1
                if (!inCell) anchor = fix
                asleep = canSleep && inCell && still >= STILL_FIXES
                !asleep
            }
            .collect()
        if (!asleep) return
        canSleep = wakeOnMotionOrNewCell(cellBits, countedBits, awaitMotion)
    }
}

/** [awaitMotion]'s answer, or true once the cell differs from [sleptAt] so the next round is judged against it. */
private suspend fun wakeOnMotionOrNewCell(
    cellBits: StateFlow<Int>,
    sleptAt: Int,
    awaitMotion: suspend () -> Boolean,
): Boolean = coroutineScope {
    val motion = async { awaitMotion() }
    val newCell = async { cellBits.first { it != sleptAt } }
    select {
        motion.onAwait { it }
        newCell.onAwait { true }
    }
        .also {
            motion.cancel()
            newCell.cancel()
        }
}
