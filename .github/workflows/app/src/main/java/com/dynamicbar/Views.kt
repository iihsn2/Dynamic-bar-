package com.dynamicbar

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

/** أعمدة الموسيقى المتحركة (Equalizer) */
class EqualizerView(ctx: Context) : View(ctx) {
    var color: Int = Color.WHITE
        set(v) { field = v; invalidate() }

    private var playing = false
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val anim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 900
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { invalidate() }
    }

    fun setPlaying(p: Boolean) {
        playing = p
        if (p) { if (!anim.isRunning) anim.start() } else { anim.cancel(); invalidate() }
    }

    override fun onDetachedFromWindow() {
        anim.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val n = 4
        val barW = width / (n * 2f - 1f)
        paint.strokeWidth = barW
        paint.color = color
        val t = if (anim.isRunning) anim.animatedFraction else 0f
        for (i in 0 until n) {
            val frac = if (playing) {
                0.25f + 0.75f * abs(sin((t + i * 0.23f) * 2.0 * PI + i)).toFloat()
            } else 0.3f
            val h = (height * frac - barW).coerceAtLeast(1f)
            val cx = barW / 2f + i * 2f * barW
            val top = (height - h) / 2f
            canvas.drawLine(cx, top, cx, top + h, paint)
        }
    }
}

/** أزرار التحكم: تشغيل، إيقاف، التالي، السابق */
class GlyphView(ctx: Context, var glyph: Int) : View(ctx) {
    companion object {
        const val PLAY = 0
        const val PAUSE = 1
        const val NEXT = 2
        const val PREV = 3
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val path = Path()

    init { isClickable = true }

    fun set(g: Int) { if (glyph != g) { glyph = g; invalidate() } }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> animate().alpha(0.5f).scaleX(0.9f).scaleY(0.9f).setDuration(80).start()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(120).start()
        }
        return super.onTouchEvent(e)
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) * 0.3f
        when (glyph) {
            PLAY -> {
                path.reset()
                path.moveTo(cx - r * 0.75f, cy - r)
                path.lineTo(cx + r, cy)
                path.lineTo(cx - r * 0.75f, cy + r)
                path.close()
                canvas.drawPath(path, paint)
            }
            PAUSE -> {
                canvas.drawRoundRect(RectF(cx - r * 0.85f, cy - r, cx - r * 0.2f, cy + r), 3f, 3f, paint)
                canvas.drawRoundRect(RectF(cx + r * 0.2f, cy - r, cx + r * 0.85f, cy + r), 3f, 3f, paint)
            }
            NEXT, PREV -> {
                canvas.save()
                if (glyph == PREV) canvas.scale(-1f, 1f, cx, cy)
                path.reset()
                path.moveTo(cx - r, cy - r * 0.8f)
                path.lineTo(cx + r * 0.25f, cy)
                path.lineTo(cx - r, cy + r * 0.8f)
                path.close()
                canvas.drawPath(path, paint)
                canvas.drawRoundRect(RectF(cx + r * 0.4f, cy - r * 0.8f, cx + r * 0.85f, cy + r * 0.8f), 3f, 3f, paint)
                canvas.restore()
            }
        }
    }
}

/** شريط تقدّم الأغنية */
class ProgressView(ctx: Context) : View(ctx) {
    var progress = 0f
        set(v) { field = v.coerceIn(0f, 1f); invalidate() }

    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(70, 255, 255, 255) }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

    override fun onDraw(canvas: Canvas) {
        val r = height / 2f
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), r, r, track)
        if (progress > 0f) {
            canvas.drawRoundRect(0f, 0f, (width * progress).coerceAtLeast(height.toFloat()), height.toFloat(), r, r, fill)
        }
    }
}

/** أيقونة القفل مع حركة الفتح (open: 0 = مقفول، 1 = مفتوح) */
class LockView(ctx: Context) : View(ctx) {
    var open = 0f
        set(v) { field = v; invalidate() }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()
    private val arc = RectF()

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val bodyW = w * 0.66f
        val bodyH = h * 0.46f
        val bodyL = (w - bodyW) / 2f
        val bodyT = h - bodyH - h * 0.03f
        canvas.drawRoundRect(bodyL, bodyT, bodyL + bodyW, bodyT + bodyH, w * 0.12f, w * 0.12f, fill)

        stroke.strokeWidth = w * 0.11f
        val sw = bodyW * 0.62f
        val left = (w - sw) / 2f
        val right = left + sw
        val top = bodyT - h * 0.32f
        arc.set(left, top, right, top + sw)

        path.reset()
        path.moveTo(left, bodyT)
        path.lineTo(left, top + sw / 2f)
        path.arcTo(arc, 180f, 180f)
        path.lineTo(right, bodyT)

        canvas.save()
        canvas.translate(0f, -open * h * 0.13f)
        canvas.rotate(-open * 26f, left, bodyT)
        canvas.drawPath(path, stroke)
        canvas.restore()
    }
}
