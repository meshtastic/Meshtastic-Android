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
package org.meshtastic.core.data.manager

import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.core.annotation.Single
import org.meshtastic.core.repository.DisplayMirrorManager
import org.meshtastic.core.repository.MirrorFrame
import org.meshtastic.proto.DisplayFrame

/**
 * Display frame reassembly state machine.
 *
 * Chunks arrive in offset order within a frame (the firmware drains one snapshot before capturing the next), so a
 * chunk with a new `frame_id` or `offset == 0` starts a new frame and an out-of-sequence chunk drops the partial
 * frame. Called sequentially from [FromRadioPacketHandlerImpl] on a single IO coroutine.
 */
@Single
class DisplayMirrorManagerImpl : DisplayMirrorManager {

    private val _frame = MutableStateFlow<MirrorFrame?>(null)
    override val frame = _frame.asStateFlow()

    private var buffer: ByteArray? = null
    private var frameId = 0
    private var received = 0

    override fun handleIncomingFrame(chunk: DisplayFrame) {
        val data = chunk.data_.toByteArray()
        val total = chunk.total_size

        if (total <= 0 || total > MAX_FRAME_BYTES || chunk.offset + data.size > total) {
            Logger.w { "DisplayMirror: dropping malformed chunk (offset=${chunk.offset} size=${data.size} total=$total)" }
            return
        }

        if (chunk.offset == 0) {
            buffer = ByteArray(total)
            frameId = chunk.frame_id
            received = 0
        }

        val buf = buffer
        if (buf == null || chunk.frame_id != frameId || chunk.offset != received || buf.size != total) {
            Logger.w { "DisplayMirror: out-of-sequence chunk (frame=${chunk.frame_id}/$frameId offset=${chunk.offset}/$received)" }
            buffer = null
            return
        }

        data.copyInto(buf, chunk.offset)
        received += data.size

        if (received == total) {
            _frame.value = MirrorFrame(width = chunk.width, height = chunk.height, frameId = frameId, pixels = buf)
            buffer = null
        }
    }

    private companion object {
        // Generous sanity cap: 320x240 at 1bpp is 9600 bytes.
        const val MAX_FRAME_BYTES = 16384
    }
}
