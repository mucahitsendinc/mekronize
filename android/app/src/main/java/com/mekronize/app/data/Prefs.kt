package com.mekronize.app.data

import android.content.Context
import com.mekronize.app.BuildConfig

class Prefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("mekronize", Context.MODE_PRIVATE)

    var key: String
        get() = sp.getString("key", "") ?: ""
        set(v) = sp.edit().putString("key", v).apply()

    var baseUrl: String
        get() = sp.getString("base", BuildConfig.BASE_URL) ?: BuildConfig.BASE_URL
        set(v) = sp.edit().putString("base", v).apply()

    /** 0 = 360p (hafif), 1 = 720p */
    var quality: Int
        get() = sp.getInt("quality", 0)
        set(v) = sp.edit().putInt("quality", v).apply()

    var wifiOnly: Boolean
        get() = sp.getBoolean("wifi", false)
        set(v) = sp.edit().putBoolean("wifi", v).apply()

    companion object {
        fun normalizeBase(raw: String): String {
            var s = raw.trim().trimEnd('/')
            if (s.isNotEmpty() && !s.contains("://")) s = "https://$s"
            return s
        }
    }
}
