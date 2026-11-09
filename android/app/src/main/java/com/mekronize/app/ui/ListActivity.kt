package com.mekronize.app.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.mekronize.app.R

/** Tümü / Videolar / Müzikler / Favoriler / Playlistler ve tek bir playlist için ortak ekran. */
class ListActivity : AppCompatActivity() {
    private lateinit var sheet: PlayerSheet

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)
        val kind = intent.getIntExtra(EXTRA_KIND, TrackListFragment.KIND_ALL)
        val id = intent.getLongExtra(EXTRA_ID, 0)
        findViewById<MaterialToolbar>(R.id.toolbar).apply {
            title = intent.getStringExtra(EXTRA_TITLE) ?: ""
            setNavigationIcon(R.drawable.ic_back)
            setNavigationIconTint(getColor(R.color.text))
            setNavigationOnClickListener { finish() }
        }
        if (savedInstanceState == null) {
            val f = if (kind == TrackListFragment.KIND_PLAYLISTS) PlaylistsFragment() else TrackListFragment.of(kind, id)
            supportFragmentManager.beginTransaction().replace(R.id.container, f).commit()
        }
        sheet = PlayerSheet(this)
    }

    override fun onStart() { super.onStart(); sheet.start() }
    override fun onStop() { sheet.stop(); super.onStop() }

    companion object {
        const val EXTRA_KIND = "kind"
        const val EXTRA_ID = "id"
        const val EXTRA_TITLE = "title"
        fun intent(ctx: Context, kind: Int, id: Long, title: String) =
            Intent(ctx, ListActivity::class.java).putExtra(EXTRA_KIND, kind).putExtra(EXTRA_ID, id).putExtra(EXTRA_TITLE, title)
    }
}
