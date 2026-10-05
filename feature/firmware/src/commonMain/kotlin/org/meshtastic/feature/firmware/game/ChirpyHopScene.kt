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
package org.meshtastic.feature.firmware.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

// The play field keeps a fixed light palette in both themes: the sprites are drawn with black linework that vanishes
// on a dark surface.
internal val ChirpyPaper = Color(0xFFF7F7F7)
internal val ChirpyInk = Color(0xFF454545)
private val GroundMarkColor = Color(0xB37A7A7A)

private const val GROUND_FRACTION = 0.18f
private const val GROUND_LINE_DP = 3f
private const val MARK_SPACING_DP = 68f
private const val MARK_WRAP_DP = 24f
private const val MIN_MARKS = 6
private const val SPRITE_HEIGHT_DP = 168f
private const val SPRITE_ASPECT = 128f / 168f
private const val SPRITE_MAX_FIELD_FRACTION = 0.32f

/** Engine heights are fractions of the field; this is the share of the field's height one unit of jump spans. */
internal const val JUMP_SCALE = 0.72f

/**
 * Everything drawn in the play field, for one size of field. It is built by `drawWithCache` when the size changes and
 * reused every frame, so the paths, strokes and layout behind each frame are made once rather than per frame.
 */
internal class ChirpyHopScene(size: Size, density: Density) : Density by density {
    private val groundY = size.height * (1 - GROUND_FRACTION)

    /** Chirpy's sprite box; obstacles and the knockout are sized against it so they stay in proportion with him. */
    private val spriteHeight = minOf(SPRITE_HEIGHT_DP * this.density, size.height * SPRITE_MAX_FIELD_FRACTION)
    private val spriteWidth = spriteHeight * SPRITE_ASPECT

    private val backdrop = ChirpyHopBackdrop(size, groundY, density)
    private val props = ChirpyHopProps(groundY, spriteHeight, density)
    private val character = ChirpyHopCharacter(groundY, spriteWidth, spriteHeight, density)

    private val groundLine = GROUND_LINE_DP * this.density
    private val markSpacing = MARK_SPACING_DP * this.density
    private val markCount = maxOf((size.width / markSpacing).toInt() + 2, MIN_MARKS)

    fun DrawScope.drawScene(engine: ChirpyHopEngine, world: ChirpyHopWorld, sprites: ChirpyHopSprites) {
        drawRect(ChirpyPaper)
        val shiftPx = (world.groundShift * size.width).toFloat()
        val knockout =
            world.knockedOutFor
                ?.takeIf { engine.phase == ChirpyHopPhase.GameOver }
                ?.let { knockoutPose(it, startHeightDp = engine.playerY * size.height * JUMP_SCALE / density) }
        translate(left = (knockout?.shakeDp ?: 0f).dp.toPx()) {
            with(backdrop) { drawBackdrop(shiftPx, world.sceneSeconds) }
            drawGround(shiftPx)
            with(props) {
                engine.departingKind?.let { drawObstacle(it, engine.departingX, world.sceneSeconds) }
                drawObstacle(engine.obstacleKind, engine.obstacleX, world.sceneSeconds)
            }
            with(character) { drawChirpy(engine, world.sceneSeconds, sprites, knockout) }
        }
    }

    @Suppress("MagicNumber")
    private fun DrawScope.drawGround(shiftPx: Float) {
        drawRect(ChirpyInk, topLeft = Offset(0f, groundY - groundLine / 2), size = Size(size.width, groundLine))
        val wrapStart = -MARK_WRAP_DP.dp.toPx()
        for (index in 0 until markCount) {
            // A fixed scatter of dashes below the ground line, so the ground reads as moving.
            val width = (8 + (index * 7) % 19).dp.toPx()
            val y = groundY + (10 + (index * 11) % 18).dp.toPx()
            val x = wrap(index * markSpacing - shiftPx, wrapStart, markCount * markSpacing)
            drawRect(GroundMarkColor, topLeft = Offset(x - width / 2, y - 1.dp.toPx()), size = Size(width, 2.dp.toPx()))
        }
    }
}

/** Wraps [x] into [start, start + span), so scenery that scrolls off the left re-enters on the right. */
internal fun wrap(x: Float, start: Float, span: Float): Float = ((x - start) % span + span) % span + start
