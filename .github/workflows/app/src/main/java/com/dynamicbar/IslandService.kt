package com.dynamicbar

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.animation.OvershootInterpolator
import android.animation.ValueAnimator
import kotlin.math.max

/**
 * خدمة إمكانية الوصول: بترسم الجزيرة فوق كل شي (TYPE_ACCESSIBILITY_OVERLAY)
 * وبتضل ثابتة فوق بكل التطبيقات، وبتدير الحالات والانيميشن.
 */
class IslandService : AccessibilityService() {

    private lateinit var wm: WindowManager
    private var view: IslandView? = null
    private var lp: WindowManager.LayoutParams? = null
    private lateinit var dims: Dims

    private val handler = Handler(Looper.getMainLooper())
    private var state = IslandState.IDLE
    private var media: MediaInfo? = null
    private var notif: NotifData? = null
    private var landscape = false
    private var receiverRegistered = false

    private val padX get() = dpi(16f)
    private val padY get() = dpi(18f)

    private val baseFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

    private val restRunnable = Runnable { setState(rest()) }
    private val openLockRunnable = Runnable { animateLockOpen() }

    private val ticker = object : Runnable {
        override fun run() {
            val m = media
            if (state == IslandState.MUSIC && m != null) {
                val pos = if (m.playing)
                    m.position + ((SystemClock.elapsedRealtime() - m.updatedAt) * m.speed).toLong()
                else m.position
                view?.setProgress(if (m.duration > 0) pos.toFloat() / m.duration else 0f)
                handler.postDelayed(this, 400)
            }
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                Intent.ACTION_SCREEN_OFF -> resetQuietly()
                Intent.ACTION_SCREEN_ON -> {
                    val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                    if (km.isKeyguardLocked) playLock(false)
                }
                Intent.ACTION_USER_PRESENT -> playLock(true)
            }
        }
    }

    // ------------------------------------------------------------------
    // دورة حياة الخدمة
    // ------------------------------------------------------------------

    override fun onServiceConnected() {
        super.onServiceConnected()
        IslandBus.service = this
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        build()

        val f = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, f, Context.RECEIVER_EXPORTED)
        else registerReceiver(receiver, f)
        receiverRegistered = true

        IslandBus.listener?.republish()
    }

        override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        landscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (view != null) rebuild()
    }

    override fun onDestroy() {
        IslandBus.service = null
        if (receiverRegistered) {
            try { unregisterReceiver(receiver) } catch (_: Exception) {}
            receiverRegistered = false
        }
        teardown()
        super.onDestroy()
    }

    // ------------------------------------------------------------------
    // بناء النافذة
    // ------------------------------------------------------------------

    fun rebuild() {
        handler.post {
            val keepMedia = media
            build()
            if (keepMedia != null) onMedia(keepMedia)
        }
    }

    private fun teardown() {
        handler.removeCallbacksAndMessages(null)
        view?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        view = null
        lp = null
    }
        private fun build() {
        teardown()
        state = IslandState.IDLE
        val m = resources.displayMetrics
        dims = Dims(this, Prefs.idleW(this), Prefs.idleH(this), m.widthPixels)

        val v = IslandView(this, dims)
        v.snapTo(dims.idleW, dims.idleH)
        v.onTap = { onTap() }
        v.onLongPress = { if (state == IslandState.COMPACT || state == IslandState.MUSIC) openMusicApp() }
        v.onSwipeUp = { if (state == IslandState.NOTIF || state == IslandState.MUSIC) setState(rest()) }
        v.btnPlay.setOnClickListener {
            val c = media?.controller?.transportControls ?: return@setOnClickListener
            if (media?.playing == true) c.pause() else c.play()
            keepMusicOpen()
        }
        v.btnNext.setOnClickListener { media?.controller?.transportControls?.skipToNext(); keepMusicOpen() }
        v.btnPrev.setOnClickListener { media?.controller?.transportControls?.skipToPrevious(); keepMusicOpen() }
        v.setArtClick { openMusicApp() }

        val p = WindowManager.LayoutParams(
            (dims.idleW + 2 * padX).toInt(),
            (dims.idleH + padY).toInt(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            baseFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        )
        p.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        p.y = dpi(Prefs.offsetY(this).toFloat())
        p.title = "DynamicIsland"
        if (Build.VERSION.SDK_INT >= 30) {
            p.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else if (Build.VERSION.SDK_INT >= 28) {
            p.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        view = v
        lp = p
        v.visibility = if (landscape) android.view.View.GONE else android.view.View.VISIBLE
        try { wm.addView(v, p) } catch (_: Exception) {}
    }

    private fun applyWindow(w: Float, h: Float, touchable: Boolean) {
        val v = view ?: return
        val p = lp ?: return
        p.width = w.toInt()
        p.height = h.toInt()
        p.flags = baseFlags or (if (touchable) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        try { wm.updateViewLayout(v, p) } catch (_: Exception) {}
    }
        private fun rest(): IslandState = if (media?.playing == true) IslandState.COMPACT else IslandState.IDLE

    private fun setState(s: IslandState) {
        val v = view ?: return
        val p = lp ?: return
        if (s == state) return
        val prev = state
        state = s

        applyWindow(
            max(p.width.toFloat(), dims.w(s) + 2 * padX),
            max(p.height.toFloat(), dims.h(s) + padY),
            s != IslandState.IDLE
        )
        v.showContent(prev, s)
        v.onSettled = {
            if (state == s) applyWindow(dims.w(s) + 2 * padX, dims.h(s) + padY, s != IslandState.IDLE)
        }
        v.animateTo(dims.w(s), dims.h(s))

        handler.removeCallbacks(ticker)
        if (s == IslandState.MUSIC) {
            ticker.run()
            postRest(9000)
        }
    }

    private fun postRest(delay: Long) {
        handler.removeCallbacks(restRunnable)
        handler.postDelayed(restRunnable, delay)
    }

    private fun keepMusicOpen() { if (state == IslandState.MUSIC) postRest(9000) }

    private fun resetQuietly() {
        handler.removeCallbacks(restRunnable)
        handler.removeCallbacks(openLockRunnable)
        setState(rest())
    }

    private fun onTap() {
        when (state) {
            IslandState.COMPACT -> if (media != null) setState(IslandState.MUSIC)
            IslandState.MUSIC -> setState(rest())
            IslandState.NOTIF -> openNotification()
            else -> {}
        }
    }

    private fun openNotification() {
        val n = notif
        if (n != null) {
            try {
                if (n.intent != null) n.intent.send()
                else packageManager.getLaunchIntentForPackage(n.pkg)?.let {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(it)
                }
            } catch (_: Exception) {
                packageManager.getLaunchIntentForPackage(n.pkg)?.let {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try { startActivity(it) } catch (_: Exception) {}
                }
            }
        }
        handler.removeCallbacks(restRunnable)
        setState(rest())
    }

    private fun openMusicApp() {
        val pkg = media?.controller?.packageName ?: return
        packageManager.getLaunchIntentForPackage(pkg)?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try { startActivity(it) } catch (_: Exception) {}
        }
        setState(rest())
    }

    fun onMedia(info: MediaInfo?) {
        handler.post {
            media = info
            if (info != null) view?.bindMedia(info)
            when (state) {
                IslandState.IDLE, IslandState.COMPACT -> setState(rest())
                IslandState.MUSIC -> if (info == null) setState(rest())
                else -> {}
            }
        }
    }

    fun onNotification(n: NotifData) {
        handler.post {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isInteractive || landscape) return@post
            notif = n
            view?.bindNotif(n)
            handler.removeCallbacks(openLockRunnable)
            setState(IslandState.NOTIF)
            postRest(4500)
        }
    }

    private fun playLock(unlock: Boolean) {
        handler.post {
            val v = view ?: return@post
            if (landscape) return@post
            handler.removeCallbacks(restRunnable)
            handler.removeCallbacks(openLockRunnable)
            if (state != IslandState.LOCK) v.lockView.open = 0f
            setState(IslandState.LOCK)
            if (unlock) {
                handler.postDelayed(openLockRunnable, 260)
                postRest(1350)
            } else {
                postRest(1500)
            }
        }
    }

    private fun animateLockOpen() {
        val lv = view?.lockView ?: return
        ValueAnimator.ofFloat(lv.open, 1f).apply {
            duration = 380
            interpolator = OvershootInterpolator(2.2f)
            addUpdateListener { lv.open = it.animatedValue as Float }
            start()
        }
    }

    fun demoNotification() {
        val icon = try { drawableToBitmap(packageManager.getApplicationIcon(packageName)) } catch (_: Exception) { null }
        onNotification(NotifData("Dynamic Island", "هيك رح تظهر الإشعارات عندك 🎉", icon, null, packageName))
    }

    fun demoLock() = playLock(false)
    fun demoUnlock() = playLock(true)
}
