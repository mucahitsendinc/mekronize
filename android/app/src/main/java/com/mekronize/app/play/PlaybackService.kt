package com.mekronize.app.play

import android.app.PendingIntent
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.mekronize.app.data.Repo
import com.mekronize.app.data.Storage
import com.mekronize.app.data.Track
import com.mekronize.app.ui.MainActivity

class PlaybackService : MediaLibraryService() {
    private lateinit var player: ExoPlayer
    private var session: MediaLibrarySession? = null
    private val handler = Handler(Looper.getMainLooper())

    // Çalma sayacı: bir parça min(30 sn, süresinin yarısı) çalınınca 1 sayılır.
    private var countedFor: String? = null
    private var anchorPending = false
    private val ticker = object : Runnable {
        override fun run() {
            checkCount()
            if (player.isPlaying) handler.postDelayed(this, 2000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Repo.init(applicationContext)

        // Yerel dosya çaldığımız için küçük tamponlar: düşük RAM'li cihazlarda hafif.
        val load = DefaultLoadControl.Builder()
            .setBufferDurationsMs(2_000, 6_000, 500, 1_000)
            .setTargetBufferBytes(4 * 1024 * 1024)
            .setPrioritizeTimeOverSizeThresholds(false)
            .build()

        player = ExoPlayer.Builder(this, DefaultRenderersFactory(this).setEnableDecoderFallback(true), SidecarSourceFactory(this))
            .setLoadControl(load)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        player.addListener(listener)

        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaLibrarySession.Builder(this, player, LibraryCallback()).setSessionActivity(open).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        session?.release(); session = null
        player.release()
        super.onDestroy()
    }

    private fun checkCount() {
        val item = player.currentMediaItem ?: return
        val id = item.mediaId
        if (countedFor == id) return
        val dur = player.duration
        val limit = if (dur > 0) minOf(30_000L, dur / 2) else 30_000L
        if (player.currentPosition >= limit) {
            countedFor = id
            val trackId = id.substringAfterLast(':').toLongOrNull() ?: return
            Repo.incrementPlay(trackId)
        }
    }

    /** Karışık modda geçerli parça başa alınır; sıra sabittir, bu yüzden ⏮ önceki çalınana döner. */
    private fun anchorShuffle() {
        val n = player.mediaItemCount
        if (!player.shuffleModeEnabled || n < 2) return
        val cur = player.currentMediaItemIndex
        val rest = (0 until n).filter { it != cur }.shuffled()
        player.setShuffleOrder(ShuffleOrder.DefaultShuffleOrder(intArrayOf(cur) + rest.toIntArray(), System.nanoTime()))
    }

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            countedFor = null
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            handler.removeCallbacks(ticker)
            if (isPlaying) handler.post(ticker)
        }

        /** Video çözülemezse (eski/zayıf cihaz) aynı içeriğin sesiyle devam et; o da yoksa sıradakine geç. */
        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            val item = player.currentMediaItem ?: return
            val path = item.localConfiguration?.uri?.path
            val audio = path?.takeIf { it.endsWith(".v.mp4") }?.let { java.io.File(it.removeSuffix(".v.mp4") + ".a.m4a") }
            val idx = player.currentMediaItemIndex
            if (audio != null && audio.exists()) {
                player.replaceMediaItem(idx, item.buildUpon().setUri(android.net.Uri.fromFile(audio)).build())
                player.prepare(); player.play()
            } else if (player.hasNextMediaItem()) {
                player.seekToNextMediaItem(); player.prepare(); player.play()
            }
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            if (shuffleModeEnabled) anchorShuffle()
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            if (anchorPending && !timeline.isEmpty && reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
                anchorPending = false
                anchorShuffle()
            }
        }
    }

    // ------------------------------------------------------------------ kütüphane / Android Auto

    private fun fileOf(t: Track) = t.file?.let { Storage.file(this, it) }?.takeIf { it.exists() }

    private fun listFor(ctx: String): List<Track> = when {
        ctx == MediaItems.ALL -> Repo.audioOnly(Repo.all())
        ctx == MediaItems.MUSIC -> Repo.music()
        ctx == MediaItems.FAV -> Repo.audioOnly(Repo.favorites())
        ctx.startsWith(MediaItems.PL_PREFIX) -> Repo.audioOnly(Repo.playlistTracks(ctx.removePrefix(MediaItems.PL_PREFIX).toLong()))
        else -> emptyList()
    }

    private inner class LibraryCallback : MediaLibrarySession.Callback {
        override fun onGetLibraryRoot(
            session: MediaLibrarySession, browser: MediaSession.ControllerInfo, params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(LibraryResult.ofItem(MediaItems.folder(MediaItems.ROOT, "Mekronize"), params))

        override fun onGetItem(
            session: MediaLibrarySession, browser: MediaSession.ControllerInfo, mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val id = mediaId.substringAfterLast(':').toLongOrNull()
            val t = id?.let { Repo.track(it) }
            val f = t?.let { fileOf(it) }
            return if (t != null && f != null) Futures.immediateFuture(LibraryResult.ofItem(MediaItems.full(t, f, mediaId), null))
            else Futures.immediateFuture(LibraryResult.ofError(androidx.media3.session.SessionError.ERROR_BAD_VALUE))
        }

        override fun onGetChildren(
            session: MediaLibrarySession, browser: MediaSession.ControllerInfo, parentId: String,
            page: Int, pageSize: Int, params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val items: List<MediaItem> = when {
                parentId == MediaItems.ROOT -> listOf(
                    MediaItems.folder(MediaItems.PLAYLISTS, "Playlistler"),
                    MediaItems.folder(MediaItems.ALL, "Tümü"),
                    MediaItems.folder(MediaItems.MUSIC, "Müzikler"),
                    MediaItems.folder(MediaItems.FAV, "Favoriler"),
                )
                parentId == MediaItems.PLAYLISTS -> Repo.playlists().map { MediaItems.playlistFolder(it) }
                else -> listFor(parentId).mapNotNull { t -> fileOf(t)?.let { MediaItems.browseTrack(parentId, t, it) } }
            }
            val from = (page * pageSize).coerceAtMost(items.size)
            val to = (from + pageSize).coerceAtMost(items.size)
            return Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.copyOf(items.subList(from, to)), params))
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            val out = mediaItems.mapNotNull { resolve(it) }.toMutableList()
            return Futures.immediateFuture(out)
        }

        override fun onSetMediaItems(
            mediaSession: MediaSession, controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            anchorPending = true
            // Android Auto tek parça seçer → ilgili listenin tamamını kuyruğa al.
            if (mediaItems.size == 1 && mediaItems[0].mediaId.startsWith(MediaItems.AUTO_PREFIX)) {
                val parts = mediaItems[0].mediaId.removePrefix(MediaItems.AUTO_PREFIX).split(':', limit = 2)
                if (parts.size == 2) {
                    val ctx = parts[0]
                    val tracks = listFor(ctx).filter { fileOf(it) != null }
                    val idx = tracks.indexOfFirst { it.id.toString() == parts[1] }
                    if (idx >= 0) {
                        val items = tracks.map { MediaItems.full(it, fileOf(it)!!) }.toMutableList()
                        return Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(items, idx, startPositionMs))
                    }
                }
            }
            val resolved = mediaItems.mapNotNull { resolve(it) }.toMutableList()
            val start = if (startIndex in resolved.indices) startIndex else 0
            return Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(resolved, start, startPositionMs))
        }

        private fun resolve(item: MediaItem): MediaItem? {
            if (item.localConfiguration != null) return item
            val id = item.mediaId.substringAfterLast(':').toLongOrNull() ?: return null
            val t = Repo.track(id) ?: return null
            val f = fileOf(t) ?: return null
            return MediaItems.full(t, f, id.toString())
        }
    }
}
