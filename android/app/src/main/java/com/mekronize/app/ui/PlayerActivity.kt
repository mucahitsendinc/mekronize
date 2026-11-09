package com.mekronize.app.ui

import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.mekronize.app.R

class PlayerActivity : AppCompatActivity() {
    private lateinit var view: PlayerView
    private lateinit var titleView: TextView
    private var player: Player? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = update(player)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_Mekronize_Player)
        setContentView(R.layout.activity_player)
        view = findViewById(R.id.playerView)
        titleView = findViewById(R.id.playerTitle)
        view.setShowPreviousButton(true)
        view.setShowNextButton(true)
        applyImmersive()
    }

    override fun onStart() {
        super.onStart()
        Conn.with(this) { c ->
            player?.removeListener(listener)
            player = c
            view.player = c
            c.addListener(listener)
            update(c)
        }
    }

    override fun onStop() {
        player?.removeListener(listener)
        view.player = null
        player = null
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onStop()
    }

    private fun update(p: Player) {
        titleView.text = p.mediaMetadata.title ?: ""
        val isVideo = p.mediaMetadata.mediaType == MediaMetadata.MEDIA_TYPE_VIDEO
        // Müzikte kontroller hep görünür, videoda otomatik gizlenir.
        view.controllerShowTimeoutMs = if (isVideo) 3000 else 0
        view.controllerHideOnTouch = isVideo
        if (!isVideo) view.showController()
        if (isVideo && p.isPlaying) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        titleView.visibility = if (isVideo && !view.isControllerFullyVisible) View.GONE else View.VISIBLE
        if (p.mediaItemCount == 0) finish()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyImmersive()
    }

    private fun applyImmersive() {
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        WindowCompat.setDecorFitsSystemWindows(window, !landscape)
        val c = WindowInsetsControllerCompat(window, window.decorView)
        if (landscape) {
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            c.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            c.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}
