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

    override fun onAcces
