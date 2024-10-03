package com.geeksville.mesh.model.map

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon

class MarkerWithLabel(mapView: MapView?, label: String, emoji: String? = null) : Marker(mapView) {

    companion object {
        private const val LABEL_CORNER_RADIUS = 12F
        private const val LABEL_Y_OFFSET = 100F
    }

    private var nodeColor: Int = Color.GRAY
    fun setNodeColors(colors: Pair<Int, Int>) {
        nodeColor = colors.second
    }

    private var precisionBits: Int? = null
    fun setPrecisionBits(bits: Int) {
        precisionBits = bits
    }

    @Suppress("MagicNumber")
    private fun getPrecisionMeters(): Double? {
        return when (precisionBits) {
            10 -> 23345.484932
            11 -> 11672.7369
            12 -> 5836.36288
            13 -> 2918.175876
            14 -> 1459.0823719999053
            15 -> 729.53562
            16 -> 364.7622
            17 -> 182.375556
            18 -> 91.182212
            19 -> 45.58554
            else -> null
        }
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

    @Suppress("MagicNumber")
    override fun draw(c: Canvas, osmv: MapView?, shadow: Boolean) {
        super.draw(c, osmv, false)
        val p = mPositionPixels
        val bgRect = getTextBackgroundSize(mLabel, (p.x - 0F), (p.y - LABEL_Y_OFFSET))
        bgRect.inset(-8F, -2F)

        if(mLabel.isNotEmpty()) {
            c.drawRoundRect(bgRect, LABEL_CORNER_RADIUS, LABEL_CORNER_RADIUS, bgPaint)
            c.drawText(mLabel, (p.x - 0F), (p.y - LABEL_Y_OFFSET), textPaint)
        }
        mEmoji?.let { c.drawText(it, (p.x - 0f), (p.y - 30f), emojiPaint) }

        getPrecisionMeters()?.let { radius ->
            val polygon = Polygon(osmv).apply {
                points = Polygon.pointsAsCircle(
                    position,
                    radius
                )
                fillPaint.apply {
                    color = nodeColor
                    alpha = 48
                }
                outlinePaint.apply {
                    color = nodeColor
                    alpha = 64
                }
            }
            polygon.draw(c, osmv, false)
        }
    }
}
