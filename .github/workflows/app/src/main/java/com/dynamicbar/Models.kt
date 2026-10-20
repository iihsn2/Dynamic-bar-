package com.dynamicbar

import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.media.session.MediaController
import kotlin.math.min

enum class IslandState { IDLE, COMPACT, NOTIF, MUSIC, LOCK }

data class NotifData(
    val title: String,
    val text: String,
    val icon: Bitmap?,
    val intent: PendingIntent?,
    val pkg: String
)

data class MediaInfo(
    val title: String,
    val artist: String,
    val art: Bitmap?,
    val accent: Int,
    val playing: Boolean,
    val position: Long,
    val duration: Long,
    val speed: Float,
    val updatedAt: Long,
    val controller: MediaController
)

/** جسر بسيط بين الخدمات (كلها بنفس الـ process) */
object IslandBus {
    @Volatile var service: IslandService? = null
    @Volatile var listener: NotifListener? = null
}

/** أحجام الجزيرة بكل حالة (بالبكسل) */
class Dims(ctx: Context, idleWdp: Int, idleHdp: Int, screenW: Int) {
    val idleW = ctx.dp(idleWdp.toFloat())
    val idleH = ctx.dp(idleHdp.toFloat())

    private val compactW = idleW + ctx.dp(76f)
    private val notifW = min(ctx.dp(350f), screenW - ctx.dp(24f))
    private val notifH = ctx.dp(78f)
    private val musicW = min(ctx.dp(360f), screenW - ctx.dp(24f))
    private val musicH = ctx.dp(178f)
    private val lockW = idleW + ctx.dp(44f)
    private val lockH = idleH + ctx.dp(36f)

    fun w(s: IslandState) = when (s) {
        IslandState.IDLE -> idleW
        IslandState.COMPACT -> compactW
        IslandState.NOTIF -> notifW
        IslandState.MUSIC -> musicW
        IslandState.LOCK -> lockW
    }

    fun h(s: IslandState) = when (s) {
        IslandState.IDLE -> idleH
        IslandState.COMPACT -> idleH
        IslandState.NOTIF -> notifH
        IslandState.MUSIC -> musicH
        IslandState.LOCK -> lockH
    }
}
