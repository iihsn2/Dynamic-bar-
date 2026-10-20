package com.dynamicbar

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * بيقرأ الإشعارات وجلسات الموسيقى وبيبعتها للجزيرة.
 */
class NotifListener : NotificationListenerService() {

    private var msm: MediaSessionManager? = null
    private var watched: List<MediaController> = emptyList()
    private var current: MediaController? = null
    private val lastShown = HashMap<String, Int>()

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        watch(list ?: emptyList())
    }

    private val cb = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) { repick() }
        override fun onMetadataChanged(metadata: MediaMetadata?) { publish() }
        override fun onSessionDestroyed() { repick() }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        IslandBus.listener = this
        val cn = ComponentName(this, NotifListener::class.java)
        try {
            val m = getSystemService(MediaSessionManager::class.java)
            msm = m
            m.addOnActiveSessionsChangedListener(sessionsListener, cn)
            watch(m.getActiveSessions(cn))
        } catch (_: SecurityException) {
        }
    }

    override fun onListenerDisconnected() {
        unwatch()
        try { msm?.removeOnActiveSessionsChangedListener(sessionsListener) } catch (_: Exception) {}
        if (IslandBus.listener === this) IslandBus.listener = null
        super.onListenerDisconnected()
    }

    // ------------------------------------------------------------------
    // الموسيقى
    // ------------------------------------------------------------------

    private fun unwatch() {
        for (c in watched) try { c.unregisterCallback(cb) } catch (_: Exception) {}
        watched = emptyList()
    }

    private fun watch(list: List<MediaController>) {
        unwatch()
        watched = list
        for (c in list) try { c.registerCallback(cb) } catch (_: Exception) {}
        repick()
    }

    private fun repick() {
        val playing = watched.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
        current = playing ?: current?.takeIf { it in watched } ?: watched.firstOrNull()
        publish()
    }

    fun republish() = publish()

    private fun publish() {
        val c = current
        if (c == null) {
            IslandBus.service?.onMedia(null)
            return
        }
        val md = c.metadata
        val ps = c.playbackState

        var art: Bitmap? = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        if (art == null) {
            art = try { drawableToBitmap(packageManager.getApplicationIcon(c.packageName)) } catch (_: Exception) { null }
        }

        val info = MediaInfo(
            title = md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "",
            artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: "",
            art = art,
            accent = accentOf(art),
            playing = ps?.state == PlaybackState.STATE_PLAYING,
            position = ps?.position ?: 0L,
            duration = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
            speed = ps?.playbackSpeed ?: 1f,
            updatedAt = ps?.lastPositionUpdateTime ?: SystemClock.elapsedRealtime(),
            controller = c
        )
        IslandBus.service?.onMedia(info)
    }

    private fun accentOf(b: Bitmap?): Int {
        if (b == null) return Color.WHITE
        return try {
            val px = Bitmap.createScaledBitmap(b, 1, 1, true).getPixel(0, 0)
            val hsv = FloatArray(3)
            Color.colorToHSV(px, hsv)
            hsv[1] = hsv[1].coerceAtLeast(0.35f)
            hsv[2] = hsv[2].coerceAtLeast(0.9f)
            Color.HSVToColor(hsv)
        } catch (_: Exception) {
            Color.WHITE
        }
    }

    // ------------------------------------------------------------------
    // الإشعارات
    // ------------------------------------------------------------------

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val n = sbn.notification ?: return
        if (sbn.packageName == packageName) return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (n.flags and Notification.FLAG_ONGOING_EVENT != 0) return
        if (n.category == Notification.CATEGORY_TRANSPORT) return
        if (n.category == Notification.CATEGORY_PROGRESS) return

        // تجاهل الإشعارات الصامتة
        try {
            val r = Ranking()
            if (currentRanking.getRanking(sbn.key, r) && r.importance < NotificationManager.IMPORTANCE_DEFAULT) return
        } catch (_: Exception) {
        }

        val e = n.extras ?: return
        val title = e.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = (e.getCharSequence(Notification.EXTRA_TEXT)
            ?: e.getCharSequence(Notification.EXTRA_BIG_TEXT))?.toString()?.trim().orEmpty()
        if (title.isEmpty() && text.isEmpty()) return

        val hash = (title + "\u0000" + text).hashCode()
        if (lastShown[sbn.key] == hash) return
        lastShown[sbn.key] = hash

        var icon: Bitmap? = null
        try {
            val d = n.getLargeIcon()?.loadDrawable(this)
            if (d != null) icon = drawableToBitmap(d)
        } catch (_: Exception) {
        }
        if (icon == null) {
            icon = try { drawableToBitmap(packageManager.getApplicationIcon(sbn.packageName)) } catch (_: Exception) { null }
        }

        IslandBus.service?.onNotification(
            NotifData(
                title = title.ifEmpty { text },
                text = if (title.isEmpty()) "" else text,
                icon = icon,
                intent = n.contentIntent,
                pkg = sbn.packageName
            )
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        lastShown.remove(sbn.key)
    }
}
