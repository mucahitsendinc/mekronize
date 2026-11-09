package com.mekronize.app.data

object Status {
    const val PENDING = 0
    const val READY = 1
    const val FAILED = 2
}

data class Track(
    val id: Long,
    val title: String,
    val type: String,
    val source: String,
    val file: String?,
    val durationMs: Long,
    val favorite: Boolean,
    val playCount: Int,
    val status: Int,
    val error: String?,
) {
    val isVideo get() = type == "video"
}

data class PlaylistInfo(val id: Long, val name: String, val count: Int)

data class RemoteItem(
    val id: Long,
    val title: String,
    val type: String,
    val source: String,
    val updatedAt: Long,
    val youtubeId: String?,
    val thumbnail: String?,
    val size: Long,
    val sha256: String?,
    val ext: String?,
    val url: String?,
)

data class RemotePlaylist(val id: Long, val name: String, val mediaIds: List<Long>)

data class Manifest(val items: List<RemoteItem>, val playlists: List<RemotePlaylist>)
