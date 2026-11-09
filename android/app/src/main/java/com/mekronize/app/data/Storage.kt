package com.mekronize.app.data

import android.content.Context
import java.io.File

/** Uygulamanın kendi klasörü: izin gerektirmez, uygulama silinince temizlenir. */
object Storage {
    fun dir(ctx: Context): File {
        val base = ctx.applicationContext.getExternalFilesDir(null) ?: ctx.applicationContext.filesDir
        return File(base, "Mekronize").also { it.mkdirs() }
    }

    fun file(ctx: Context, name: String) = File(dir(ctx), name)

    /** Bir içeriğe ait tüm dosyaları (ana dosya, ses yan dosyası, yarım parçalar) siler. */
    fun removeFor(ctx: Context, id: Long) {
        dir(ctx).listFiles()?.forEach { if (it.name.startsWith("$id.")) it.delete() }
    }
}
