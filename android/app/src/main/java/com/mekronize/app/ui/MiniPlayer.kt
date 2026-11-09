package com.mekronize.app.ui

import android.app.Activity
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.mekronize.app.R

/** Alttaki mini oynatıcı çubuğu. Activity başlayınca bağlanır, durunca ayrılır. */
class MiniPlayer(private val act: Activity) {
    private val root: View = act.findViewById(R.id.mini)
    private val title: TextView = act.findViewById(R.id.miniTitle)
    private val icon: ImageView = act.findViewById(R.id.miniIcon)
    private val play: ImageButton = act.findViewById(R.id.miniPlay)
    private val shuffle: ImageButton = act.findViewById(R.id.miniShuffle)
    private var ctrl: MediaController? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refresh()
    }

    init {
        act.findViewById<ImageButton>(R.id.miniPrev).setOnClickListener { ctrl?.seekToPreviousMediaItem() }
        act.findViewById<ImageButton>(R.id.miniNext).setOnClickListener { ctrl?.seekToNextMediaItem() }
        play.setOnClickListener { ctrl?.let { if (it.isPlaying) it.pause() else it.play() } }
        shuffle.setOnClickListener { ctrl?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }
        root.setOnClickListener { Conn.openPlayer(act) }
    }

    fun start() {
        Conn.with(act) { c ->
            ctrl?.removeListener(listener)
            ctrl = c
            c.addListener(listener)
            refresh()
        }
    }

    fun stop() {
        ctrl?.removeListener(listener)
        ctrl = null
    }

    private fun refresh() {
        val c = ctrl ?: return
        if (c.mediaItemCount == 0) { root.visibility = View.GONE; return }
        root.visibility = View.VISIBLE
        title.text = c.mediaMetadata.title ?: ""
        icon.setImageResource(if (c.mediaMetadata.mediaType == MediaMetadata.MEDIA_TYPE_VIDEO) R.drawable.ic_video else R.drawable.ic_music)
        play.setImageResource(if (c.isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
        shuffle.imageTintList = android.content.res.ColorStateList.valueOf(
            androidx.core.content.ContextCompat.getColor(act, if (c.shuffleModeEnabled) R.color.accent else R.color.muted),
        )
    }
}
