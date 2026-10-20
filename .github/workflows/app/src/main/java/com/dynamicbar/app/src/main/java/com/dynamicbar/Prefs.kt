package com.dynamicbar

import android.content.Context

/** إعدادات المعايرة (كلها بالـ dp) */
object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("island", Context.MODE_PRIVATE)

    fun idleW(c: Context) = sp(c).getInt("w", 112)
    fun idleH(c: Context) = sp(c).getInt("h", 32)
    fun offsetY(c: Context) = sp(c).getInt("y", 4)

    fun save(c: Context, w: Int, h: Int, y: Int) {
        sp(c).edit().putInt("w", w).putInt("h", h).putInt("y", y).apply()
    }
}
