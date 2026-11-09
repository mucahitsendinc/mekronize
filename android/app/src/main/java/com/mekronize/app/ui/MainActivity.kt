package com.mekronize.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.mekronize.app.R

class MainActivity : AppCompatActivity() {
    private lateinit var mini: MiniPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<MaterialToolbar>(R.id.toolbar).apply {
            inflateMenu(R.menu.main)
            setOnMenuItemClickListener {
                if (it.itemId == R.id.action_sync) startActivity(Intent(this@MainActivity, SyncActivity::class.java))
                true
            }
        }

        val titles = listOf(R.string.tab_playlists, R.string.tab_all, R.string.tab_videos, R.string.tab_music, R.string.tab_favorites)
        val pager = findViewById<ViewPager2>(R.id.pager)
        pager.adapter = object : FragmentStateAdapter(this as FragmentActivity) {
            override fun getItemCount() = titles.size
            override fun createFragment(position: Int): Fragment = when (position) {
                0 -> PlaylistsFragment()
                1 -> TrackListFragment.of(TrackListFragment.KIND_ALL)
                2 -> TrackListFragment.of(TrackListFragment.KIND_VIDEOS)
                3 -> TrackListFragment.of(TrackListFragment.KIND_MUSIC)
                else -> TrackListFragment.of(TrackListFragment.KIND_FAV)
            }
        }
        pager.offscreenPageLimit = 1
        // Açılışta "Tümü" sekmesi
        pager.setCurrentItem(1, false)
        TabLayoutMediator(findViewById<TabLayout>(R.id.tabs), pager) { tab, pos -> tab.setText(titles[pos]) }.attach()

        mini = MiniPlayer(this)
    }

    override fun onStart() {
        super.onStart()
        mini.start()
    }

    override fun onStop() {
        mini.stop()
        super.onStop()
    }
}
