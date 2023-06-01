package com.geeksville.mesh.model.map

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.view.MotionEvent
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

class MarkerWithLabel(mapView: MapView?, label: String, emoji: String? = null) : Marker(mapView) {

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

    private fun getTextBackgroundSize(text: String, x: Float, y: Float): Rect {
        val fontMetrics = textPaint.fontMetrics
        val halfTextLength = textPaint.measureText(text) / 2 + 3
        return Rect(
            (x - halfTextLength).toInt(),
            (y + fontMetrics.top).toInt(),
            (x + halfTextLength).toInt(),
            (y + fontMetrics.bottom).toInt()
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
        val bgRect = getTextBackgroundSize(mLabel, (p.x - 0f), (p.y - 110f))
        c.drawRect(bgRect, bgPaint)
        c.drawText(mLabel, (p.x - 0f), (p.y - 110f), textPaint)
        mEmoji?.let { c.drawText(it, (p.x - 0f), (p.y - 30f), emojiPaint) }
    }
}
