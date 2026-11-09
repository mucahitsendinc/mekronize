package com.mekronize.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mekronize.app.R
import com.mekronize.app.data.PlaylistInfo
import com.mekronize.app.data.Track

class TrackAdapter(
    private val onClick: (List<Track>, Int) -> Unit,
    private val onHeart: (Track) -> Unit,
) : ListAdapter<Track, TrackAdapter.VH>(object : DiffUtil.ItemCallback<Track>() {
    override fun areItemsTheSame(a: Track, b: Track) = a.id == b.id
    override fun areContentsTheSame(a: Track, b: Track) = a == b
}) {
    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val icon: ImageView = v.findViewById(R.id.icon)
        val title: TextView = v.findViewById(R.id.title)
        val sub: TextView = v.findViewById(R.id.sub)
        val heart: ImageButton = v.findViewById(R.id.heart)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_track, parent, false))

    override fun onBindViewHolder(h: VH, position: Int) {
        val t = getItem(position)
        h.icon.setImageResource(if (t.isVideo) R.drawable.ic_video else R.drawable.ic_music)
        h.title.text = t.title
        val parts = ArrayList<String>()
        parts.add(if (t.isVideo) "Video" else "Müzik")
        if (t.durationMs > 0) parts.add(fmt(t.durationMs))
        if (t.playCount > 0) parts.add("${t.playCount} kez çalındı")
        h.sub.text = parts.joinToString(" · ")
        h.heart.setImageResource(if (t.favorite) R.drawable.ic_heart else R.drawable.ic_heart_outline)
        h.heart.imageTintList = android.content.res.ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(h.itemView.context, if (t.favorite) R.color.heart else R.color.muted))
        h.heart.setOnClickListener { onHeart(t) }
        h.itemView.setOnClickListener {
            val pos = h.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onClick(currentList, pos)
        }
    }

    companion object {
        fun fmt(ms: Long): String {
            val s = ms / 1000
            return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
        }
    }
}

class PlaylistAdapter(private val onClick: (PlaylistInfo) -> Unit) :
    ListAdapter<PlaylistInfo, TrackAdapter.VH>(object : DiffUtil.ItemCallback<PlaylistInfo>() {
        override fun areItemsTheSame(a: PlaylistInfo, b: PlaylistInfo) = a.id == b.id
        override fun areContentsTheSame(a: PlaylistInfo, b: PlaylistInfo) = a == b
    }) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        TrackAdapter.VH(LayoutInflater.from(parent.context).inflate(R.layout.item_track, parent, false))

    override fun onBindViewHolder(h: TrackAdapter.VH, position: Int) {
        val p = getItem(position)
        h.icon.setImageResource(R.drawable.ic_playlist)
        h.title.text = p.name
        h.sub.text = "${p.count} parça"
        h.heart.visibility = View.GONE
        h.itemView.setOnClickListener { onClick(p) }
    }
}
