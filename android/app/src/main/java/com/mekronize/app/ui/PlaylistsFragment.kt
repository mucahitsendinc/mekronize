package com.mekronize.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mekronize.app.R
import com.mekronize.app.data.Repo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlaylistsFragment : Fragment() {
    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        i.inflate(R.layout.fragment_tracks, c, false)

    override fun onViewCreated(v: View, s: Bundle?) {
        v.findViewById<View>(R.id.actions).visibility = View.GONE
        val list = v.findViewById<RecyclerView>(R.id.list)
        val empty = v.findViewById<TextView>(R.id.empty)
        val adapter = PlaylistAdapter { p ->
            startActivity(
                Intent(requireContext(), PlaylistActivity::class.java)
                    .putExtra(PlaylistActivity.EXTRA_ID, p.id).putExtra(PlaylistActivity.EXTRA_NAME, p.name),
            )
        }
        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = adapter
        list.itemAnimator = null
        empty.text = "Playlist yok. Web panelinde oluşturup senkronla."

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                Repo.changes.collect {
                    val data = withContext(Dispatchers.IO) { Repo.playlists() }
                    adapter.submitList(data)
                    empty.visibility = if (data.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }
}
