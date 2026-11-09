package com.mekronize.app.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.mekronize.app.R

class PlaylistActivity : AppCompatActivity() {
    private lateinit var mini: MiniPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_container)
        val id = intent.getLongExtra(EXTRA_ID, 0)
        findViewById<MaterialToolbar>(R.id.toolbar).apply {
            title = intent.getStringExtra(EXTRA_NAME) ?: "Playlist"
            setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
            setNavigationOnClickListener { finish() }
        }
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.container, TrackListFragment.of(TrackListFragment.KIND_PLAYLIST, id)).commit()
        }
        mini = MiniPlayer(this)
    }

    override fun onStart() { super.onStart(); mini.start() }
    override fun onStop() { mini.stop(); super.onStop() }

    companion object {
        const val EXTRA_ID = "id"
        const val EXTRA_NAME = "name"
    }
}
