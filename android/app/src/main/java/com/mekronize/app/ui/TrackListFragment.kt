package com.mekronize.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mekronize.app.R
import com.mekronize.app.data.Repo
import com.mekronize.app.data.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Tümü / Videolar / Müzikler / Favoriler ve tek bir playlistin içeriği için ortak liste. */
class TrackListFragment : Fragment() {
    private var current: List<Track> = emptyList()

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        i.inflate(R.layout.fragment_tracks, c, false)

    override fun onViewCreated(v: View, s: Bundle?) {
        val kind = requireArguments().getInt(ARG_KIND)
        val playlistId = requireArguments().getLong(ARG_PLAYLIST)
        val list = v.findViewById<RecyclerView>(R.id.list)
        val empty = v.findViewById<TextView>(R.id.empty)
        val ctx = requireContext().applicationContext

        val adapter = TrackAdapter(
            onClick = { tracks, pos -> Conn.play(ctx, tracks, pos) },
            onHeart = { t -> lifecycleScope.launch(Dispatchers.IO) { Repo.toggleFavorite(t.id) } },
        )
        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = adapter
        list.setHasFixedSize(true)
        list.itemAnimator = null

        v.findViewById<Button>(R.id.btnPlayAll).setOnClickListener { Conn.play(ctx, current, 0, false) }
        v.findViewById<Button>(R.id.btnShuffleAll).setOnClickListener { Conn.playShuffled(ctx, current) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                Repo.changes.collect {
                    val data = withContext(Dispatchers.IO) {
                        when (kind) {
                            KIND_VIDEOS -> Repo.videos()
                            KIND_MUSIC -> Repo.music()
                            KIND_FAV -> Repo.favorites()
                            KIND_PLAYLIST -> Repo.playlistTracks(playlistId)
                            else -> Repo.all()
                        }
                    }
                    current = data
                    adapter.submitList(data)
                    v.findViewById<View>(R.id.actions).visibility = if (data.isEmpty()) View.GONE else View.VISIBLE
                    empty.visibility = if (data.isEmpty()) View.VISIBLE else View.GONE
                    empty.text = if (kind == KIND_FAV) "Henüz favori yok. Parçaları çaldıkça en çok çalınanlar burada listelenir; ❤ ile de ekleyebilirsin."
                    else "İçerik yok. Ana ekrandaki Senkron kutusundan key girip senkronla."
                }
            }
        }
    }

    companion object {
        const val ARG_KIND = "kind"
        const val ARG_PLAYLIST = "pl"
        const val KIND_ALL = 0
        const val KIND_VIDEOS = 1
        const val KIND_MUSIC = 2
        const val KIND_FAV = 3
        const val KIND_PLAYLIST = 4
        const val KIND_PLAYLISTS = 5

        fun of(kind: Int, playlistId: Long = 0) = TrackListFragment().apply {
            arguments = Bundle().apply { putInt(ARG_KIND, kind); putLong(ARG_PLAYLIST, playlistId) }
        }
    }
}
