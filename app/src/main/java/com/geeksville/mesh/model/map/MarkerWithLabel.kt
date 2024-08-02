package com.geeksville.mesh.model.map

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

class MarkerWithLabel(val mapView: MapView?, label: String, emoji: String? = null) : Marker(mapView) {

    companion object {
        private const val LABEL_CORNER_RADIUS = 12F
        private const val LABEL_Y_OFFSET = 100F
    }

    private var onLongClickListener: (() -> Boolean)? = null

    fun setOnLongClickListener(listener: () -> Boolean) {
        onLongClickListener = listener
    }

    private val mLabel = label
    private val mEmoji = emoji
    private val textPaint = Paint().apply {
        textSize = 40f
        color = Color.DKGRAY
        isAntiAlias = true
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }
    private val emojiPaint = Paint().apply {
        textSize = 80f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }

    private val bgPaint = Paint().apply { color = Color.WHITE }

    private fun getTextBackgroundSize(text: String, x: Float, y: Float): RectF {
        val fontMetrics = textPaint.fontMetrics
        val halfTextLength = textPaint.measureText(text) / 2 + 3
        return RectF(
            (x - halfTextLength),
            (y + fontMetrics.top),
            (x + halfTextLength),
            (y + fontMetrics.bottom)
        )
    }

    override fun onLongPress(event: MotionEvent?, mapView: MapView?): Boolean {
        val touched = hitTest(event, mapView)
        if (touched && this.id != null) {
            return onLongClickListener?.invoke() ?: super.onLongPress(event, mapView)
        }
        return super.onLongPress(event, mapView)
    }

    override fun draw(c: Canvas, osmv: MapView?, shadow: Boolean) {
        super.draw(c, osmv, false)
        val p = mPositionPixels
        val bgRect = getTextBackgroundSize(mLabel, (p.x - 0F), (p.y - LABEL_Y_OFFSET))
        bgRect.inset(-8F, -2F)

        c.drawRoundRect(bgRect, LABEL_CORNER_RADIUS, LABEL_CORNER_RADIUS, bgPaint)
        c.drawText(mLabel, (p.x - 0F), (p.y - LABEL_Y_OFFSET), textPaint)
        mEmoji?.let { c.drawText(it, (p.x - 0f), (p.y - 30f), emojiPaint) }
    }

}
