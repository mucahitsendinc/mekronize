package com.mekronize.app.ui

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mekronize.app.R
import com.mekronize.app.data.Repo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Açılış: CarPlay tarzı büyük, ikonlu kutular. */
class MainActivity : AppCompatActivity() {
    private lateinit var sheet: PlayerSheet

    private class Tile(
        val id: Int, val title: String, var sub: String, val icon: Int, val c1: Int, val c2: Int, var enabled: Boolean = true,
    )

    private val tiles = listOf(
        Tile(0, "Playlistler", "", R.drawable.ic_playlist, 0xFF6C8CFF.toInt(), 0xFF4B59D6.toInt()),
        Tile(1, "Tümü", "", R.drawable.ic_library, 0xFF8E6BFF.toInt(), 0xFF5B3FD0.toInt()),
        Tile(2, "Videolar", "", R.drawable.ic_video, 0xFFFF7A59.toInt(), 0xFFD9423D.toInt()),
        Tile(3, "Müzikler", "", R.drawable.ic_music, 0xFF2EC4B6.toInt(), 0xFF178A9A.toInt()),
        Tile(4, "Favoriler", "", R.drawable.ic_heart, 0xFFFF5C8A.toInt(), 0xFFC2336B.toInt()),
        Tile(5, "Karışık çal", "Hepsini karıştır", R.drawable.ic_shuffle, 0xFFF5B042.toInt(), 0xFFD9822B.toInt()),
        Tile(6, "Senkron", "Yeni içerikleri indir", R.drawable.ic_sync, 0xFF3ECF8E.toInt(), 0xFF1FA06A.toInt()),
        Tile(7, "Ayarlar", "Sunucu, kalite, depolama", R.drawable.ic_settings, 0xFF5C6580.toInt(), 0xFF383F55.toInt()),
    )

    private lateinit var adapter: TileAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val grid = findViewById<RecyclerView>(R.id.grid)
        val cols = if (resources.configuration.screenWidthDp >= 600 || resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) 4 else 2
        grid.layoutManager = GridLayoutManager(this, cols)
        adapter = TileAdapter(tiles) { open(it) }
        grid.adapter = adapter
        sheet = PlayerSheet(this)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                Repo.changes.collect {
                    val (all, vid, mus, fav, pl) = withContext(Dispatchers.IO) {
                        listOf(Repo.all().size, Repo.videos().size, Repo.music().size, Repo.favorites().size, Repo.playlists().size)
                    }
                    tiles[0].sub = "$pl liste"; tiles[1].sub = "$all parça"; tiles[2].sub = "$vid video"
                    tiles[3].sub = "$mus müzik"; tiles[4].sub = "$fav favori"
                    tiles[0].enabled = pl > 0; tiles[1].enabled = all > 0; tiles[2].enabled = vid > 0
                    tiles[3].enabled = mus > 0; tiles[4].enabled = fav > 0; tiles[5].enabled = all > 0
                    for (i in 0..4) if (!tiles[i].enabled) tiles[i].sub = "İçerik yok"
                    adapter.notifyDataSetChanged()
                    findViewById<TextView>(R.id.subtitle).text =
                        "$all parça"
                }
            }
        }
    }

    private fun open(t: Tile) {
        when (t.id) {
            0 -> startActivity(ListActivity.intent(this, TrackListFragment.KIND_PLAYLISTS, 0, t.title))
            1 -> startActivity(ListActivity.intent(this, TrackListFragment.KIND_ALL, 0, t.title))
            2 -> startActivity(ListActivity.intent(this, TrackListFragment.KIND_VIDEOS, 0, t.title))
            3 -> startActivity(ListActivity.intent(this, TrackListFragment.KIND_MUSIC, 0, t.title))
            4 -> startActivity(ListActivity.intent(this, TrackListFragment.KIND_FAV, 0, t.title))
            5 -> lifecycleScope.launch {
                val all = withContext(Dispatchers.IO) { Repo.all() }
                Conn.expandNext = false
                Conn.playShuffled(applicationContext, all)
            }
            6 -> startActivity(Intent(this, SyncActivity::class.java))
            else -> startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    override fun onStart() { super.onStart(); sheet.start() }
    override fun onStop() { sheet.stop(); super.onStop() }

    private class TileAdapter(private val data: List<Tile>, private val onClick: (Tile) -> Unit) :
        RecyclerView.Adapter<TileAdapter.VH>() {
        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val root: View = v.findViewById(R.id.tile)
            val icon: ImageView = v.findViewById(R.id.tileIcon)
            val title: TextView = v.findViewById(R.id.tileTitle)
            val sub: TextView = v.findViewById(R.id.tileSub)
        }

        override fun onCreateViewHolder(p: ViewGroup, t: Int) =
            VH(LayoutInflater.from(p.context).inflate(R.layout.item_tile, p, false))

        override fun getItemCount() = data.size

        override fun onBindViewHolder(h: VH, i: Int) {
            val t = data[i]
            val d = h.root.resources.displayMetrics.density
            val colors = if (t.enabled) intArrayOf(t.c1, t.c2) else intArrayOf(0xFF1B1E29.toInt(), 0xFF14161E.toInt())
            h.root.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply { cornerRadius = 26 * d }
            h.root.alpha = if (t.enabled) 1f else 0.55f
            h.root.isEnabled = t.enabled
            h.icon.setImageResource(t.icon)
            h.icon.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0x33FFFFFF) }
            h.title.text = t.title
            h.sub.text = t.sub
            h.root.setOnClickListener { if (t.enabled) onClick(t) }
        }
    }
}
