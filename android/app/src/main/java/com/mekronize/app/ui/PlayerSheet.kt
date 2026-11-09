package com.mekronize.app.ui

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.ui.PlayerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.mekronize.app.R
import com.mekronize.app.data.Repo

/**
 * Her ekranın altındaki oynatıcı: küçükken kendi ileri/geri/oynat kontrolleri ve ilerleme çizgisi var,
 * tıklayınca ya da yukarı çekince tam ekran oynatıcıya büyür (kapak/video, ilerleme çubuğu, karışık, tekrar, favori).
 */
class PlayerSheet(private val act: Activity) {
    private val sheet: View = act.findViewById(R.id.sheet)
    private val miniBar: View = act.findViewById(R.id.miniBar)
    private val full: View = act.findViewById(R.id.full)
    private val behavior = BottomSheetBehavior.from(sheet)

    private val miniIcon: ImageView = act.findViewById(R.id.miniIcon)
    private val miniTitle: TextView = act.findViewById(R.id.miniTitle)
    private val miniSub: TextView = act.findViewById(R.id.miniSub)
    private val miniPlay: ImageButton = act.findViewById(R.id.miniPlay)
    private val miniProgress: android.widget.ProgressBar = act.findViewById(R.id.miniProgress)

    private val art: ImageView = act.findViewById(R.id.art)
    private val video: PlayerView = act.findViewById(R.id.video)
    private val fullTitle: TextView = act.findViewById(R.id.fullTitle)
    private val fullSub: TextView = act.findViewById(R.id.fullSub)
    private val seek: SeekBar = act.findViewById(R.id.seek)
    private val pos: TextView = act.findViewById(R.id.pos)
    private val dur: TextView = act.findViewById(R.id.dur)
    private val play: ImageButton = act.findViewById(R.id.play)
    private val shuffle: ImageButton = act.findViewById(R.id.shuffle)
    private val repeat: ImageButton = act.findViewById(R.id.repeat)
    private val heart: ImageButton = act.findViewById(R.id.heart)
    private val fullscreen: ImageButton = act.findViewById(R.id.fullscreen)

    private var ctrl: MediaController? = null
    private var dragging = false
    private var started = false
    private val handler = Handler(Looper.getMainLooper())

    private val ticker = object : Runnable {
        override fun run() {
            tick()
            if (started) handler.postDelayed(this, 500)
        }
    }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refresh()
    }

    private val back = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() { behavior.state = BottomSheetBehavior.STATE_COLLAPSED }
    }

    init {
        behavior.isHideable = true
        behavior.skipCollapsed = false
        behavior.state = BottomSheetBehavior.STATE_HIDDEN
        behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(v: View, newState: Int) {
                back.isEnabled = newState == BottomSheetBehavior.STATE_EXPANDED
                if (newState == BottomSheetBehavior.STATE_COLLAPSED || newState == BottomSheetBehavior.STATE_HIDDEN) {
                    full.visibility = View.INVISIBLE; miniBar.alpha = 1f
                }
                attachVideo()
            }

            override fun onSlide(v: View, offset: Float) {
                if (offset < 0f) return
                miniBar.alpha = (1f - offset * 3f).coerceIn(0f, 1f)
                full.visibility = if (offset > 0.02f) View.VISIBLE else View.INVISIBLE
                full.alpha = ((offset - 0.25f) / 0.75f).coerceIn(0f, 1f)
                miniBar.visibility = if (miniBar.alpha <= 0.01f) View.INVISIBLE else View.VISIBLE
            }
        })
        (act as? ComponentActivity)?.onBackPressedDispatcher?.addCallback(act, back)

        miniBar.setOnClickListener { behavior.state = BottomSheetBehavior.STATE_EXPANDED }
        act.findViewById<View>(R.id.collapse).setOnClickListener { behavior.state = BottomSheetBehavior.STATE_COLLAPSED }
        act.findViewById<View>(R.id.miniPrev).setOnClickListener { ctrl?.seekToPreviousMediaItem() }
        act.findViewById<View>(R.id.miniNext).setOnClickListener { ctrl?.seekToNextMediaItem() }
        act.findViewById<View>(R.id.prev).setOnClickListener { ctrl?.seekToPreviousMediaItem() }
        act.findViewById<View>(R.id.next).setOnClickListener { ctrl?.seekToNextMediaItem() }
        val toggle = View.OnClickListener { ctrl?.let { if (it.isPlaying) it.pause() else it.play() } }
        miniPlay.setOnClickListener(toggle); play.setOnClickListener(toggle)
        shuffle.setOnClickListener { ctrl?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }
        repeat.setOnClickListener {
            ctrl?.let {
                it.repeatMode = when (it.repeatMode) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                    else -> Player.REPEAT_MODE_OFF
                }
            }
        }
        heart.setOnClickListener {
            val id = currentId() ?: return@setOnClickListener
            Repo.toggleFavorite(id); refresh()
        }
        fullscreen.setOnClickListener { Conn.openPlayer(act) }
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                if (fromUser) ctrl?.let { c -> val d = c.duration; if (d > 0) pos.text = fmt(d * p / 1000) }
            }
            override fun onStartTrackingTouch(s: SeekBar) { dragging = true }
            override fun onStopTrackingTouch(s: SeekBar) {
                dragging = false
                ctrl?.let { c -> val d = c.duration; if (d > 0) c.seekTo(d * s.progress / 1000) }
            }
        })
    }

    fun start() {
        started = true
        Conn.with(act) { c ->
            ctrl?.removeListener(listener)
            ctrl = c
            c.addListener(listener)
            refresh()
        }
        handler.post(ticker)
    }

    fun stop() {
        started = false
        handler.removeCallbacks(ticker)
        ctrl?.removeListener(listener)
        ctrl = null
        video.player = null
    }

    private fun currentId(): Long? =
        ctrl?.currentMediaItem?.mediaId?.substringAfterLast(':')?.toLongOrNull()

    private fun attachVideo() {
        val c = ctrl
        val isVideo = c != null && c.mediaMetadata.mediaType == MediaMetadata.MEDIA_TYPE_VIDEO
        val expanded = behavior.state != BottomSheetBehavior.STATE_COLLAPSED && behavior.state != BottomSheetBehavior.STATE_HIDDEN
        if (isVideo && expanded) { video.player = c; video.visibility = View.VISIBLE; art.visibility = View.GONE }
        else { video.player = null; video.visibility = View.GONE; art.visibility = View.VISIBLE }
    }

    private fun refresh() {
        val c = ctrl ?: return
        if (c.mediaItemCount == 0) {
            behavior.state = BottomSheetBehavior.STATE_HIDDEN
            return
        }
        if (behavior.state == BottomSheetBehavior.STATE_HIDDEN) behavior.state = BottomSheetBehavior.STATE_COLLAPSED
        if (Conn.expandNext) { Conn.expandNext = false; behavior.state = BottomSheetBehavior.STATE_EXPANDED }

        val isVideo = c.mediaMetadata.mediaType == MediaMetadata.MEDIA_TYPE_VIDEO
        val title = (c.mediaMetadata.title ?: "").toString()
        val sub = if (isVideo) "Video" else "Müzik"
        miniTitle.text = title; fullTitle.text = title
        miniSub.text = sub; fullSub.text = sub
        miniIcon.setImageResource(if (isVideo) R.drawable.ic_video else R.drawable.ic_music)
        val playIcon = if (c.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        miniPlay.setImageResource(playIcon); play.setImageResource(playIcon)
        fullscreen.visibility = if (isVideo) View.VISIBLE else View.GONE
        tint(shuffle, c.shuffleModeEnabled)
        tint(repeat, c.repeatMode != Player.REPEAT_MODE_OFF)
        repeat.setImageResource(if (c.repeatMode == Player.REPEAT_MODE_ONE) R.drawable.ic_repeat_one else R.drawable.ic_repeat)
        val fav = currentId()?.let { Repo.track(it)?.favorite } == true
        heart.setImageResource(if (fav) R.drawable.ic_heart else R.drawable.ic_heart_outline)
        heart.imageTintList = android.content.res.ColorStateList.valueOf(
            ContextCompat.getColor(act, if (fav) R.color.heart else R.color.muted),
        )
        attachVideo()
        tick()
    }

    private fun tint(b: ImageButton, on: Boolean) {
        b.imageTintList = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(act, if (on) R.color.accent else R.color.text))
    }

    private fun tick() {
        val c = ctrl ?: return
        val d = c.duration
        val p = c.currentPosition
        if (d == C.TIME_UNSET || d <= 0) {
            miniProgress.progress = 0
            if (!dragging) { seek.progress = 0; pos.text = fmt(p); dur.text = "--:--" }
            return
        }
        val frac = (p * 1000 / d).toInt().coerceIn(0, 1000)
        miniProgress.progress = frac
        if (!dragging) { seek.progress = frac; pos.text = fmt(p) }
        dur.text = fmt(d)
    }

    private fun fmt(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
    }
}
