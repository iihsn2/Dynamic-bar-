package com.dynamicbar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.GestureDetector
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.min

/**
 * الجزيرة نفسها: مستطيل أسود بزوايا دائرية بيتحرك حجمه بنابض،
 * والمحتوى (موسيقى/إشعار/قفل) بيظهر ويختفي جواته.
 */
class IslandView(ctx: Context, private val d: Dims) : FrameLayout(ctx) {

    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
    private val rect = RectF()
    private val clip = Path()
    private val maxR = ctx.dp(36f)

    var curW = 0f
    var curH = 0f

    private val sw = SpringValue({ curW = it; invalidate() }, { checkSettled() })
    private val sh = SpringValue({ curH = it; invalidate() }, { checkSettled() })

    var onSettled: (() -> Unit)? = null
    var onTap: (() -> Unit)? = null
    var onLongPress: (() -> Unit)? = null
    var onSwipeUp: (() -> Unit)? = null

    // ---- محتوى الحالة المضغوطة (موسيقى شغالة) ----
    val compact = FrameLayout(ctx)
    private val artSmall = ImageView(ctx)
    private val eq = EqualizerView(ctx)

    // ---- إشعار ----
    val notifC = LinearLayout(ctx)
    private val notifIcon = ImageView(ctx)
    private val notifTitle = TextView(ctx)
    private val notifText = TextView(ctx)

    // ---- مشغّل الموسيقى الموسّع ----
    val musicC = LinearLayout(ctx)
    private val artBig = ImageView(ctx)
    private val mTitle = TextView(ctx)
    private val mArtist = TextView(ctx)
    private val progress = ProgressView(ctx)
    val btnPrev = GlyphView(ctx, GlyphView.PREV)
    val btnPlay = GlyphView(ctx, GlyphView.PLAY)
    val btnNext = GlyphView(ctx, GlyphView.NEXT)
    private val artBigClickArea = artBig

    // ---- قفل ----
    val lockC = FrameLayout(ctx)
    val lockView = LockView(ctx)

    private val gd = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true
        override fun onSingleTapUp(e: MotionEvent): Boolean { onTap?.invoke(); return true }
        override fun onLongPress(e: MotionEvent) { onLongPress?.invoke() }
        override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
            if (vy < -800f) { onSwipeUp?.invoke(); return true }
            return false
        }
    })

    init {
        layoutDirection = LAYOUT_DIRECTION_LTR
        setWillNotDraw(false)
        clipChildren = false
        buildCompact()
        buildNotif()
        buildMusic()
        buildLock()
    }

    private fun place(c: View, w: Float, h: Float) {
        c.visibility = GONE
        c.alpha = 0f
        addView(c, LayoutParams(w.toInt(), h.toInt(), Gravity.TOP or Gravity.CENTER_HORIZONTAL))
    }

    private fun txt(t: TextView, sp: Float, bold: Boolean, alpha: Int, lines: Int) {
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        t.setTextColor(Color.argb(alpha, 255, 255, 255))
        t.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        t.maxLines = lines
        t.ellipsize = TextUtils.TruncateAt.END
        t.textDirection = TEXT_DIRECTION_FIRST_STRONG
        t.textAlignment = TEXT_ALIGNMENT_TEXT_START
        t.includeFontPadding = false
    }

    private fun buildCompact() {
        val s = context.dpi(22f)
        artSmall.scaleType = ImageView.ScaleType.CENTER_CROP
        artSmall.roundClip(context.dp(6f))
        compact.addView(artSmall, LayoutParams(s, s, Gravity.LEFT or Gravity.CENTER_VERTICAL).apply {
            leftMargin = context.dpi(12f)
        })
        compact.addView(eq, LayoutParams(context.dpi(20f), context.dpi(16f), Gravity.RIGHT or Gravity.CENTER_VERTICAL).apply {
            rightMargin = context.dpi(14f)
        })
        place(compact, d.w(IslandState.COMPACT), d.h(IslandState.COMPACT))
    }

    private fun buildNotif() {
        notifC.orientation = LinearLayout.HORIZONTAL
        notifC.gravity = Gravity.CENTER_VERTICAL
        notifC.setPadding(context.dpi(18f), 0, context.dpi(18f), 0)

        notifIcon.scaleType = ImageView.ScaleType.CENTER_CROP
        notifIcon.roundClip(context.dp(12f))
        notifC.addView(notifIcon, LinearLayout.LayoutParams(context.dpi(46f), context.dpi(46f)))

        val col = LinearLayout(context)
        col.orientation = LinearLayout.VERTICAL
        txt(notifTitle, 15f, true, 255, 1)
        txt(notifText, 14f, false, 190, 2)
        col.addView(notifTitle)
        col.addView(notifText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = context.dpi(3f) })
        notifC.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            leftMargin = context.dpi(14f)
        })
        place(notifC, d.w(IslandState.NOTIF), d.h(IslandState.NOTIF))
    }

    private fun buildMusic() {
        musicC.orientation = LinearLayout.VERTICAL
        musicC.setPadding(context.dpi(20f), context.dpi(20f), context.dpi(20f), context.dpi(12f))

        val row = LinearLayout(context)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        artBig.scaleType = ImageView.ScaleType.CENTER_CROP
        artBig.roundClip(context.dp(14f))
        row.addView(artBig, LinearLayout.LayoutParams(context.dpi(64f), context.dpi(64f)))

        val col = LinearLayout(context)
        col.orientation = LinearLayout.VERTICAL
        txt(mTitle, 17f, true, 255, 1)
        txt(mArtist, 14f, false, 170, 1)
        col.addView(mTitle)
        col.addView(mArtist, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = context.dpi(3f) })
        row.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            leftMargin = context.dpi(14f)
        })
        musicC.addView(row)

        musicC.addView(progress, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, context.dpi(5f)
        ).apply { topMargin = context.dpi(14f) })

        val controls = LinearLayout(context)
        controls.orientation = LinearLayout.HORIZONTAL
        controls.gravity = Gravity.CENTER
        val b = context.dpi(56f)
        controls.addView(btnPrev, LinearLayout.LayoutParams(b, b))
        controls.addView(btnPlay, LinearLayout.LayoutParams(b, b).apply {
            leftMargin = context.dpi(18f); rightMargin = context.dpi(18f)
        })
        controls.addView(btnNext, LinearLayout.LayoutParams(b, b))
        musicC.addView(controls, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = context.dpi(4f) })

        place(musicC, d.w(IslandState.MUSIC), d.h(IslandState.MUSIC))
    }

    private fun buildLock() {
        lockC.addView(lockView, LayoutParams(context.dpi(22f), context.dpi(26f), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = context.dpi(7f)
        })
        place(lockC, d.w(IslandState.LOCK), d.h(IslandState.LOCK))
    }

    /** يضغط على الغلاف لفتح تطبيق الموسيقى */
    fun setArtClick(l: View.OnClickListener) { artBigClickArea.setOnClickListener(l) }

    // ---------------- ربط البيانات ----------------

    fun bindMedia(m: MediaInfo) {
        artSmall.setImageBitmap(m.art)
        artBig.setImageBitmap(m.art)
        mTitle.text = m.title
        mArtist.text = m.artist
        eq.color = m.accent
        eq.setPlaying(m.playing)
        btnPlay.set(if (m.playing) GlyphView.PAUSE else GlyphView.PLAY)
    }

    fun bindNotif(n: NotifData) {
        notifIcon.setImageBitmap(n.icon)
        notifTitle.text = n.title
        notifText.text = n.text
        notifText.visibility = if (n.text.isEmpty()) GONE else VISIBLE
    }

    fun setProgress(p: Float) { progress.progress = p }

    // ---------------- الحجم والحركة ----------------

    fun snapTo(w: Float, h: Float) {
        sw.snap(w)
        sh.snap(h)
    }

    fun animateTo(w: Float, h: Float) {
        sw.animateTo(w)
        sh.animateTo(h)
        if (!sw.running && !sh.running) onSettled?.invoke()
    }

    private fun checkSettled() {
        if (!sw.running && !sh.running) onSettled?.invoke()
    }

    private fun container(s: IslandState): View? = when (s) {
        IslandState.COMPACT -> compact
        IslandState.NOTIF -> notifC
        IslandState.MUSIC -> musicC
        IslandState.LOCK -> lockC
        IslandState.IDLE -> null
    }

    /** يبدّل المحتوى: القديم بيختفي بسرعة، الجديد بيظهر بعد ما الجزيرة تبلّش تكبر */
    fun showContent(prev: IslandState, next: IslandState) {
        val expanding = d.w(next) * d.h(next) > d.w(prev) * d.h(prev)
        for (st in listOf(IslandState.COMPACT, IslandState.NOTIF, IslandState.MUSIC, IslandState.LOCK)) {
            val c = container(st) ?: continue
            if (st == next) {
                c.animate().cancel()
                if (c.visibility != VISIBLE) {
                    c.alpha = 0f
                    c.scaleX = 0.9f
                    c.scaleY = 0.9f
                    c.visibility = VISIBLE
                }
                c.animate().alpha(1f).scaleX(1f).scaleY(1f)
                    .setStartDelay(if (expanding) 80L else 0L).setDuration(220).start()
            } else if (c.visibility == VISIBLE) {
                c.animate().cancel()
                c.animate().alpha(0f).setStartDelay(0L).setDuration(110)
                    .withEndAction { if (c.alpha == 0f) c.visibility = GONE }.start()
            }
        }
    }

    // ---------------- الرسم ----------------

    private fun updateShape(): Float {
        val w = curW.coerceAtLeast(1f)
        val h = curH.coerceAtLeast(1f)
        val l = (width - w) / 2f
        rect.set(l, 0f, l + w, h)
        return min(h / 2f, maxR)
    }

    override fun onDraw(canvas: Canvas) {
        val r = updateShape()
        canvas.drawRoundRect(rect, r, r, bg)
    }

    override fun dispatchDraw(canvas: Canvas) {
        val r = updateShape()
        clip.reset()
        clip.addRoundRect(rect, r, r, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clip)
        super.dispatchDraw(canvas)
        canvas.restore()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_DOWN) {
            updateShape()
            if (!rect.contains(e.x, e.y)) return false
        }
        return gd.onTouchEvent(e)
    }
}
