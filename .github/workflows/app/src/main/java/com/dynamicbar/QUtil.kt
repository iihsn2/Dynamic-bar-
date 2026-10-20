package com.dynamicbar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.view.Choreographer
import android.view.View
import android.view.ViewOutlineProvider
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

fun Context.dp(v: Float): Float = v * resources.displayMetrics.density
fun Context.dpi(v: Float): Int = (v * resources.displayMetrics.density).toInt()

/** يقصّ الـ View بزوايا دائرية */
fun View.roundClip(radiusPx: Float) {
    outlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setRoundRect(0, 0, view.width, view.height, radiusPx)
        }
    }
    clipToOutline = true
}

fun drawableToBitmap(d: Drawable, size: Int = 128): Bitmap {
    if (d is BitmapDrawable && d.bitmap != null) return d.bitmap
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    d.setBounds(0, 0, size, size)
    d.draw(c)
    return bmp
}

/**
 * نابض (Spring) بسيط بدون أي مكتبة — هاد اللي بيعطي الحركة "المطاطية"
 * اللي بتشبه Dynamic Island تبع الآيفون.
 */
class SpringValue(
    private val onUpdate: (Float) -> Unit,
    private val onRest: () -> Unit,
    private val stiffness: Float = 380f,
    dampingRatio: Float = 0.68f
) {
    var value = 0f
        private set
    var running = false
        private set

    private var velocity = 0f
    private var target = 0f
    private var lastNanos = 0L
    private val damping = 2f * dampingRatio * sqrt(stiffness)

    private val frame = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            val dt = min((frameTimeNanos - lastNanos) / 1_000_000_000f, 0.032f)
            lastNanos = frameTimeNanos
            var rem = dt
            while (rem > 0f) {
                val s = min(rem, 1f / 240f)
                val a = -stiffness * (value - target) - damping * velocity
                velocity += a * s
                value += velocity * s
                rem -= s
            }
            if (abs(value - target) < 0.4f && abs(velocity) < 8f) {
                value = target
                velocity = 0f
                running = false
                onUpdate(value)
                onRest()
            } else {
                onUpdate(value)
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
    }

    fun snap(v: Float) {
        Choreographer.getInstance().removeFrameCallback(frame)
        running = false
        value = v
        target = v
        velocity = 0f
        onUpdate(v)
    }

    fun animateTo(t: Float) {
        target = t
        if (!running && abs(value - t) < 0.4f) return
        if (!running) {
            running = true
            lastNanos = System.nanoTime()
            Choreographer.getInstance().postFrameCallback(frame)
        }
    }

    fun cancel() {
        Choreographer.getInstance().removeFrameCallback(frame)
        running = false
    }
}
