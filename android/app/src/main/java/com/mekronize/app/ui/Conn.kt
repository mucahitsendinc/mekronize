package com.mekronize.app.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.mekronize.app.data.Track
import com.mekronize.app.play.MediaItems
import com.mekronize.app.play.PlaybackService

/** Oynatma servisine tek bağlantı (ana iş parçacığından kullanılır). */
object Conn {
    @Volatile var controller: MediaController? = null
    /** Çalma başlayınca oynatıcı sayfasının tam ekran açılması istendi (video). */
    @Volatile var expandNext = false
    private var future: ListenableFuture<MediaController>? = null
    private val waiting = ArrayList<(MediaController) -> Unit>()

    fun with(ctx: Context, cb: (MediaController) -> Unit) {
        controller?.let { cb(it); return }
        waiting += cb
        if (future != null) return
        val app = ctx.applicationContext
        val f = MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync()
        future = f
        f.addListener({
            try {
                val c = f.get()
                controller = c
                val list = ArrayList(waiting); waiting.clear()
                list.forEach { it(c) }
            } catch (e: Exception) {
                future = null; waiting.clear()
            }
        }, ContextCompat.getMainExecutor(app))
    }

    private const val MAX_QUEUE = 400

    /**
     * Kuyruğu kurup çalar. shuffle=null → mevcut karışık modu korur.
     * Çok büyük listelerde IPC sınırı için çalınan parçanın çevresinden pencere gönderilir.
     */
    fun play(ctx: Context, tracks: List<Track>, index: Int, shuffle: Boolean? = null) {
        if (tracks.isEmpty()) return
        val start = index.coerceIn(0, tracks.size - 1)
        val from = if (tracks.size > MAX_QUEUE) (start - MAX_QUEUE / 4).coerceAtLeast(0).coerceAtMost(tracks.size - MAX_QUEUE) else 0
        val window = tracks.subList(from, minOf(tracks.size, from + MAX_QUEUE))
        with(ctx) { c ->
            c.setMediaItems(window.map { MediaItems.light(it) }, start - from, 0L)
            if (shuffle != null) c.shuffleModeEnabled = shuffle
            c.prepare()
            c.play()
            if (tracks[start].isVideo) expandNext = true
        }
    }

    fun playShuffled(ctx: Context, tracks: List<Track>) {
        if (tracks.isEmpty()) return
        play(ctx, tracks, tracks.indices.random(), true)
    }

    fun openPlayer(ctx: Context) {
        ctx.startActivity(Intent(ctx, PlayerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
