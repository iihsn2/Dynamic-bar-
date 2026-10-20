package com.dynamicbar

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var statusA11y: TextView
    private lateinit var statusNotif: TextView

    private var w = 112
    private var h = 32
    private var y = 4

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        w = Prefs.idleW(this)
        h = Prefs.idleH(this)
        y = Prefs.offsetY(this)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dpi(20f), dpi(36f), dpi(20f), dpi(30f))

        root.addView(text("Dynamic Island", 26f, true, Color.WHITE))
        root.addView(text("جزيرة ديناميكية ثابتة مثل الآيفون: موسيقى، إشعارات، وأنيميشن قفل/فتح.", 14f, false, 0xFFAAAAAA.toInt()))

        root.addView(section("1) الصلاحيات"))
        root.addView(text("إذا الإعدادات طلعت رمادية (مقفلة): افتح معلومات التطبيق ← القائمة ⋮ ← \"السماح بالإعدادات المقيّدة\"، وبعدين ارجع.", 13f, false, 0xFFFFC857.toInt()))
        statusA11y = text("", 14f, true, Color.WHITE)
        root.addView(statusA11y)
        root.addView(button("فعّل خدمة الجزيرة (إمكانية الوصول)") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        statusNotif = text("", 14f, true, Color.WHITE)
        root.addView(statusNotif)
        root.addView(button("اسمح بقراءة الإشعارات والموسيقى") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        })
        root.addView(button("بدون قيود البطارية (عشان ما تنقتل الخدمة)") {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        })

        root.addView(section("2) المعايرة على كاميرتك"))
        root.addView(text("عدّل لحد ما الجزيرة تغطي ثقب الكاميرا بالضبط.", 13f, false, 0xFFAAAAAA.toInt()))
        root.addView(seek("العرض", 60, 220, w) { w = it })
        root.addView(seek("الارتفاع", 20, 56, h) { h = it })
        root.addView(seek("المسافة من فوق", -10, 40, y) { y = it })

        root.addView(section("3) جرّب"))
        root.addView(button("جرّب إشعار") { svc()?.demoNotification() })
        root.addView(button("جرّب القفل") { svc()?.demoLock() })
        root.addView(button("جرّب الفتح") { svc()?.demoUnlock() })

        root.addView(section("ملاحظة"))
        root.addView(text("طفّي \"Dynamic Bar\" الأصلية تبع Infinix من الإعدادات حتى ما تتكرر الجزيرة. وقفّل التطبيق بقائمة التطبيقات الأخيرة حتى ما ينقتل بالخلفية.", 13f, false, 0xFFAAAAAA.toInt()))

        val sv = ScrollView(this)
        sv.setBackgroundColor(Color.BLACK)
        sv.addView(root)
        setContentView(sv)
    }

    override fun onResume() {
        super.onResume()
        val a11yOn = (Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: "")
            .contains(ComponentName(this, IslandService::class.java).flattenToString())
        val notifOn = (Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: "")
            .contains(packageName)
        statusA11y.text = if (a11yOn) "✅ الجزيرة شغّالة" else "❌ خدمة الجزيرة مو مفعّلة"
        statusNotif.text = if (notifOn) "✅ قراءة الإشعارات مفعّلة" else "❌ قراءة الإشعارات مو مفعّلة"
    }

    private fun svc(): IslandService? {
        val s = IslandBus.service
        if (s == null) Toast.makeText(this, "فعّل خدمة الجزيرة أولاً", Toast.LENGTH_SHORT).show()
        return s
    }

    private fun text(t: String, sp: Float, bold: Boolean, color: Int): TextView {
        val v = TextView(this)
        v.text = t
        v.textSize = sp
        v.setTextColor(color)
        if (bold) v.setTypeface(v.typeface, android.graphics.Typeface.BOLD)
        v.setPadding(0, dpi(6f), 0, dpi(6f))
        return v
    }

    private fun section(t: String): TextView {
        val v = text(t, 17f, true, Color.WHITE)
        v.setPadding(0, dpi(26f), 0, dpi(4f))
        return v
    }

    private fun button(t: String, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = t
        b.isAllCaps = false
        b.gravity = Gravity.CENTER
        b.setOnClickListener { onClick() }
        return b
    }

    private fun seek(label: String, min: Int, max: Int, start: Int, onValue: (Int) -> Unit): LinearLayout {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        val tv = text("$label: $start", 14f, false, Color.WHITE)
        val sb = SeekBar(this)
        sb.max = max - min
        sb.progress = start - min
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                tv.text = "$label: ${p + min}"
            }
            override fun onStartTrackingTouch(s: SeekBar) {}
            override fun onStopTrackingTouch(s: SeekBar) {
                onValue(s.progress + min)
                Prefs.save(this@MainActivity, w, h, y)
                IslandBus.service?.rebuild()
            }
        })
        box.addView(tv)
        box.addView(sb)
        return box
    }
}
