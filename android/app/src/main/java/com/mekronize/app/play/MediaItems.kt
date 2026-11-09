package com.mekronize.app.play

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.mekronize.app.data.PlaylistInfo
import com.mekronize.app.data.Track

object MediaItems {
    const val ROOT = "root"
    const val PLAYLISTS = "playlists"
    const val ALL = "all"
    const val MUSIC = "music"
    const val FAV = "fav"
    const val PL_PREFIX = "pl:"
    /** Android Auto öğeleri: "a:<bağlam>:<id>" — tek parça seçilince bağlamın tamamı kuyruğa alınır. */
    const val AUTO_PREFIX = "a:"

    /** Arayüzden gönderilen hafif öğe (URI'yi servis çözer). */
    fun light(t: Track): MediaItem = MediaItem.Builder()
        .setMediaId(t.id.toString())
        .setMediaMetadata(meta(t, null))
        .build()

    /** Servisin oynatacağı tam öğe. */
    fun full(t: Track, file: java.io.File, mediaId: String = t.id.toString()): MediaItem = MediaItem.Builder()
        .setMediaId(mediaId)
        .setUri(Uri.fromFile(file))
        .setMediaMetadata(meta(t, null))
        .build()

    fun meta(t: Track, artist: String?): MediaMetadata = MediaMetadata.Builder()
        .setTitle(t.title)
        .setArtist(artist)
        .setIsPlayable(true)
        .setIsBrowsable(false)
        .setMediaType(if (t.isVideo) MediaMetadata.MEDIA_TYPE_VIDEO else MediaMetadata.MEDIA_TYPE_MUSIC)
        .build()

    fun folder(id: String, title: String): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder().setTitle(title).setIsBrowsable(true).setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED).build(),
        ).build()

    fun playlistFolder(p: PlaylistInfo) = folder(PL_PREFIX + p.id, "${p.name} (${p.count})")

    fun browseTrack(ctx: String, t: Track, file: java.io.File): MediaItem = full(t, file, "$AUTO_PREFIX$ctx:${t.id}")
}
